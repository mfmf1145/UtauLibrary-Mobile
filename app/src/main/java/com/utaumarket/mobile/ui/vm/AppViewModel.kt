package com.utaumarket.mobile.ui.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.annotation.StringRes
import com.utaumarket.mobile.R
import com.utaumarket.mobile.UtauMarketApp
import com.utaumarket.mobile.core.L
import com.utaumarket.mobile.data.github.GitHubApi
import com.utaumarket.mobile.data.installed.InstalledCache
import com.utaumarket.mobile.data.installed.DirScanner
import com.utaumarket.mobile.data.installed.InstalledScanner
import com.utaumarket.mobile.data.model.DownloadStatus
import com.utaumarket.mobile.data.model.DownloadTask
import com.utaumarket.mobile.data.model.InstalledItem
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.data.prefs.AppSettings
import com.utaumarket.mobile.data.prefs.SettingsStore
import com.utaumarket.mobile.data.prefs.ThemeMode
import com.utaumarket.mobile.download.DownloadController
import kotlinx.coroutines.delay
import com.utaumarket.mobile.download.DownloadQueueFacade
import com.utaumarket.mobile.saf.SafManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val settings: AppSettings = AppSettings(),
    val voicebanks: List<ResourceItem> = emptyList(),
    val plugins: List<ResourceItem> = emptyList(),
    /** 分支 / 改版（第三方修改的宿主程序，如支持 DLL 的 OpenUtau Mobile） */
    val forks: List<ResourceItem> = emptyList(),
    val phonemizers: List<ResourceItem> = emptyList(),
    val phonemizerLoading: Boolean = false,
    val phonemizerError: String = "",
    val installedVoicebanks: List<InstalledItem> = emptyList(),
    val installedPlugins: List<InstalledItem> = emptyList(),
    val installedPhonemizers: List<InstalledItem> = emptyList(),
    /** 已安装的分支改版（第三方 OpenUtau 本体，是 APK，不在声库目录里） */
    val installedForks: List<com.utaumarket.mobile.data.model.InstalledFork> = emptyList(),
    val installedLoading: Boolean = false,
    /** 正在后台删除的条目数，>0 时界面显示"删除中" */
    val deletingCount: Int = 0,
    /** 目录授权是否已失效（需要重新授权） */
    val rootInvalid: Boolean = false,
    /** 扫描到的 USTX / UST 工程 */
    val projects: List<com.utaumarket.mobile.data.model.ProjectItem> = emptyList(),
    val projectsLoading: Boolean = false,
    /** 已安装列表是否已经加载过一次（用于区分"空"与"还没扫"） */
    val installedLoadedOnce: Boolean = false,
    val tasks: List<DownloadTask> = emptyList(),
    val message: String = "",
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val utm: UtauMarketApp = app as UtauMarketApp
    val settings: SettingsStore = utm.settings
    private val manifest = utm.manifest
    private val github = GitHubApi(app.applicationContext)

    /** 界面提示文案走资源文件；切语言会重建 Activity，这里每次调用都取当前语言 */
    private fun s(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        try {
            _state.value = _state.value.copy(
                voicebanks = manifest.voicebanks(),
                plugins = manifest.plugins(),
                forks = manifest.forks(),
                // 内置音素器也在这里初始化。
                // 之前只靠"刷新"按钮触发 loadPhonemizers，切到音素器页时列表一直是空的。
                phonemizers = manifest.phonemizers(),
            )
        } catch (t: Throwable) {
            L.e("读取内置清单失败", t)
            com.utaumarket.mobile.core.CrashReporter.write(getApplication(), t)
        }
        dumpSafDiagnostics()
        try {
            // 后台实测每个站点的直连情况，徽章不再是清一色"直连缓慢"
            viewModelScope.launch {
                runCatching {
                    val urls = (manifest.voicebanks() + manifest.plugins())
                        .filter { it.url.isNotBlank() }
                        .map { it.url }
                    com.utaumarket.mobile.data.net.NetworkProbe.probeAll(urls)
                }.onFailure { L.w("网络探测失败", it) }
            }
            viewModelScope.launch {
                settings.state.collectLatest { s ->
                    _state.value = _state.value.copy(settings = s)
                    runCatching { refreshInstalled() }
                }
            }
            // 【后台常驻预热】启动后 1.5 秒开始悄悄扫一遍「已安装」。
            // 清单预热与网络探测先跑，扫描排在它们后面，避免同时抢 IO。
            // 用户点进「已安装」时，数据通常已经是现成的（配合 InstalledCache 缓存）。
            viewModelScope.launch {
                delay(1500)
                if (!settings.current.warmScanInstalled) return@launch
                if (settings.current.rootTreeUri.isBlank()) return@launch
                runCatching {
                    // 把线程优先级降到后台，滚动/输入时不会被扫描抢走 CPU
                    val t = Thread.currentThread()
                    val old = t.priority
                    runCatching { t.priority = Thread.MIN_PRIORITY }
                    try {
                        refreshInstalled(force = true, quiet = true)
                    } finally {
                        runCatching { t.priority = old }
                    }
                }.onFailure { L.w("已安装预热扫描失败", it) }
            }
            // 工程同理：再等 1 秒，避开「已安装」扫描的高峰
            viewModelScope.launch {
                delay(2600)
                if (!settings.current.warmScanInstalled) return@launch
                if (settings.current.rootTreeUri.isBlank()) return@launch
                runCatching {
                    val t = Thread.currentThread()
                    val old = t.priority
                    runCatching { t.priority = Thread.MIN_PRIORITY }
                    try {
                        refreshProjects(force = true, quiet = true)
                    } finally {
                        runCatching { t.priority = old }
                    }
                }.onFailure { L.w("工程预热扫描失败", it) }
            }
            viewModelScope.launch {
                var knownDone = emptySet<String>()
                DownloadController.tasks.collectLatest { tasks ->
                    _state.value = _state.value.copy(tasks = tasks)
                    // 有任务刚变成"已完成" → 立刻刷新已安装列表，不用用户手动拉
                    val done = tasks.filter { it.status == DownloadStatus.DONE }.map { it.id }.toSet()
                    if (done.isNotEmpty() && !knownDone.containsAll(done)) {
                        // 必须 force：普通刷新有 30 秒限流，装完那一刻的刷新会被吞掉，
                        // 用户就得手动点「刷新」才能看到新装的声库。
                        runCatching { refreshInstalled(force = true) }
                    }
                    knownDone = done
                }
            }
        } catch (t: Throwable) {
            // 常见于协程 Main dispatcher 初始化失败；记录下来但不要杀掉进程
            L.e("启动后台任务失败（Main dispatcher 可能不可用）", t)
            com.utaumarket.mobile.core.CrashReporter.write(getApplication(), t)
        }
    }

    // ------------------------------------------------------------ 目录授权

    /** 授权根目录（只授权一个），随后自动扫描识别子目录 */
    fun setRootTree(uri: String) {
        val hadExplicit = settings.current.voicebankTreeUri.isNotBlank() ||
            settings.current.pluginTreeUri.isNotBlank() ||
            settings.current.phonemizerTreeUri.isNotBlank()
        if (hadExplicit) {
            // 之前单独授权过某一类目录，会覆盖根目录自动识别；这里一并清掉，避免"授权了根目录却没生效"
            settings.releaseAll()
        }
        settings.setRootTree(uri)
        rescanRoot()
    }

    /** 扫描根目录，自动认领/创建 Singers、Plugins 并写回设置 */
    fun rescanRoot() {
        viewModelScope.launch {
            _state.value = _state.value.copy(installedLoading = true)
            val root = settings.current.rootTreeUri
            if (root.isBlank()) {
                _state.value = _state.value.copy(installedLoading = false, message = s(R.string.vm_authorize_root_first))
                return@launch
            }
            val out = withContext(Dispatchers.IO) {
                runCatching { DirScanner.scanAndConfigure(getApplication(), root, createIfMissing = true) }
                    .onFailure { L.w("目录扫描失败", it) }
                    .getOrNull()
            }
            if (out == null || !out.ok) {
                _state.value = _state.value.copy(
                    installedLoading = false,
                    message = out?.error ?: s(R.string.vm_scan_failed),
                )
            } else {
                settings.setSubdirs(
                    out.voicebankName.ifBlank { "Singers" },
                    out.pluginName.ifBlank { "Plugins" },
                )
                settings.setResolvedDocIds(out.voicebankDocId, out.pluginDocId, out.phonemizerDocId)
                settings.setScanSummary(out.summary, System.currentTimeMillis())
                _state.value = _state.value.copy(installedLoading = false, message = out.summary)
            }
            refreshInstalled()
        }
    }

    fun setTreeUri(kind: ResourceKind, uri: String) {
        when (kind) {
            ResourceKind.VOICEBANK -> settings.setVoicebankTree(uri)
            ResourceKind.PLUGIN -> settings.setPluginTree(uri)
            ResourceKind.PHONEMIZER -> settings.setPhonemizerTree(uri)
        }
        refreshInstalled()
    }

    fun releaseRoot() {
        val ctx = getApplication<Application>()
        val root = settings.current.rootTreeUri
        if (root.isNotBlank()) runCatching { SafManager.release(ctx, Uri.parse(root)) }
        settings.releaseRoot()
        refreshInstalled()
    }

    fun releaseTree(kind: ResourceKind) {
        val ctx = getApplication<Application>()
        val cur = settings.rootFor(kind)
        if (cur.isNotBlank()) {
            runCatching { SafManager.release(ctx, Uri.parse(cur)) }
        }
        when (kind) {
            ResourceKind.VOICEBANK -> settings.setVoicebankTree("")
            ResourceKind.PLUGIN -> settings.setPluginTree("")
            ResourceKind.PHONEMIZER -> settings.setPhonemizerTree("")
        }
    }

    // ------------------------------------------------------------ 已安装

    /**
     * 重新加载音源列表。会重新读一遍清单（外部清单优先），
     * 这样更新目录不需要重装 APK。
     */
    fun reloadCatalog() {
        viewModelScope.launch {
            _state.value = _state.value.copy(message = s(R.string.vm_catalog_refreshing))
            runCatching {
                val items = manifest.load(force = true)
                val src = manifest.sourceName
                _state.value = _state.value.copy(
                    voicebanks = items.filter { it.kind == ResourceKind.VOICEBANK },
                    plugins = items.filter { it.kind == ResourceKind.PLUGIN },
                    message = s(
                        R.string.vm_catalog_refreshed,
                        src,
                        items.count { it.kind == ResourceKind.VOICEBANK },
                    ),
                )
            }.onFailure {
                L.w("刷新音源列表失败", it)
                _state.value = _state.value.copy(
                    message = s(R.string.vm_refresh_failed, it.message ?: s(R.string.vm_unknown_error)),
                )
            }
        }
    }

    /** 那个歌手装在哪 —— 自动扫描已安装目录后给出的可读路径 */
    fun installedAt(item: ResourceItem): String {
        val list = when (item.kind) {
            ResourceKind.VOICEBANK -> _state.value.installedVoicebanks
            ResourceKind.PLUGIN -> _state.value.installedPlugins
            ResourceKind.PHONEMIZER -> _state.value.installedPhonemizers
        }
        val target = item.name.trim()
        val hit = list.firstOrNull { installed ->
            installed.name.equals(target, true) ||
                installed.name.contains(target, true) ||
                target.contains(installed.name, true)
        } ?: return ""
        return runCatching {
            val tree = Uri.parse(hit.parentUri)
            val docId = runCatching {
                android.provider.DocumentsContract.getDocumentId(Uri.parse(hit.uri))
            }.getOrDefault("")
            SafManager.readablePath(tree, docId)
        }.getOrDefault(hit.name)
    }

    /** 手动重新探测全部站点的连通性（右上角「刷新」会调它） */
    fun refreshNetwork() {
        viewModelScope.launch {
            _state.value = _state.value.copy(message = s(R.string.vm_probing_network))
            runCatching {
                // 顺手清掉单连接记忆，避免历史误判一直压着下载速度
                com.utaumarket.mobile.download.ParallelDownloader.clearHostMemory()
                com.utaumarket.mobile.data.net.NetworkProbe.invalidate()
                // 音素器也要探：它们的文件大小只能从 Content-Length 拿，
                // 不放进探测列表的话插件/音素器卡片就一直不显示大小
                val urls = (manifest.voicebanks() + manifest.plugins() + manifest.phonemizers())
                    .filter { it.url.isNotBlank() }
                    .map { it.url }
                com.utaumarket.mobile.data.net.NetworkProbe.probeAll(urls)
            }.onFailure { L.w("网络探测失败", it) }
            _state.value = _state.value.copy(message = s(R.string.vm_network_updated))
        }
    }


    /**
     * 把 SAF 授权的真实状态写到 /sdcard/Download/UtauMarketMobile-saf.txt。
     *
     * 「授权失败」这类问题在设备上根本看不见内部状态，只能靠猜。
     * 把 rootTreeUri / 持久化授权 / treeDocId / listChildren / DirScanner 每一步的
     * 结果都写出来，一次就能定位到底卡在哪一步。
     */
    fun dumpSafDiagnostics() {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<Application>()
                    val sb = StringBuilder()
                    val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                        .format(java.util.Date())
                    sb.appendLine("=== SAF 诊断 " + ts + " ===")
                    // 必须读 settings.current（真实 prefs）；读 _state 快照会拿到还没加载完的默认值
                    val s = settings.current
                    sb.appendLine("rootTreeUri      = " + s.rootTreeUri.ifBlank { "(空)" })
                    sb.appendLine("lastRootTreeUri  = " + s.lastRootTreeUri.ifBlank { "(空)" })
                    val persisted = runCatching {
                        ctx.contentResolver.persistedUriPermissions.map {
                            it.uri.toString() + " r=" + it.isReadPermission + " w=" + it.isWritePermission
                        }
                    }.getOrElse { listOf("读取失败: " + it) }
                    sb.appendLine("持久化授权(" + persisted.size + "):")
                    persisted.forEach { sb.appendLine("    " + it) }
                    if (s.rootTreeUri.isNotBlank()) {
                        val uri = Uri.parse(s.rootTreeUri)
                        sb.appendLine("hasPersisted     = " + SafManager.hasPersisted(ctx, uri))
                        val docId = SafManager.treeDocId(uri) ?: ""
                        sb.appendLine("treeDocId        = " + docId.ifBlank { "(null)" })
                        sb.appendLine("canAccess        = " + SafManager.canAccess(ctx, uri))
                        val kids = runCatching {
                            SafManager.listChildren(ctx, uri, docId).map { it.name }
                        }.getOrElse { listOf("EX: " + it.javaClass.simpleName + " / " + it.message) }
                        sb.appendLine("listChildren(" + kids.size + ") = " + kids.take(14).joinToString(", "))
                        val oc = runCatching {
                            com.utaumarket.mobile.data.installed.DirScanner
                                .scanAndConfigure(ctx, s.rootTreeUri, createIfMissing = false)
                        }.getOrElse {
                            com.utaumarket.mobile.data.installed.DirScanner.Outcome(
                                false, error = "EX: " + it.javaClass.simpleName + " / " + it.message
                            )
                        }
                        sb.appendLine("DirScanner       = ok=" + oc.ok + " vbCount=" + oc.voicebankCount +
                            " vbDoc=" + oc.voicebankDocId + " err=" + oc.error)
                    }
                    listOf(ResourceKind.VOICEBANK, ResourceKind.PLUGIN, ResourceKind.PHONEMIZER).forEach { k ->
                        val t = settings.targetFor(k)
                        sb.appendLine("targetFor(" + k + ") = " + (t?.docId ?: "(null)"))
                    }
                    // 【最关键】真写一次 —— 授权看起来正常但写不进去，才是"授权失败"的真身
                    val t0 = settings.targetFor(ResourceKind.VOICEBANK)
                    if (t0 != null) {
                        val tree = Uri.parse(t0.treeUri)
                        val testDoc = runCatching {
                            SafManager.createFile(ctx, tree, t0.docId, "utm_write_test.txt", "text/plain")
                        }.getOrElse { sb.appendLine("写入测试 createFile 异常: " + it.javaClass.name + " / " + it.message); null }
                        sb.appendLine("写入测试 createFile = " + (testDoc ?: "(null)"))
                        if (testDoc != null) {
                            val wrote = runCatching {
                                ctx.contentResolver.openOutputStream(
                                    SafManager.documentUri(tree, testDoc), "wt"
                                )?.use { it.write("utm".toByteArray()) }
                                true
                            }.getOrElse {
                                sb.appendLine("    写内容异常: " + it.javaClass.name + " / " + it.message); false
                            }
                            sb.appendLine("    写内容 = " + wrote)
                            val renamed = SafManager.displayNameOf(ctx, tree, testDoc)
                            sb.appendLine("    实际文件名 = " + (renamed ?: "(null)"))
                            runCatching { android.provider.DocumentsContract.deleteDocument(ctx.contentResolver, SafManager.documentUri(tree, testDoc)) }
                            sb.appendLine("    已清理测试文件")
                        }
                        // 【完整模拟安装】建一个子目录，再往里写一个文件 —— 这正是 Installer 干的事
                        val subDir = runCatching {
                            SafManager.createDirectory(ctx, tree, t0.docId, "utm_install_test")
                        }.getOrElse { sb.appendLine("建子目录异常: " + it.javaClass.name + " / " + it.message); null }
                        sb.appendLine("模拟安装 建子目录 = " + (subDir ?: "(null)"))
                        if (subDir != null) {
                            val inner = runCatching {
                                SafManager.createFile(ctx, tree, subDir, "oto.ini", "text/plain")
                            }.getOrElse { sb.appendLine("    嵌套建文件异常: " + it.javaClass.name + " / " + it.message); null }
                            sb.appendLine("    嵌套 createFile(oto.ini) = " + (inner ?: "(null)"))
                            if (inner != null) {
                                sb.appendLine("    嵌套实际文件名 = " + (SafManager.displayNameOf(ctx, tree, inner) ?: "(null)"))
                                val w = runCatching {
                                    ctx.contentResolver.openOutputStream(
                                        SafManager.documentUri(tree, inner), "wt"
                                    )?.use { it.write("test".toByteArray()) }; true
                                }.getOrElse { sb.appendLine("    嵌套写异常: " + it.javaClass.name + " / " + it.message); false }
                                sb.appendLine("    嵌套写内容 = " + w)
                                runCatching { android.provider.DocumentsContract.deleteDocument(ctx.contentResolver, SafManager.documentUri(tree, inner)) }
                            }
                            runCatching { android.provider.DocumentsContract.deleteDocument(ctx.contentResolver, SafManager.documentUri(tree, subDir)) }
                            sb.appendLine("    已清理模拟目录")
                        }
                    }
                    sb.toString()
                }.getOrElse { "诊断异常: " + it + "\n" }
            }
            runCatching {
                com.utaumarket.mobile.core.CrashReporter.appendShared(
                    getApplication(), "UtauMarketMobile-saf.txt", text + "\n"
                )
            }
        }
    }

    /** 启动/刷新时提前发现目录授权失效，不要等到"安装"那一刻才报错 */
    private fun checkRootAccess() {
        viewModelScope.launch {
            val st = _state.value
            val root = st.settings.rootTreeUri
            if (root.isBlank()) {
                if (st.rootInvalid) _state.value = st.copy(rootInvalid = false)
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<Application>()
                    val uri = Uri.parse(root)
                    val docId = SafManager.treeDocId(uri) ?: ""
                    if (docId.isBlank()) return@runCatching false
                    if (!SafManager.canAccess(ctx, uri)) return@runCatching false
                    // 【关键】授权有效还不够 —— 目标子目录（Singers / Plugins）可能被删了，
                    // 而设置里还记着旧的 docId。这时 targetFor() 会返回一个**不存在的目录**，
                    // 写入必然失败，却被报成"SAF 授权失败"。
                    // 每次启动都跑一次 scanAndConfigure(createIfMissing = true)，
                    // 目录不在就重建、docId 过期就刷新。
                    val oc = com.utaumarket.mobile.data.installed.DirScanner
                        .scanAndConfigure(ctx, root, createIfMissing = true)
                    if (!oc.ok) {
                        L.w("目标目录准备失败: " + oc.error)
                    } else {
                        // 【必须回写】之前这里只扫不存，音素器目录即使新建了，
                        // 设置里还是旧的（= 插件目录），于是两个标签扫同一文件夹、
                        // 互相显示对方的条目（用户反馈）。
                        settings.setResolvedDocIds(
                            oc.voicebankDocId, oc.pluginDocId, oc.phonemizerDocId,
                        )
                    }
                    true
                }.getOrDefault(false)
            }
            if (!ok) {
                L.w("目录授权已失效: \$root")
                _state.value = _state.value.copy(
                    rootInvalid = true,
                    message = s(R.string.vm_root_invalid_message),
                )
            } else if (_state.value.rootInvalid) {
                _state.value = _state.value.copy(rootInvalid = false)
            }
        }
    }

    // ---------------------------------------------------------------- 工程管理

    /** 上次真正扫完工程的时刻，用于 30 秒限流 */
    private var lastProjectScanAt: Long = 0L

    /**
     * 扫描已授权根目录下的 USTX / UST 工程。
     *
     * 和「已安装」一样的策略：
     *   1. 先亮出磁盘缓存（毫秒级）
     *   2. 30 秒内不重复扫
     *   3. 扫完写回缓存
     */
    fun refreshProjects(force: Boolean = false, quiet: Boolean = false) {
        val root = settings.current.rootTreeUri
        if (root.isBlank()) {
            _state.value = _state.value.copy(projects = emptyList(), projectsLoading = false)
            return
        }
        val now = System.currentTimeMillis()
        if (!force && lastProjectScanAt > 0 && now - lastProjectScanAt < 30_000 &&
            _state.value.projects.isNotEmpty()
        ) return
        viewModelScope.launch {
            // 有缓存就先显示，避免进页面空一下
            if (_state.value.projects.isEmpty()) {
                val cached = withContext(Dispatchers.IO) {
                    runCatching { com.utaumarket.mobile.data.project.ProjectCache.load(getApplication(), root) }
                        .getOrDefault(emptyList())
                }
                if (cached.isNotEmpty()) {
                    _state.value = _state.value.copy(projects = cached, projectsLoading = !quiet)
                }
            }
            if (!quiet) _state.value = _state.value.copy(projectsLoading = true)
            val list = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(root)
                    val docId = SafManager.treeDocId(uri) ?: return@runCatching emptyList()
                    com.utaumarket.mobile.data.project.ProjectScanner.scan(getApplication(), uri, docId)
                }.onFailure { L.w("扫描工程失败", it) }.getOrDefault(emptyList())
            }
            _state.value = _state.value.copy(projects = list, projectsLoading = false)
            lastProjectScanAt = System.currentTimeMillis()
            withContext(Dispatchers.IO) {
                runCatching {
                    com.utaumarket.mobile.data.project.ProjectCache.save(getApplication(), root, list)
                }
            }
        }
    }

    /**
     * 清理安装目录里的残留：
     *  · 松散的压缩包（.zip/.rar/.7z/.uar/.exe）—— 多是"只下载不安装"或解压失败留下的
     *  · 空目录 —— 解压中断留下的空壳
     *
     * 只动这两类，不碰任何看起来像声库/插件的目录。
     */
    fun cleanResidue(kind: ResourceKind) {
        val target = settings.targetFor(kind)
        if (target == null) {
            _state.value = _state.value.copy(message = s(R.string.vm_need_root_first))
            return
        }
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = getApplication<Application>()
                    val tree = Uri.parse(target.treeUri)
                    val kids = SafManager.listChildren(ctx, tree, target.docId)
                    var archives = 0
                    var empties = 0
                    val archiveExt = listOf(".zip", ".rar", ".7z", ".uar", ".exe", ".tar", ".gz")
                    for (k in kids) {
                        if (!k.isDirectory) {
                            if (archiveExt.any { k.name.lowercase().endsWith(it) }) {
                                if (SafManager.delete(ctx, tree, k.docId)) archives++
                            }
                        } else if (k.name != ".nomedia") {
                            val sub = runCatching { SafManager.listChildren(ctx, tree, k.docId) }.getOrDefault(emptyList())
                            if (sub.isEmpty() && SafManager.delete(ctx, tree, k.docId)) empties++
                        }
                    }
                    archives to empties
                }.getOrDefault(0 to 0)
            }
            _state.value = _state.value.copy(message = s(R.string.vm_clean_residue_done, res.first, res.second))
            refreshInstalled(force = true)
        }
    }

    /** 删除一个工程文件 */
    fun deleteProject(p: com.utaumarket.mobile.data.model.ProjectItem) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val tree = Uri.parse(p.parentUri)
                    val docId = runCatching {
                        android.provider.DocumentsContract.getDocumentId(Uri.parse(p.uri))
                    }.getOrDefault("")
                    if (docId.isBlank()) false else SafManager.delete(getApplication(), tree, docId)
                }.getOrDefault(false)
            }
            _state.value = _state.value.copy(
                message = if (ok) {
                    s(R.string.vm_project_deleted, p.fileName)
                } else {
                    s(R.string.vm_project_delete_failed)
                },
                projects = if (ok) _state.value.projects.filterNot { it.uri == p.uri } else _state.value.projects,
            )
        }
    }

    /** 某个歌手名对应的声库是否已安装（工程页用来提示"缺少歌手"） */
    fun isVoicebankInstalled(name: String): Boolean {
        val n = name.trim()
        if (n.isBlank()) return true
        val list = _state.value.installedVoicebanks
        if (list.isEmpty()) return true // 还没扫描过就不误报
        return list.any { inst ->
            inst.name.contains(n, true) || n.contains(inst.name, true)
        }
    }

    /** 删除单条下载记录 */
    fun removeTask(task: com.utaumarket.mobile.data.model.DownloadTask) {
        runCatching { DownloadController.remove(task.id) }
        runCatching { DownloadController.setStatus(task.id, DownloadStatus.CANCELED, s(R.string.vm_task_deleted)) }
    }

    /** 上次真正扫描完成的时刻，用来限流（30 秒内重复请求直接复用结果） */
    private var lastInstalledScanAt: Long = 0L

    fun refreshInstalled(force: Boolean = false, quiet: Boolean = false) {
        // 【限流】切页/切标签都会调到这里，之前每次都完整扫一遍。
        // 30 秒内已经有结果就直接用现成的，除非用户明确点了刷新。
        val now = System.currentTimeMillis()
        if (!force && lastInstalledScanAt > 0 && now - lastInstalledScanAt < 30_000 &&
            _state.value.installedLoadedOnce
        ) return
        // 先校验授权、并把缺失的目标目录建回来（Singers / Plugins）
        checkRootAccess()
        viewModelScope.launch {
            // 【先亮出缓存】磁盘读一次 JSON，毫秒级，用户不会再对着空白等扫描
            if (!_state.value.installedLoadedOnce) {
                val cached = withContext(Dispatchers.IO) {
                    runCatching { InstalledCache.load(getApplication()) }.getOrDefault(emptyMap())
                }
                if (cached.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        installedVoicebanks = cached[ResourceKind.VOICEBANK] ?: emptyList(),
                        installedPlugins = cached[ResourceKind.PLUGIN] ?: emptyList(),
                        installedPhonemizers = cached[ResourceKind.PHONEMIZER] ?: emptyList(),
                        installedLoadedOnce = true,
                        installedLoading = true,
                    )
                }
            }
            // quiet = 后台预热：不要点亮"正在刷新"状态，用户没在看这个页面
            if (!quiet) _state.value = _state.value.copy(installedLoading = true)
            // 三类目录并发扫描，串行的话用户要等三倍时间
            val results = coroutineScope {
                listOf(ResourceKind.VOICEBANK, ResourceKind.PLUGIN, ResourceKind.PHONEMIZER).map { k ->
                    async { k to scanAsync(settings.targetFor(k), k) }
                }.awaitAll()
            }.toMap()
            _state.value = _state.value.copy(
                installedVoicebanks = results[ResourceKind.VOICEBANK] ?: emptyList(),
                installedPlugins = results[ResourceKind.PLUGIN] ?: emptyList(),
                installedPhonemizers = results[ResourceKind.PHONEMIZER] ?: emptyList(),
                installedForks = scanInstalledForks(),
                installedLoading = false,
                installedLoadedOnce = true,
            )
            lastInstalledScanAt = System.currentTimeMillis()
            // 写回缓存，下次冷启动秒开
            withContext(Dispatchers.IO) {
                runCatching {
                    InstalledCache.save(
                        getApplication(),
                        mapOf(
                            ResourceKind.VOICEBANK to (_state.value.installedVoicebanks),
                            ResourceKind.PLUGIN to (_state.value.installedPlugins),
                            ResourceKind.PHONEMIZER to (_state.value.installedPhonemizers),
                        ),
                    )
                }
            }
        }
    }

    /**
     * 查询已安装的分支改版。
     *
     * 分支改版是一个 APK（第三方 OpenUtau 本体），装完是系统里的一个应用，
     * 不在声库/插件目录里，所以没法用 InstalledScanner，只能问 PackageManager。
     */
    private fun scanInstalledForks(): List<com.utaumarket.mobile.data.model.InstalledFork> {
        val pkgs = com.utaumarket.mobile.data.HostAppDetector.FORK_PACKAGES
        val pm = getApplication<android.app.Application>().packageManager
        return pkgs.mapNotNull { pkg ->
            runCatching {
                val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
                com.utaumarket.mobile.data.model.InstalledFork(
                    packageName = pkg,
                    label = runCatching { pm.getApplicationLabel(info.applicationInfo).toString() }
                        .getOrDefault(pkg),
                    versionName = info.versionName ?: "",
                    versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else {
                        @Suppress("DEPRECATION") info.versionCode.toLong()
                    },
                    isFork = true,
                    lastUpdateTime = info.lastUpdateTime,
                )
            }.getOrNull()
        }
    }

    private suspend fun scanAsync(
        target: com.utaumarket.mobile.saf.TargetDir?,
        kind: ResourceKind,
    ): List<InstalledItem> {
        if (target == null) return emptyList()
        return withContext(Dispatchers.IO) {
            runCatching { InstalledScanner.scan(getApplication(), target, kind) }
                .onFailure { L.w("扫描已安装资源失败 kind=$kind", it) }
                .getOrDefault(emptyList())
        }
    }

    /**
     * 删除已安装条目。
     *
     * SAF 删一个声库要递归删掉成百上千个文件，再走一次完整扫描，整轮下来可能要好几秒。
     * 所以这里**先从界面移除**（用户立刻看到反馈），再放到后台真删，最后重新扫描校正。
     */
    fun deleteInstalled(kind: ResourceKind, items: List<InstalledItem>, onDone: (Int) -> Unit = {}) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }.toSet()
        _state.value = removeInstalledByUri(uris).copy(
            deletingCount = _state.value.deletingCount + items.size,
            message = s(R.string.vm_deleting_count, items.size),
        )
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            // 每删完一项就刷新一次「正在删除 N/M…」，不然大目录删除期间界面完全没动静
            val finished = java.util.concurrent.atomic.AtomicInteger(0)
            val results = withContext(Dispatchers.IO) {
                items.map { item ->
                    async {
                        val tree = Uri.parse(item.parentUri)
                        val docId = docIdOf(item.uri)
                        val r = docId != null && SafManager.delete(ctx, tree, docId)
                        _state.update {
                            it.copy(message = s(R.string.vm_deleting_progress, finished.incrementAndGet(), items.size))
                        }
                        r
                    }
                }.awaitAll()
            }
            val ok = results.count { it }
            _state.value = _state.value.copy(
                deletingCount = (_state.value.deletingCount - items.size).coerceAtLeast(0),
                message = if (ok == items.size) {
                    s(R.string.vm_deleted_count, ok)
                } else {
                    s(R.string.vm_delete_result, ok, items.size)
                },
            )
            // 必须 force：否则 30 秒限流会把这次刷新吞掉，
            // 已安装列表停留在乐观移除的状态，和真实目录对不上。
            refreshInstalled(force = true)
            onDone(ok)
        }
    }

    private fun removeInstalledByUri(uris: Set<String>): UiState {
        val s = _state.value
        return s.copy(
            installedVoicebanks = s.installedVoicebanks.filterNot { it.uri in uris },
            installedPlugins = s.installedPlugins.filterNot { it.uri in uris },
            installedPhonemizers = s.installedPhonemizers.filterNot { it.uri in uris },
        )
    }

    /**
     * 修复已经装到设备上的声库。
     *
     * 方向是**把被错误转成 UTF-8 的内容转回 Shift-JIS** —— OpenUtau 默认就按 Shift-JIS 读
     * （见 TextEncoding.resolveEncoding 的注释），所以之前"转成 UTF-8"才是乱码根源。
     * 同时会把被系统追加的扩展名改回来（oto.ini.txt -> oto.ini）。
     * 如果发现 U+FFFD 替换字符，说明原始字节已经丢失，只能重新下载。
     */
    fun repairEncoding(item: InstalledItem) {
        viewModelScope.launch {
            _state.value = _state.value.copy(message = s(R.string.vm_checking_encoding))
            val pair: Pair<Int, Int> = withContext(Dispatchers.IO) {
                runCatching<Pair<Int, Int>> {
                    val ctx = getApplication<Application>()
                    val tree = Uri.parse(item.parentUri)
                    val dirId = docIdOf(item.uri) ?: return@runCatching (0 to 0)
                    // 先纠正被系统追加的扩展名（oto.ini.txt -> oto.ini）—— 这是声库加载失败的主因
                    var n = SafManager.fixAppendedExtensions(ctx, tree, dirId)
                    var corrupted = 0
                    // OpenUtau 按 **Shift-JIS** 读 character.txt / oto.ini（UTAU 原生格式）。
                    // 所以"修复"的方向是把被转成 UTF-8 的内容**转回 Shift-JIS**，不是转成 UTF-8。
                    val sjisCs = java.nio.charset.Charset.forName("Shift_JIS")
                    val queue = ArrayDeque<String>()
                    queue.addLast(dirId)
                    var guard = 0
                    while (queue.isNotEmpty() && guard < 6000) {
                        val d = queue.removeLast()
                        for (c in SafManager.listChildren(ctx, tree, d)) {
                            guard++
                            if (c.isDirectory) { queue.addLast(c.docId); continue }
                            val lower = c.name.lowercase()
                            if (listOf(".txt", ".ini", ".yaml", ".yml").none { lower.endsWith(it) }) continue
                            if (c.size > 4L * 1024 * 1024) continue
                            val bytes = SafManager.readBytes(ctx, tree, c.docId) ?: continue
                            // 先查有没有被"有损解码"过的痕迹（U+FFFD）—— 那种是救不回来的
                            if (com.utaumarket.mobile.archive.TextEncoding.isCorrupted(bytes)) {
                                corrupted++
                                continue
                            }
                            // 只处理"现在是合法 UTF-8 且含非 ASCII"的文件；已是 Shift-JIS 的不动
                            val text = com.utaumarket.mobile.archive.TextEncoding.decode(bytes, Charsets.UTF_8)
                                ?: continue
                            if (text.all { it.code < 0x80 }) continue
                            val out = runCatching { text.toByteArray(sjisCs) }.getOrNull() ?: continue
                            if (runCatching { String(out, sjisCs) }.getOrNull() != text) continue
                            if (SafManager.writeBytes(ctx, tree, c.docId, out)) n++
                        }
                    }
                    n to corrupted
                }.getOrDefault(0 to 0)
            }
            val fixed = pair.first
            val corrupted = pair.second
            _state.value = _state.value.copy(
                message = when {
                    // 含 U+FFFD 说明文件被"有损解码"过，原始字节已经丢了，任何编码都救不回来
                    corrupted > 0 -> s(R.string.vm_repair_corrupted, corrupted)
                    fixed > 0 -> s(R.string.vm_repair_fixed, fixed)
                    else -> s(R.string.vm_repair_none)
                },
            )
            refreshInstalled()
        }
    }

    /**
     * 已安装条目的官网/详情页：拿目录名去清单里找（先精确、再前缀、最后按系列名）。
     * 找不到就返回空，界面不显示按钮。
     */
    fun officialUrlFor(item: InstalledItem): String {
        val catalog = _state.value.voicebanks + _state.value.plugins + _state.value.phonemizers
        val key = item.name.trim().lowercase()
        if (key.isBlank() || catalog.isEmpty()) return ""
        catalog.firstOrNull { it.name.trim().lowercase() == key }
            ?.let { if (it.detailUrl.isNotBlank()) return it.detailUrl }
        catalog.firstOrNull {
            val n = it.name.trim().lowercase()
            it.detailUrl.isNotBlank() && n.isNotBlank() && (n.startsWith(key) || key.startsWith(n))
        }?.let { return it.detailUrl }
        catalog.firstOrNull {
            it.series.isNotBlank() && it.detailUrl.isNotBlank() &&
                key.startsWith(it.series.trim().lowercase())
        }?.let { return it.detailUrl }
        return ""
    }

    fun openFolder(item: InstalledItem): android.content.Intent? {
        val ctx = getApplication<Application>()
        val tree = Uri.parse(item.parentUri)
        val docId = docIdOf(item.uri) ?: return null
        return runCatching { SafManager.openFolderIntent(ctx, tree, docId) }.getOrNull()
    }

    private fun docIdOf(uri: String): String? = runCatching {
        android.provider.DocumentsContract.getDocumentId(Uri.parse(uri))
    }.getOrNull()

    // ------------------------------------------------------------ 下载

    fun download(item: ResourceItem, overwrite: Boolean): Boolean {
        if (settings.targetFor(item.kind) == null) {
            _state.value = _state.value.copy(message = s(R.string.vm_need_root_settings))
            return false
        }
        val id = DownloadQueueFacade.start(
            context = getApplication(),
            settings = settings,
            item = item,
            overwrite = overwrite,
            useMirror = settings.current.useMirrorFirst,
        )
        if (id == null) {
            _state.value = _state.value.copy(message = s(R.string.vm_download_cannot_start))
            return false
        }
        return true
    }

    // 乐观更新：先立刻改界面状态，再发 intent。
    // 之前完全等服务回调，用户点一下没反应就会连点好几下。
    /**
     * 暂停/继续的防抖。
     *
     * 暂停后按钮会在**同一位置**立刻变成「继续」，
     * 用户以为没点上再点一下，就正好点到了「继续」——
     * 于是任务又被恢复，看起来就是"暂停后自己回弹"。
     * 这里给每个任务加 1.2 秒冷却，连点只认第一次。
     */
    private val lastToggleAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun allowToggle(id: String): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        val last = lastToggleAt[id] ?: 0L
        if (now - last < 1200L) return false
        lastToggleAt[id] = now
        return true
    }

    /**
     * 按钮内的瞬时反馈：点下去后按钮文字变成「暂停中…」等，1.5 秒后恢复。
     * 之前用的是 Toast 小窗，用户明确说"要按下按钮的那种反馈，不要弹小窗"。
     */
    private val pendingActions = kotlinx.coroutines.flow.MutableStateFlow<Map<String, String>>(emptyMap())
    val pendingAction: kotlinx.coroutines.flow.StateFlow<Map<String, String>> = pendingActions

    private fun markPending(id: String, label: String) {
        pendingActions.value = pendingActions.value + (id to label)
        viewModelScope.launch {
            delay(1500)
            pendingActions.value = pendingActions.value - id
        }
    }

    fun setDownloadNoticeHidden(hidden: Boolean) =
        com.utaumarket.mobile.UtauMarketApp.get().settings.setDownloadNoticeHidden(hidden)

    fun pauseTask(task: DownloadTask) {
        if (!allowToggle(task.id)) return
        markPending(task.id, "暂停中…")
        // 已经是暂停态就不要再发一遍 —— 之前连点会重复发 intent，
        // 服务那边反复启停反而更容易出现"点了没反应、要再点一次"。
        if (DownloadController.snapshot().firstOrNull { it.id == task.id }?.status == DownloadStatus.PAUSED) return
        DownloadController.markPaused(task.id)
        DownloadController.setStatus(task.id, DownloadStatus.PAUSED, "已暂停")
        DownloadQueueFacade.pause(getApplication(), task)
        // 兜底：1.2 秒后若还没真正停下（服务没收到 / 被重启），再补发一次
        viewModelScope.launch {
            delay(1200)
            val cur = DownloadController.snapshot().firstOrNull { it.id == task.id }
            if (cur != null && cur.status in setOf(
                    DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING,
                    DownloadStatus.EXTRACTING, DownloadStatus.INSTALLING,
                )
            ) {
                DownloadController.markPaused(task.id)
                DownloadController.setStatus(task.id, DownloadStatus.PAUSED, "已暂停")
                DownloadQueueFacade.pause(getApplication(), task)
            }
        }
    }

    fun resumeTask(task: DownloadTask) {
        if (!allowToggle(task.id)) return
        markPending(task.id, "继续中…")
        val cur = DownloadController.snapshot().firstOrNull { it.id == task.id }
        // 已经在跑就不重复发
        if (cur != null && cur.status in setOf(
                DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING,
                DownloadStatus.EXTRACTING, DownloadStatus.INSTALLING,
            )
        ) return
        // 继续 = 明确要跑：暂停锁、取消锁都要放下，否则会被当成"取消"
        DownloadController.clearPaused(task.id)
        DownloadController.clearCanceled(task.id)
        DownloadController.setStatus(task.id, DownloadStatus.QUEUED, "正在继续…")
        DownloadQueueFacade.resume(getApplication(), task)
        // 兜底：1.2 秒后还没动起来就再发一次（服务没收到 / 被重启）
        viewModelScope.launch {
            delay(1200)
            val now = DownloadController.snapshot().firstOrNull { it.id == task.id }
            if (now != null && now.status == DownloadStatus.PAUSED) {
                DownloadController.clearPaused(task.id)
                DownloadController.clearCanceled(task.id)
                DownloadController.setStatus(task.id, DownloadStatus.QUEUED, "正在继续…")
                DownloadQueueFacade.resume(getApplication(), task)
            }
        }
    }

    fun cancelTask(task: DownloadTask) {
        if (!allowToggle(task.id)) return
        markPending(task.id, "取消中…")
        DownloadController.markCanceled(task.id)
        DownloadController.setStatus(task.id, DownloadStatus.CANCELED, "已取消")
        DownloadQueueFacade.cancel(getApplication(), task)
        // 兜底：1 秒后若又被后台刷回"在动"，再压一次
        viewModelScope.launch {
            delay(1000)
            val cur = DownloadController.snapshot().firstOrNull { it.id == task.id }
            if (cur != null && (cur.status == DownloadStatus.DOWNLOADING ||
                    cur.status == DownloadStatus.QUEUED ||
                    cur.status == DownloadStatus.EXTRACTING ||
                    cur.status == DownloadStatus.INSTALLING)
            ) {
                DownloadController.markCanceled(task.id)
                DownloadController.setStatus(task.id, DownloadStatus.CANCELED, "已取消")
                DownloadQueueFacade.cancel(getApplication(), task)
            }
        }
    }
    fun cancelAllTasks() = DownloadQueueFacade.cancelAll(getApplication())

    /** 批量暂停：所有正在跑的任务一起停 */
    fun pauseAllTasks() {
        val running = DownloadController.snapshot().filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.EXTRACTING || it.status == DownloadStatus.INSTALLING
        }
        running.forEach { pauseTask(it) }
    }

    /** 批量继续：暂停中 + 已失败的任务一起接着下 */
    fun resumeAllTasks() {
        val stalled = DownloadController.snapshot().filter {
            it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED
        }
        stalled.forEach { resumeTask(it) }
    }

    fun retryTask(task: DownloadTask) {
        // 先把旧进度清掉：否则重试后进度条还是上一次的 100%，看起来像没动
        DownloadController.upsert(
            task.copy(
                status = DownloadStatus.QUEUED,
                bytesDownloaded = 0,
                speedBps = 0,
                etaSeconds = 0,
                message = s(R.string.dlmsg_queued_retry),
            ),
        )
        DownloadQueueFacade.resume(getApplication(), task)
    }
    fun clearFinished() = DownloadController.clearFinished()

    // 【性能】已安装名字缓存：之前每个卡片每次重组都跑 list.any { sameSinger(...) }，
    // sameSinger 里还有两次 lowercase()。769 个系列滚动时会做几万次字符串比较。
    private var installedCacheKey: String = ""
    private var installedCache: Map<ResourceKind, List<String>> = emptyMap()

    private fun installedNames(kind: ResourceKind): List<String> {
        val st = _state.value
        val src = when (kind) {
            ResourceKind.VOICEBANK -> st.installedVoicebanks
            ResourceKind.PLUGIN -> st.installedPlugins
            ResourceKind.PHONEMIZER -> st.installedPhonemizers
        }
        val key = kind.id + "#" + src.size + "#" + src.joinToString("|") { it.name }
        if (key != installedCacheKey) {
            installedCacheKey = key
            installedCache = mapOf(
                ResourceKind.VOICEBANK to st.installedVoicebanks.map { it.name.trim().lowercase() },
                ResourceKind.PLUGIN to st.installedPlugins.map { it.name.trim().lowercase() },
                ResourceKind.PHONEMIZER to st.installedPhonemizers.map { it.name.trim().lowercase() },
            )
        }
        return installedCache[kind].orEmpty()
    }

    fun isInstalled(item: ResourceItem): Boolean {
        val list = installedNames(item.kind)
        if (list.isEmpty()) return false
        val target = item.name.trim()
        if (target.isBlank()) return false
        val t = target.lowercase()
        if (list.contains(t)) return true          // 常见的精确命中走 O(1)
        return list.any { installed -> sameSinger(installed, t) }
    }

    /**
     * 判断两个名字是不是同一个声库。
     *
     * 原来用的是"互相 contains"，太松了 —— "琴吹梦" 会被别的已装声库的任意子串命中，
     * 于是明明没下载也显示"已安装"。这里改成：
     * 完全相同，或者其中一个是另一个的**前缀/后缀且长度相当**（用于 "重音テト" ↔ "重音テト 単独音"）。
     */
    private fun sameSinger(a: String, b: String): Boolean {
        val x = a.trim().lowercase()
        val y = b.trim().lowercase()
        if (x.isEmpty() || y.isEmpty()) return false
        if (x == y) return true
        val (short, long) = if (x.length <= y.length) x to y else y to x
        // 短名太短时不做前缀匹配，避免 "梦" 命中一切
        if (short.length < 3) return false
        if (!long.startsWith(short)) return false
        // 长名多出来的必须是分隔符/括号/备注这类尾巴，不能是任意文字
        val tail = long.substring(short.length).trim()
        return tail.isEmpty() || tail.first() in listOf(' ', '(', '（', '[', '【', '-', '_', '·', '/')
    }

    // ------------------------------------------------------------ 音素器（GitHub）

    fun loadPhonemizers(force: Boolean = false) {
        if (_state.value.phonemizerLoading) return
        if (!force && _state.value.phonemizers.isNotEmpty()) return
        viewModelScope.launch {
            // 内置的先无条件放上去 —— GitHub 那边限流/失败也不能让页面是空的。
            // 之前 builtIn 是在 try 里算的，GitHub 一抛异常整个列表就空了，内置的也跟着没了。
            val builtIn = manifest.phonemizers()
            _state.value = _state.value.copy(
                phonemizers = builtIn,
                phonemizerLoading = true,
                phonemizerError = "",
            )
            try {
                val list = github.searchPhonemizers(settings.current.githubToken)
                val merged = LinkedHashMap<String, ResourceItem>()
                (builtIn + list).forEach { merged[it.id] = it }
                _state.value = _state.value.copy(
                    phonemizers = merged.values.toList(),
                    phonemizerLoading = false,
                    phonemizerError = "",
                )
            } catch (t: Throwable) {
                L.w("获取音素器列表失败", t)
                // 保留内置条目，只把错误显示出来
                _state.value = _state.value.copy(
                    phonemizers = builtIn,
                    phonemizerLoading = false,
                    phonemizerError = if (t is GitHubApi.RateLimitException) {
                        // 这条已经是本地化过的提示
                        t.message ?: s(R.string.vm_github_api_failed)
                    } else {
                        com.utaumarket.mobile.core.ErrorText.of(getApplication(), t)
                    },
                )
            }
        }
    }

    // ------------------------------------------------------------ 设置

    fun setTheme(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setOrientation(v: String) = settings.setOrientation(v)
    fun setAutoInstall(v: Boolean) = settings.setAutoInstall(v)
    fun setToken(token: String) = settings.setGithubToken(token)
    fun setMirrorFirst(v: Boolean) = settings.setUseMirrorFirst(v)
    fun setParallelDownload(v: Boolean) = settings.setParallelDownload(v)
    /** 用户同意「使用前须知」 */
    fun acceptDisclaimer() = settings.setDisclaimerAccepted()

    /** 教程看过/跳过：标记后不再自动弹出 */
    fun dismissTutorial() = settings.setTutorialSeen()

    /**
     * 开发者模式：清空全部用户数据，回到"刚装完"的状态。
     * 下次启动会重新弹出免责声明与教程；已安装的声库/插件/工程文件不受影响。
     */
    fun wipeAllUserData() {
        settings.wipeUserData()
        runCatching {
            // 清掉下载记录：逐条 remove，避免留下孤儿 .part 文件
            com.utaumarket.mobile.download.DownloadController.snapshot().forEach {
                com.utaumarket.mobile.download.DownloadController.remove(it.id)
            }
            com.utaumarket.mobile.download.DownloadController.clearFinished()
        }
        com.utaumarket.mobile.download.ParallelDownloader.clearHostMemory()
        // settings.state 由上面的 collectLatest 自动同步回 UiState，这里不用再手动赋值
    }

    /** 重置所有设置到默认状态（不动已安装的文件） */
    fun resetAllSettings() {
        settings.resetToDefaults()
        com.utaumarket.mobile.download.ParallelDownloader.clearHostMemory()
        _state.value = _state.value.copy(message = s(R.string.settings_reset_done))
    }

    fun setConnections(v: Int) {
        settings.setConnections(v)
        // 换了并发数就把"只能单连接"的记忆清掉，否则旧标记会一直压着速度
        com.utaumarket.mobile.download.ParallelDownloader.clearHostMemory()
        _state.value = _state.value.copy(message = s(R.string.vm_connections_changed, v))
    }
    fun setGhMode(v: String) = settings.setGhMode(v)
    fun setGhCustomPrefix(v: String) = settings.setGhCustomPrefix(v)
    fun setOnboardingDone(v: Boolean) = settings.setOnboardingDone(v)

    /** 清掉下到一半的压缩包和解压临时目录（下载卡住/解压失败后最有效的一招） */
    fun clearDownloadCache() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val freed = withContext(Dispatchers.IO) {
                val dirs = listOf(
                    java.io.File(ctx.cacheDir, "utm/partials"),
                    java.io.File(ctx.cacheDir, "utm/work"),
                    java.io.File(ctx.cacheDir, "utm/extract"),
                )
                var bytes = 0L
                dirs.forEach { d ->
                    runCatching {
                        d.walkTopDown().filter { it.isFile }.forEach { bytes += it.length() }
                    }
                }
                dirs.forEach { runCatching { it.deleteRecursively() } }
                bytes
            }
            val text = if (freed >= 1024 * 1024) {
                String.format(java.util.Locale.ROOT, "%.1f MB", freed / 1024.0 / 1024.0)
            } else {
                String.format(java.util.Locale.ROOT, "%d KB", (freed / 1024).coerceAtLeast(1))
            }
            _state.update { it.copy(message = s(R.string.settings_cache_cleared, text)) }
        }
    }

    fun snack(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    fun consumeMessage() {
        if (_state.value.message.isNotEmpty()) {
            _state.value = _state.value.copy(message = "")
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer { AppViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!) }
        }
    }
}
