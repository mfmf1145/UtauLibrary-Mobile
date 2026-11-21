package com.utaumarket.mobile.data.prefs

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.saf.SafManager
import com.utaumarket.mobile.saf.TargetDir
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 深色 / 浅色 / 跟随系统 —— 与 OpenUtau 原版一致：System / Light / Dark 三态 */
enum class ThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

data class AppSettings(
    /** 只授权这一个根目录，其余子目录由程序自动扫描识别 */
    val rootTreeUri: String = "",
    /**
     * 上一次授权过的根目录。**取消授权时不清空** ——
     * 重新授权时用它当选择器的起始位置；之前用的是一个写死的路径（primary:OpenUtau），
     * 那个目录不存在会让选择器直接打不开，表现就是"取消后没法再授权了"。
     */
    val lastRootTreeUri: String = "",
    val voicebankSubdir: String = "Singers",
    val pluginSubdir: String = "Plugins",
    val phonemizerSubdir: String = "",
    /** 扫描得到的子目录 documentId（相对 rootTreeUri） */
    val voicebankDocId: String = "",
    val pluginDocId: String = "",
    val phonemizerDocId: String = "",
    /** 高级：为某一类单独授权的目录树（留空表示跟随根目录自动识别） */
    val voicebankTreeUri: String = "",
    val pluginTreeUri: String = "",
    val phonemizerTreeUri: String = "",
    /**
     * 网络状态指示样式。
     *  · DOT   —— 用主题色画的实心圆（默认，和整体平直风格一致）
     *  · EMOJI —— 系统彩色 emoji 🟢🟡🔴（醒目，但与字体绑定）
     */
    /**
     * 默认隐藏「仅资料」条目。
     *
     * 这类条目没有任何下载途径（只有官网/萌娘百科链接），
     * 混在可下载的条目里会稀释列表的有效信息密度 —— 所以默认隐藏，
     * 需要时在设置里关掉，或在筛选面板里临时选「显示」。
     */
    val hideReference: Boolean = true,
    /** 下载失败后自动重试的次数（0 = 不重试；指数退避） */
    val autoRetryCount: Int = 3,
    /** 启动后在后台预先扫描「已安装」，进页面时直接是结果 */
    val warmScanInstalled: Boolean = true,
    /** 下载/校验失败时保留缓存文件，便于排查（默认关闭，失败即清理） */
    val keepPartialOnFail: Boolean = false,
    /** 强制多连接：忽略一切"该站点只能用单连接"的降级记忆 */
    val forceMultiConnection: Boolean = false,
    val netIndicatorStyle: String = "dot",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 配色方案 id；默认 openutau 原版 */
    val colorScheme: String = "openutau",
    /**
     * 被用户在「自定义导航」里隐藏的页面 id（逗号分隔）。
     *
     * 默认隐藏「分支改版」—— 它是给少数需要第三方魔改本体的用户准备的，
     * 新用户第一次打开不应该被这个陌生入口干扰。用户可在设置 → 自定义导航里打开。
     */
    val hiddenTabs: String = "forks",
    /**
     * 用户上次自定义导航时**已存在**的页面 id。
     * 用来区分「用户主动隐藏」和「后来新增的页面」——
     * 新增页面（比如分支改版）默认要显示，不能因为历史 hiddenTabs 被一起藏掉。
     */
    val navKnownTabs: String = "",
    /** 首次设置教程是否已看过（只看一次） */
    val tutorialSeen: Boolean = false,
    /** 显示方向：auto / landscape / portrait。手机自动旋转关掉时也能强制横屏 */
    val orientation: String = "auto",
    /**
     * 免责声明的版本号。条款改过就把 [DISCLAIMER_VERSION] 加一，
     * 老用户下次启动会重新弹一次（否则改完条款根本没人看到）。
     */
    val disclaimerVersion: Int = 0,
    val githubToken: String = "",
    val autoDeleteAfterInstall: Boolean = false,
    /** 下载完成后是否自动解压并安装；关掉则只把压缩包放到目标目录 */
    val autoInstall: Boolean = true,
    val useMirrorFirst: Boolean = true,
    val parallelDownload: Boolean = false,
    val connections: Int = 32,
    val ghMode: String = "auto",
    val ghCustomPrefix: String = "",
    val lastNetworkProbeAt: Long = 0L,
    val onboardingDone: Boolean = false,
    /** 是否已经看过并同意「使用前须知」（免责声明）。只在第一次启动时弹一次。 */
    val disclaimerAccepted: Boolean = false,
    /** 下载须知里勾了「不再提示」；默认 false（每次下载都弹） */
    val downloadNoticeHidden: Boolean = false,
    /** 界面语言；空串表示跟随系统。取值："" / "zh-CN" / "zh-TW" / "en" / "ja" */
    val appLanguage: String = "",
    /** 列表与卡片的过渡动画；关掉可以让界面更"静"、也更省电 */
    val animationsEnabled: Boolean = true,
    val lastScanSummary: String = "",
    val lastScanAt: Long = 0L,
)

/**
 * 设置持久化。
 *
 * 目录模型：**只授权一个根目录**，程序扫描它并自动认领/创建 Singers、Plugins 等子目录。
 * 这样用户不需要一个个手动指定。高级用户仍可对某一类单独授权（会覆盖自动识别结果）。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("utaumarket_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    init {
        // 实测多线程在部分站点慢 30 倍，且会让进度/续传错乱：历史开启过的一律关掉
        if (!prefs.getBoolean(KEY_MT_MIGRATED, false)) {
            prefs.edit {
                putBoolean(KEY_PARALLEL, false)
                putBoolean(KEY_MT_MIGRATED, true)
            }
        }
        // 历史遗留：某一类"单独授权"的目录和根目录是同一个 → 它会让资源直接装到根目录下
        // （而不是根目录/Singers），既乱又和"一个根目录自动识别"的模型冲突，直接清掉。
        val root = prefs.getString(KEY_ROOT, "") ?: ""
        if (root.isNotBlank()) {
            val same = listOf(KEY_VB, KEY_PLUGIN, KEY_PHONEMIZER).filter {
                val v = prefs.getString(it, "") ?: ""
                v.isNotBlank() && v == root
            }
            if (same.isNotEmpty()) {
                prefs.edit { same.forEach { key -> putString(key, "") } }
            }
        }
    }

    private fun read(): AppSettings = AppSettings(
        rootTreeUri = prefs.getString(KEY_ROOT, "") ?: "",
        lastRootTreeUri = prefs.getString(KEY_LAST_ROOT, "") ?: "",
        voicebankSubdir = prefs.getString(KEY_VB_SUB, "Singers") ?: "Singers",
        pluginSubdir = prefs.getString(KEY_PL_SUB, "Plugins") ?: "Plugins",
        phonemizerSubdir = prefs.getString(KEY_PH_SUB, "") ?: "",
        voicebankDocId = prefs.getString(KEY_VB_DOC, "") ?: "",
        pluginDocId = prefs.getString(KEY_PL_DOC, "") ?: "",
        phonemizerDocId = prefs.getString(KEY_PH_DOC, "") ?: "",
        voicebankTreeUri = prefs.getString(KEY_VB, "") ?: "",
        pluginTreeUri = prefs.getString(KEY_PLUGIN, "") ?: "",
        phonemizerTreeUri = prefs.getString(KEY_PHONEMIZER, "") ?: "",
        themeMode = ThemeMode.fromId(prefs.getString(KEY_THEME, "system")),
        colorScheme = prefs.getString(KEY_COLOR_SCHEME, "openutau") ?: "openutau",
        hiddenTabs = prefs.getString(KEY_HIDDEN_TABS, "forks") ?: "forks",
        navKnownTabs = prefs.getString(KEY_NAV_KNOWN_TABS, "") ?: "",
        tutorialSeen = prefs.getBoolean(KEY_TUTORIAL_SEEN, false),
        orientation = prefs.getString(KEY_ORIENTATION, "auto") ?: "auto",
        githubToken = prefs.getString(KEY_TOKEN, "") ?: "",
        autoDeleteAfterInstall = prefs.getBoolean(KEY_AUTO_DELETE, false),
        autoInstall = prefs.getBoolean(KEY_AUTO_INSTALL, true),
        useMirrorFirst = prefs.getBoolean(KEY_MIRROR_FIRST, true),
        parallelDownload = prefs.getBoolean(KEY_PARALLEL, false),
        // 读到不在档位里的旧值时回到默认，避免设置页没有任何 chip 高亮
        connections = prefs.getInt(KEY_CONNECTIONS, 32).let {
            if (it in ALLOWED_CONNECTIONS) it else 32
        },
        ghMode = prefs.getString(KEY_GH_MODE, "auto") ?: "auto",
        ghCustomPrefix = prefs.getString(KEY_GH_CUSTOM, "") ?: "",
        lastNetworkProbeAt = prefs.getLong(KEY_PROBE_AT, 0L),
        onboardingDone = prefs.getBoolean(KEY_ONBOARDING, false),
        disclaimerAccepted = prefs.getBoolean(KEY_DISCLAIMER, false),
        downloadNoticeHidden = prefs.getBoolean(KEY_DL_NOTICE_HIDDEN, false),
        appLanguage = prefs.getString(KEY_LANG, "") ?: "",
        disclaimerVersion = prefs.getInt(KEY_DISCLAIMER_VERSION, 0),
        animationsEnabled = prefs.getBoolean(KEY_ANIM, true),
        netIndicatorStyle = prefs.getString(KEY_NET_INDICATOR, "dot") ?: "dot",
        hideReference = prefs.getBoolean(KEY_HIDE_REFERENCE, true),
        autoRetryCount = prefs.getInt(KEY_AUTO_RETRY, 3),
        warmScanInstalled = prefs.getBoolean(KEY_WARM_SCAN, true),
        keepPartialOnFail = prefs.getBoolean(KEY_KEEP_PARTIAL, false),
        forceMultiConnection = prefs.getBoolean("force_multi", false),
        lastScanSummary = prefs.getString(KEY_SCAN_SUMMARY, "") ?: "",
        lastScanAt = prefs.getLong(KEY_SCAN_AT, 0L),
    )

    private fun update(block: (SharedPreferences.Editor) -> Unit) {
        prefs.edit { block(this) }
        _state.value = read()
    }

    fun setRootTree(uri: String) = update {
        it.putString(KEY_ROOT, uri)
        it.putString(KEY_LAST_ROOT, uri)
    }
    fun setSubdirs(vb: String, pl: String) = update {
        it.putString(KEY_VB_SUB, vb)
        it.putString(KEY_PL_SUB, pl)
    }
    fun setResolvedDocIds(vb: String, pl: String, ph: String) = update {
        it.putString(KEY_VB_DOC, vb)
        it.putString(KEY_PL_DOC, pl)
        it.putString(KEY_PH_DOC, ph)
    }
    fun setScanSummary(text: String, at: Long) = update {
        it.putString(KEY_SCAN_SUMMARY, text)
        it.putLong(KEY_SCAN_AT, at)
    }

    fun setVoicebankTree(uri: String) = update { it.putString(KEY_VB, uri) }
    fun setPluginTree(uri: String) = update { it.putString(KEY_PLUGIN, uri) }
    fun setPhonemizerTree(uri: String) = update { it.putString(KEY_PHONEMIZER, uri) }
    fun setThemeMode(mode: ThemeMode) = update { it.putString(KEY_THEME, mode.id) }

    fun setColorScheme(id: String) = update { it.putString(KEY_COLOR_SCHEME, id) }

    fun setHiddenTabs(ids: String) = update { it.putString(KEY_HIDDEN_TABS, ids) }

    /** 用户改导航时，同时记下"当前有哪些页面"，供以后判断哪些是新增的 */
    fun setNavKnownTabs(ids: String) = update { it.putString(KEY_NAV_KNOWN_TABS, ids) }

    fun setTutorialSeen() = update { it.putBoolean(KEY_TUTORIAL_SEEN, true) }
    fun setOrientation(v: String) = update { it.putString(KEY_ORIENTATION, v) }
    fun setGithubToken(token: String) = update { it.putString(KEY_TOKEN, token) }
    fun setAutoDeleteAfterInstall(v: Boolean) = update { it.putBoolean(KEY_AUTO_DELETE, v) }
    fun setAutoInstall(v: Boolean) = update { it.putBoolean(KEY_AUTO_INSTALL, v) }
    fun setUseMirrorFirst(v: Boolean) = update { it.putBoolean(KEY_MIRROR_FIRST, v) }
    fun setParallelDownload(v: Boolean) = update { it.putBoolean(KEY_PARALLEL, v) }
    fun setConnections(v: Int) = update {
        // 只接受实际生效的档位（8 / 16 / 32），其他值一律按 32 保存
        it.putInt(KEY_CONNECTIONS, if (v in ALLOWED_CONNECTIONS) v else 32)
    }
    /**
     * 把所有设置恢复到默认状态。
     *
     * 【保留】
     *  · 免责声明的接受记录 —— 条款没变，没必要强迫用户再读一遍
     *  · 教程已看过的标记 —— 用户主动重置设置，不代表他想再看一次新手教程
     *
     * 【清空】其余全部：主题、配色、隐藏标签、方向、并发、加速线路、GitHub 令牌、
     * 自动安装/删除开关、下载须知、语言、目录授权（会要求重新授权）。
     *
     * 说明：这里清的是"配置"，不会删除任何已安装到文件夹里的声库/插件。
     */
    fun resetToDefaults() {
        val keepDisclaimer = prefs.getBoolean(KEY_DISCLAIMER, false)
        val keepDisclaimerVersion = prefs.getInt(KEY_DISCLAIMER_VERSION, 0)
        val keepTutorial = prefs.getBoolean(KEY_TUTORIAL_SEEN, false)
        val ed = prefs.edit()
        ed.clear()
        ed.putBoolean(KEY_DISCLAIMER, keepDisclaimer)
        ed.putInt(KEY_DISCLAIMER_VERSION, keepDisclaimerVersion)
        ed.putBoolean(KEY_TUTORIAL_SEEN, keepTutorial)
        ed.apply()
        _state.value = read()
    }

    /**
     * 开发者模式用：清空**全部**用户数据，回到刚安装完的状态。
     *
     * 与 [resetToDefaults] 的区别：这里连「免责声明已同意」「教程已看过」也一起清掉，
     * 所以下次启动会重新走一遍首次启动流程（弹条款、弹教程）。
     * **不会删除任何已安装到文件夹里的声库 / 插件 / 工程文件。**
     */
    fun wipeUserData() {
        prefs.edit().clear().apply()
        _state.value = read()
    }

    fun setGhMode(v: String) = update { it.putString(KEY_GH_MODE, v) }
    fun setGhCustomPrefix(v: String) = update { it.putString(KEY_GH_CUSTOM, v) }
    fun setLastNetworkProbeAt(v: Long) = update { it.putLong(KEY_PROBE_AT, v) }
    fun setOnboardingDone(v: Boolean) = update { it.putBoolean(KEY_ONBOARDING, v) }
    fun setDownloadNoticeHidden(hidden: Boolean) = update {
        it.putBoolean(KEY_DL_NOTICE_HIDDEN, hidden)
    }

    fun setDisclaimerAccepted() = update {
        it.putBoolean(KEY_DISCLAIMER, true)
        it.putInt(KEY_DISCLAIMER_VERSION, DISCLAIMER_VERSION)
    }

    fun setAppLanguage(tag: String) = update { it.putString(KEY_LANG, tag) }

    fun setAnimationsEnabled(v: Boolean) = update { it.putBoolean(KEY_ANIM, v) }

    /** 失败时保留缓存（排障用） */
    fun setKeepPartialOnFail(v: Boolean) = update { it.putBoolean(KEY_KEEP_PARTIAL, v) }

    /** 强制多连接：写入后立即通知下载器，不必重启 */
    fun setForceMultiConnection(v: Boolean) {
        update { it.putBoolean("force_multi", v) }
        com.utaumarket.mobile.download.ParallelDownloader.setForceMulti(v)
    }

    /** 启动时后台预扫描已安装 */
    fun setWarmScanInstalled(v: Boolean) = update { it.putBoolean(KEY_WARM_SCAN, v) }

    /** 下载失败自动重试次数 */
    fun setAutoRetryCount(v: Int) = update { it.putInt(KEY_AUTO_RETRY, v.coerceIn(0, 10)) }

    /** 是否默认隐藏「仅资料」条目 */
    fun setHideReference(v: Boolean) = update { it.putBoolean(KEY_HIDE_REFERENCE, v) }

    /** 切换网络状态指示样式："dot"（圆点）或 "emoji" */
    fun setNetIndicatorStyle(v: String) = update { it.putString(KEY_NET_INDICATOR, v) }

    /** 某一类资源实际使用的目标目录；没有配置时返回 null。 */
    fun targetFor(kind: ResourceKind): TargetDir? {
        val s = current
        val explicit = when (kind) {
            ResourceKind.VOICEBANK -> s.voicebankTreeUri
            ResourceKind.PLUGIN -> s.pluginTreeUri
            ResourceKind.PHONEMIZER -> s.phonemizerTreeUri
        }
        if (explicit.isNotBlank()) {
            val docId = SafManager.treeDocId(Uri.parse(explicit)) ?: return null
            return TargetDir(explicit, docId, explicit = true)
        }
        if (s.rootTreeUri.isBlank()) return null
        val docId = when (kind) {
            ResourceKind.VOICEBANK -> s.voicebankDocId
            ResourceKind.PLUGIN -> s.pluginDocId
            ResourceKind.PHONEMIZER -> s.phonemizerDocId.ifBlank { s.pluginDocId }
        }
        if (docId.isBlank()) return null
        return TargetDir(s.rootTreeUri, docId, explicit = false)
    }

    /** 兼容旧调用：返回 tree uri 字符串，未配置返回空串。 */
    fun rootFor(kind: ResourceKind): String = targetFor(kind)?.treeUri ?: ""

    /** 是否已经完成目录配置 */
    fun isConfigured(): Boolean = targetFor(ResourceKind.VOICEBANK) != null || targetFor(ResourceKind.PLUGIN) != null

    fun releaseAll() = update {
        it.putString(KEY_VB, ""); it.putString(KEY_PLUGIN, ""); it.putString(KEY_PHONEMIZER, "")
    }

    fun releaseRoot() = update {
        it.putString(KEY_ROOT, "")
        it.putString(KEY_VB_DOC, ""); it.putString(KEY_PL_DOC, ""); it.putString(KEY_PH_DOC, "")
        it.putString(KEY_SCAN_SUMMARY, "")
    }

    companion object {
        private const val KEY_ROOT = "root_tree_uri"
        private const val KEY_LAST_ROOT = "last_root_tree_uri"
        private const val KEY_VB_SUB = "voicebank_subdir"
        private const val KEY_PL_SUB = "plugin_subdir"
        private const val KEY_PH_SUB = "phonemizer_subdir"
        private const val KEY_VB_DOC = "voicebank_doc_id"
        private const val KEY_PL_DOC = "plugin_doc_id"
        private const val KEY_PH_DOC = "phonemizer_doc_id"
        private const val KEY_SCAN_SUMMARY = "scan_summary"
        private const val KEY_SCAN_AT = "scan_at"
        private const val KEY_VB = "voicebank_tree_uri"
        private const val KEY_PLUGIN = "plugin_tree_uri"
        private const val KEY_PHONEMIZER = "phonemizer_tree_uri"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_COLOR_SCHEME = "color_scheme"
        private const val KEY_HIDDEN_TABS = "hidden_tabs"
        private const val KEY_NAV_KNOWN_TABS = "nav_known_tabs"
        private const val KEY_TUTORIAL_SEEN = "tutorial_seen"
        private const val KEY_ORIENTATION = "display_orientation"
        private const val KEY_TOKEN = "github_token"
        private const val KEY_AUTO_DELETE = "auto_delete_after_install"
        private const val KEY_AUTO_INSTALL = "auto_install_after_download"
        private const val KEY_MIRROR_FIRST = "use_mirror_first"
        private const val KEY_PARALLEL = "parallel_download"
        private const val KEY_MT_MIGRATED = "parallel_download_migrated_v2"
        private const val KEY_CONNECTIONS = "download_connections"

        /** 实际可选的并发档位（与设置页 chips、ParallelDownloader.MAX_WORKERS 一致） */
        val ALLOWED_CONNECTIONS = setOf(8, 16, 32, 48, 64)
        private const val KEY_GH_MODE = "gh_accel_mode"
        private const val KEY_GH_CUSTOM = "gh_accel_custom"
        private const val KEY_PROBE_AT = "last_network_probe_at"
        private const val KEY_ONBOARDING = "onboarding_done"
        private const val KEY_DISCLAIMER = "disclaimer_accepted"
        private const val KEY_LANG = "app_language"
        private const val KEY_DL_NOTICE_HIDDEN = "download_notice_hidden"
        private const val KEY_DISCLAIMER_VERSION = "disclaimer_version"
        /** 当前生效的免责声明版本；正文在 ui/screens/DisclaimerTexts.kt */
        // v3：新增「第八节 使用限制」——严禁将本程序用于盈利或商业用途
        const val DISCLAIMER_VERSION = 3
        private const val KEY_ANIM = "animations_enabled"
        private const val KEY_NET_INDICATOR = "net_indicator_style"
        private const val KEY_HIDE_REFERENCE = "hide_reference_default"
        private const val KEY_AUTO_RETRY = "auto_retry_count"
        private const val KEY_WARM_SCAN = "warm_scan_installed"
        private const val KEY_KEEP_PARTIAL = "keep_partial_on_fail"
    }
}
