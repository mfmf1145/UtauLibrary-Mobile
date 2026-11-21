package com.utaumarket.mobile.ui

import com.utaumarket.mobile.R

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.utaumarket.mobile.data.model.NetworkStatus
import com.utaumarket.mobile.data.model.DownloadStatus
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.saf.SafManager
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuBottomNav
import com.utaumarket.mobile.ui.components.OuDialog
import com.utaumarket.mobile.ui.components.OuIconButton
import com.utaumarket.mobile.ui.components.OuNavItem
import com.utaumarket.mobile.ui.components.OuTag
import com.utaumarket.mobile.ui.components.OuTopBar
import com.utaumarket.mobile.ui.screens.DownloadsScreen
import com.utaumarket.mobile.ui.screens.InstalledScreen
import com.utaumarket.mobile.ui.screens.PhonemizersScreen
import com.utaumarket.mobile.ui.screens.PluginsScreen
import com.utaumarket.mobile.ui.screens.SettingsScreen
import com.utaumarket.mobile.ui.screens.isLandscape
import com.utaumarket.mobile.ui.screens.VoicebanksScreen
import com.utaumarket.mobile.ui.screens.groupBySeries
import com.utaumarket.mobile.ui.theme.OuIcons
import com.utaumarket.mobile.ui.theme.OuTheme
import com.utaumarket.mobile.ui.vm.AppViewModel
import com.utaumarket.mobile.ui.vm.UiState
import kotlinx.coroutines.delay


@Composable
fun AppRoot(vm: AppViewModel = viewModel(factory = AppViewModel.factory())) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()

    // 用 rememberSaveable：切换语言会重建 Activity，普通 remember 会把它重置回 0（插件页）
    var tab by rememberSaveable { mutableIntStateOf(0) }
    /** 工程页「去声库找某歌手」时携带的搜索词；声库页取走后清空 */
    var singerSearch by rememberSaveable { mutableStateOf("") }
    var showDownloads by rememberSaveable { mutableStateOf(false) }
    var pendingKind by remember { mutableStateOf<ResourceKind?>(null) }
    var pendingRoot by remember { mutableStateOf(false) }
    // 二级页面：某个声库系列的分支版本列表
    // 记住"正在看哪个系列"的 key（String 可保存），Activity 重建后能恢复回去 ——
    // 之前切换界面语言会 recreate，用户正在看的系列详情页会被直接关掉。
    var seriesKey by rememberSaveable { mutableStateOf("") }
    // 【性能】groupBySeries 是 991 条的重活，绝不能每帧算 —— 只在声库列表变化时重算
    val allSeries = remember(state.voicebanks) { state.voicebanks.groupBySeries() }
    val series: com.utaumarket.mobile.ui.screens.SeriesGroup? =
        if (seriesKey.isBlank()) null
        else allSeries.firstOrNull { it.key == seriesKey }

    // 按 tab 保存可记住的状态（列表滑动位置、筛选等），切走再切回来不会重置
    // 把动画开关同步到全局（列表组件读它）
    com.utaumarket.mobile.ui.screens.UiFlags.animations = state.settings.animationsEnabled

    val saveableHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    // 屏幕边缘右滑 = 系统返回手势。只要还有"上一级"，就回上一级而**不是直接退出 App**。
    // 之前没有这个处理，在二级版本页 / 下载面板上右滑会直接把程序关掉。
    androidx.activity.compose.BackHandler(enabled = showDownloads || series != null) {
        when {
            showDownloads -> showDownloads = false
            series != null -> seriesKey = ""
        }
    }

    // 应用「显示方向」设置（手机自动旋转关掉时也能强制横屏）
    val appCtx = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(state.settings.orientation) {
        val act = appCtx as? android.app.Activity ?: return@LaunchedEffect
        act.requestedOrientation = when (state.settings.orientation) {
            "landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            "portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // 切到「已安装」时顺手刷新一次，避免刚装完进来还是旧列表
    fun selectTab(i: Int) {
        tab = i
        seriesKey = ""
        if (i == 4) runCatching { vm.refreshInstalled() }
        // 切到音素器标签时补一次加载（内置条目已经有了，这里只是顺便拉 GitHub 的补充）
        if (i == 1) runCatching { vm.loadPhonemizers() }
        // 切到工程页时自动扫描一次
        if (i == 3) runCatching { vm.refreshProjects() }
    }

    fun openSeries(g: com.utaumarket.mobile.ui.screens.SeriesGroup) {
        seriesKey = g.key
    }
    var pendingDownload by remember { mutableStateOf<ResourceItem?>(null) }
    /** 待确认「下载须知」的条目；非空时弹窗 */
    var pendingNotice by remember { mutableStateOf<ResourceItem?>(null) }
    /** 下载须知里的「不再提示」勾选状态（默认不勾） */
    var noticeDontShow by remember { mutableStateOf(false) }
    var showOnboarding by remember { mutableStateOf(false) }
    var crashText by remember { mutableStateOf(com.utaumarket.mobile.core.CrashReporter.lastCrash(ctx)) }

    // 首次启动引导（免责声明同意之后才进入目录引导）
    LaunchedEffect(
        state.settings.disclaimerAccepted,
        state.settings.onboardingDone,
        state.settings.voicebankTreeUri,
        state.settings.pluginTreeUri,
    ) {
        if (!state.settings.disclaimerAccepted) return@LaunchedEffect
        if (!state.settings.onboardingDone && state.settings.voicebankTreeUri.isBlank() && state.settings.pluginTreeUri.isBlank()) {
            showOnboarding = true
        }
    }

    // 非 composable 的回调里取不到 stringResource，先在组合里把三类资源的称呼取好
    val kindVoicebankText = stringResource(R.string.kind_voicebank)
    val kindPluginText = stringResource(R.string.kind_plugin)
    val kindPhonemizerText = stringResource(R.string.kind_phonemizer)
    fun kindText(k: ResourceKind): String = when (k) {
        ResourceKind.VOICEBANK -> kindVoicebankText
        ResourceKind.PLUGIN -> kindPluginText
        ResourceKind.PHONEMIZER -> kindPhonemizerText
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val kind = pendingKind
        val isRoot = pendingRoot
        pendingKind = null
        pendingRoot = false
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val taken = SafManager.persist(ctx, uri)
            if (isRoot) {
                vm.setRootTree(uri.toString())
                vm.snack(
                    if (taken) {
                        ctx.getString(R.string.root_authorized_scanning)
                    } else {
                        ctx.getString(R.string.root_authorized_persist_failed)
                    }
                )
            } else if (kind != null) {
                vm.setTreeUri(kind, uri.toString())
                vm.snack(
                    if (taken) {
                        ctx.getString(R.string.root_authorized_kind, kindText(kind))
                    } else {
                        ctx.getString(R.string.root_authorized_persist_failed)
                    }
                )
            }
        } else {
            vm.snack(ctx.getString(R.string.root_pick_canceled))
        }
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun pickRoot() {
        pendingKind = null
        pendingRoot = true
        // 起点优先用当前根目录；取消授权后用"上次授权过的"；
        // 都没有就不传提示，让系统选择器自己决定 —— 传一个不存在的路径会让它打不开
        val raw = state.settings.rootTreeUri.ifBlank { state.settings.lastRootTreeUri }
        val hint = raw.takeIf { it.isNotBlank() }?.let { runCatching { android.net.Uri.parse(it) }.getOrNull() }
        treePicker.launch(SafManager.buildOpenTreeIntent(hint))
    }

    fun pickDirectory(kind: ResourceKind) {
        pendingRoot = false
        pendingKind = kind
        val initial = when (kind) {
            ResourceKind.VOICEBANK -> state.settings.voicebankTreeUri
            ResourceKind.PLUGIN -> state.settings.pluginTreeUri
            ResourceKind.PHONEMIZER -> state.settings.phonemizerTreeUri
        }.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) }
        treePicker.launch(SafManager.buildOpenTreeIntent(initial))
    }

    /** 真正开始下载（下载须知通过后调用） */
    fun doStartDownload(item: ResourceItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
        if (vm.settings.rootFor(item.kind).isBlank()) {
            vm.snack(ctx.getString(R.string.root_need_authorize, kindText(item.kind)))
            tab = 5
            return
        }
        if (vm.isInstalled(item)) {
            pendingDownload = item
        } else {
            vm.download(item, overwrite = true)
            showDownloads = true
        }
    }

    fun startDownload(item: ResourceItem) {
        // 下载须知：用户勾了「不再提示」才直接下，否则先弹窗确认
        if (!vm.settings.current.downloadNoticeHidden) {
            pendingNotice = item
            return
        }
        doStartDownload(item)
    }

    // 导航标签走资源文件，跟随系统语言（英 / 简中 / 繁中 / 日）
    // 「页面 id → 屏幕下标」的规范映射。下标是稳定的，用户隐藏页面只影响显示顺序。
    //   0 插件 / 1 音素器 / 2 声库 / 3 工程 / 4 已安装 / 5 分支改版 / 6 设置
    data class NavSlot(val id: String, val index: Int, val item: OuNavItem)
    val allSlots = listOf(
        NavSlot("plugins", 0, OuNavItem(stringResource(R.string.nav_plugins), OuIcons.plugin)),
        NavSlot("phonemizers", 1, OuNavItem(stringResource(R.string.nav_phonemizers), OuIcons.phonemizer)),
        NavSlot("voicebanks", 2, OuNavItem(stringResource(R.string.nav_voicebanks), OuIcons.voicebank)),
        NavSlot("forks", 5, OuNavItem(stringResource(R.string.nav_forks), OuIcons.plugin)),
        NavSlot("projects", 3, OuNavItem(stringResource(R.string.nav_projects), OuIcons.document)),
        NavSlot("installed", 4, OuNavItem(stringResource(R.string.nav_installed), OuIcons.installed)),
        NavSlot("settings", 6, OuNavItem(stringResource(R.string.nav_settings), OuIcons.settings)),
    )
    // 用户在「自定义导航」里隐藏的页面
    var hiddenTabs = state.settings.hiddenTabs.split(',').filter { it.isNotBlank() }.toSet()
    // 【新增页面自动显示】老用户自定义过导航后，后来新增的页面不能被历史记录顺手藏掉。
    // 注意：新安装用户的默认值是 hiddenTabs="forks"（分支改版默认不显示），
    // 此时 navKnownTabs 为空，不会走到下面这段，所以默认隐藏依然生效。
    val navKnownTabs = state.settings.navKnownTabs.split(',').filter { it.isNotBlank() }.toSet()
    if (navKnownTabs.isNotEmpty()) {
        val brandNew = allSlots.map { it.id }.filter { it !in navKnownTabs }.toSet()
        if (brandNew.isNotEmpty()) hiddenTabs = hiddenTabs - brandNew
    }
    var navSlots = allSlots.filter { it.id !in hiddenTabs }
    // 兜底：全隐藏了至少要留一个，否则界面直接空白
    if (navSlots.isEmpty()) navSlots = allSlots.take(1)
    val navItems = navSlots.map { it.item }
    // 当前显示的下标（用于导航高亮）；tab 是规范下标，可能不在可见列表里
    val navSelected = navSlots.indexOfFirst { it.index == tab }.let { if (it >= 0) it else 0 }

    val activeDownloads = state.tasks.count {
        it.status != DownloadStatus.DONE && it.status != DownloadStatus.FAILED && it.status != DownloadStatus.CANCELED
    }
    val pausedDownloads = state.tasks.count { it.status == DownloadStatus.PAUSED }
    // 真正在跑（下载/解压/写入）时才让图标闪；只是排队就不闪
    val working = state.tasks.any {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.EXTRACTING || it.status == DownloadStatus.INSTALLING
    }
    val downloadBadge = if (activeDownloads > 0) activeDownloads else pausedDownloads
    val badgeColor = if (activeDownloads > 0) {
        OuTheme.colors.accent
    } else {
        OuTheme.colors.neutralAccent
    }

    val landscape = isLandscape()
    Row(
        Modifier
            .fillMaxSize()
            .background(OuTheme.colors.background)
            .systemBarsPadding()
    ) {
        // 横屏：导航移到左侧竖轨，腾出垂直空间
        if (landscape && !showDownloads) {
            com.utaumarket.mobile.ui.components.OuNavRail(
                items = navItems,
                selectedIndex = navSelected,
                // 导航给的是「可见位置」，映射回规范的屏幕下标
                onSelect = { pos -> navSlots.getOrNull(pos)?.let { selectTab(it.index) } },
            )
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
        OuTopBar(
            title = when {
                showDownloads -> stringResource(R.string.app_downloads_title)
                series != null -> series!!.head.series.ifBlank { series!!.head.name }
                else -> allSlots.firstOrNull { it.index == tab }?.item?.label
                    ?: navItems.first().label
            },
            subtitle = if (showDownloads) stringResource(R.string.app_downloads_subtitle) else appSubtitle(tab, state),
            actions = {
                if (showDownloads) {
                    OuIconButton(painterResource(OuIcons.back), stringResource(R.string.action_back), { showDownloads = false })
                } else {
                    OuIconButton(
                        painter = painterResource(OuIcons.cloudDownload),
                        contentDescription = if (activeDownloads > 0) {
                            stringResource(R.string.app_downloads_cd_active, activeDownloads)
                        } else {
                            stringResource(R.string.app_downloads_title)
                        },
                        onClick = { showDownloads = true },
                        // 有任务时图标变强调色并呼吸闪烁，右上角显示个数
                        tint = if (activeDownloads > 0) OuTheme.colors.accent else null,
                        badge = downloadBadge,
                        badgeColor = badgeColor,
                        pulsing = working,
                    )
                    OuIconButton(
                        painterResource(OuIcons.refresh),
                        stringResource(R.string.action_refresh),
                        {
                            vm.refreshInstalled()
                            // 重新读一遍音源清单（外部清单优先，不用重装就能更新）
                            vm.reloadCatalog()
                            // 顺手把站点连通性重新测一遍，别让一次误判一直显示红点
                            vm.refreshNetwork()
                            if (tab == 1) vm.loadPhonemizers(force = true)
                        },
                    )
                }
            },
        )
        if (activeDownloads > 0 && !showDownloads) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(OuIcons.cloudDownload), null, tint = OuTheme.colors.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.app_downloads_running, activeDownloads),
                    style = OuTheme.typography.small,
                    color = OuTheme.colors.foreground,
                )
                Spacer(Modifier.weight(1f))
                OuButton(stringResource(R.string.action_view), onClick = { showDownloads = true }, variant = OuButtonVariant.Clear)
            }
        }

        // 横屏统一限制内容宽度并居中：以前各页面各写各的（工程页加了留白、插件页没有），
        // 看起来"两个页面不一样"。现在统一在这里约束，所有页面视觉一致。
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = if (landscape) Alignment.TopCenter else Alignment.TopStart,
        ) {
          Column(
              Modifier
                  .fillMaxHeight()
                  .then(if (landscape) Modifier.width(1240.dp) else Modifier.fillMaxWidth()),
          ) {
            // 授权失效时最顶上给一条能直接点的提示，别等到安装失败才让人一脸问号
            if (state.rootInvalid) {
                com.utaumarket.mobile.ui.screens.RootAuthBanner(onFix = ::pickRoot)
            }
            Box(Modifier.weight(1f)) {
            val openSeries = series
            if (showDownloads) {
                // 下载页也包一层 SaveableStateProvider：
                // 之前切回列表再进下载页，滚动位置全丢，跳到最顶部（用户反馈误触很难受）。
                saveableHolder.SaveableStateProvider("downloads") {
                    DownloadsScreen(vm, state)
                }
            } else if (openSeries != null) {
                com.utaumarket.mobile.ui.screens.SeriesScreen(
                    vm = vm,
                    state = state,
                    group = openSeries,
                    onBack = { seriesKey = "" },
                    onRequestDownload = ::startDownload,
                )
            } else {
                // 【关键】用 SaveableStateProvider 按 tab 保存状态。
                // 之前切到设置再切回来，列表的滑动位置、筛选条件全部重置 ——
                // 因为 composable 离开组合后 remember/rememberSaveable 都会丢。
                // 包一层之后 LazyListState 等可保存状态会按 tab 恢复。
                saveableHolder.SaveableStateProvider(tab) {
                when (tab) {
                    0 -> PluginsScreen(vm, state, ::startDownload, ::pickDirectory)
                    1 -> PhonemizersScreen(vm, state, ::startDownload, ::pickDirectory)
                    2 -> VoicebanksScreen(
                        vm, state, ::startDownload, ::pickDirectory, ::openSeries,
                        searchRequest = singerSearch,
                        onSearchRequestHandled = { singerSearch = "" },
                    )
                    3 -> com.utaumarket.mobile.ui.screens.ProjectsScreen(
                        vm, state,
                        onGoSettings = { tab = 6 },
                        onSearchSinger = { name ->
                            // 跳到声库页并把搜索词填好，声库页会在收到请求后自动应用
                            singerSearch = name
                            tab = 2
                        },
                    )
                    4 -> InstalledScreen(vm, state, ::pickDirectory, onGoSettings = { tab = 6 })
                    5 -> PluginsScreen(vm, state, ::startDownload, ::pickDirectory, forksOnly = true)
                    else -> SettingsScreen(vm, state, ::pickDirectory, ::pickRoot)
                }
                }
            }
            }
          }
        }

        // 轻量消息提示（不使用 Material Snackbar，保持 OpenUtau 的直角描边风格）
        if (state.message.isNotBlank()) {
            LaunchedEffect(state.message) {
                delay(3200)
                vm.consumeMessage()
            }
            Box(Modifier.fillMaxWidth().padding(8.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(OuTheme.colors.background)
                        .border(1.dp, OuTheme.colors.accent)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(OuIcons.info), null, tint = OuTheme.colors.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(state.message, style = OuTheme.typography.small, color = OuTheme.colors.foreground)
                }
            }
        }

        if (!showDownloads && !landscape) {
            OuBottomNav(
                items = navItems,
                selectedIndex = navSelected,
                onSelect = { pos -> navSlots.getOrNull(pos)?.let { selectTab(it.index) } },
            )
        }
        }
    }

    // 重复下载确认
    pendingDownload?.let { item ->
        OuDialog(
            title = stringResource(R.string.dlg_exists_title),
            message = stringResource(R.string.dlg_exists_message, item.name),
            confirmText = stringResource(R.string.dlg_continue_overwrite),
            dismissText = stringResource(R.string.cancel),
            onConfirm = {
                pendingDownload = null
                vm.download(item, overwrite = true)
                showDownloads = true
            },
            onDismiss = { pendingDownload = null },
        ) {
            if (item.network == NetworkStatus.RED) {
                Text(
                    stringResource(R.string.dlg_net_red_override),
                    style = OuTheme.typography.small,
                    color = OuTheme.colors.accentColor3,
                )
            }
        }
    }

    // 上次崩溃日志（用于自诊断）
    crashText?.let { log ->
        OuDialog(
            title = stringResource(R.string.crash_title),
            confirmText = stringResource(R.string.crash_clear),
            dismissText = stringResource(R.string.close),
            onConfirm = {
                com.utaumarket.mobile.core.CrashReporter.clear(ctx)
                crashText = null
            },
            onDismiss = { crashText = null },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    log,
                    style = OuTheme.typography.mono,
                    color = OuTheme.colors.foreground,
                )
            }
        }
    }

    // 使用前须知（免责声明）：只在第一次启动时弹，必须点「同意」才能继续。
    // 支持简中 / 繁中 / English / 日本語，正文可滚动。
    // 条款改版后（版本号变了）也重新提示一次，否则改了等于没改
    // 下载须知弹窗
    pendingNotice?.let { item ->
        com.utaumarket.mobile.ui.screens.DownloadNoticeDialog(
            dontShowAgain = noticeDontShow,
            onDontShowAgainChange = { noticeDontShow = it },
            onConfirm = {
                if (noticeDontShow) vm.setDownloadNoticeHidden(true)
                pendingNotice = null
                noticeDontShow = false
                doStartDownload(item)
            },
            onDismiss = {
                pendingNotice = null
                noticeDontShow = false
            },
        )
    }

    if (!state.settings.disclaimerAccepted ||
        state.settings.disclaimerVersion < com.utaumarket.mobile.data.prefs.SettingsStore.DISCLAIMER_VERSION
    ) {
        com.utaumarket.mobile.ui.screens.DisclaimerDialog(
            onAccept = { vm.acceptDisclaimer() },
        )
    }

    // 【首次设置教程】放在免责声明之后：先确认条款，再教怎么配目录。
    // 只看一次，之后可从「设置 → 目录」重新打开（这里只负责首次弹出）。
    if (state.settings.disclaimerAccepted &&
        state.settings.disclaimerVersion >= com.utaumarket.mobile.data.prefs.SettingsStore.DISCLAIMER_VERSION &&
        !state.settings.tutorialSeen
    ) {
        com.utaumarket.mobile.ui.screens.TutorialDialog(
            onDismiss = { vm.dismissTutorial() },
        )
    }

    // 【宿主缺失提示】本 App 只是资源管理器，没有 OpenUtau Mobile 就没法用。
    // 每次启动探一次（用户可能刚卸载了宿主），没装就引导去 GitHub 下载。
    var hostChecked by remember { mutableStateOf(false) }
    var hostMissing by remember { mutableStateOf(false) }
    var hostDontShow by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hostChecked) {
            hostMissing = !com.utaumarket.mobile.data.HostAppDetector.isInstalled(ctx)
            hostChecked = true
        }
    }
    if (hostChecked && hostMissing && !hostDontShow) {
        OuDialog(
            title = stringResource(R.string.host_missing_title),
            message = stringResource(R.string.host_missing_message),
            confirmText = stringResource(R.string.host_missing_download),
            dismissText = stringResource(R.string.host_missing_later),
            onConfirm = {
                runCatching {
                    ctx.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(com.utaumarket.mobile.data.HostAppDetector.DOWNLOAD_URL),
                        )
                    )
                }
                hostDontShow = true
            },
            onDismiss = { hostDontShow = true },
        )
    }

    // 首次引导
    if (showOnboarding) {
        OuDialog(
            title = stringResource(R.string.onboard_title),
            message = stringResource(R.string.onboard_message),
            confirmText = stringResource(R.string.onboard_go_settings),
            dismissText = stringResource(R.string.onboard_later),
            onConfirm = {
                showOnboarding = false
                vm.setOnboardingDone(true)
                tab = 5
            },
            onDismiss = {
                showOnboarding = false
                vm.setOnboardingDone(true)
            },
        ) {
            Text(
                stringResource(R.string.onboard_warning),
                style = OuTheme.typography.small,
                color = OuTheme.colors.accentColor3,
            )
        }
    }
}

@Composable
private fun appSubtitle(tab: Int, state: UiState): String = when (tab) {
    0 -> stringResource(R.string.sub_plugins, state.plugins.size)
    1 -> if (state.phonemizerLoading) {
        stringResource(R.string.sub_phonemizers_loading)
    } else {
        stringResource(R.string.sub_phonemizers, state.phonemizers.size)
    }
    2 -> stringResource(R.string.sub_voicebanks, state.voicebanks.size)
    // 【tab 3 是「工程」页】之前这里错用了已安装的文案，
    // 结果工程页顶部显示「共 10 项已安装」，和页面内容完全对不上（用户反馈）。
    3 -> stringResource(R.string.sub_projects, state.projects.size)
    4 -> stringResource(
        R.string.sub_installed,
        state.installedVoicebanks.size + state.installedPlugins.size + state.installedPhonemizers.size,
    )
    5 -> stringResource(R.string.sub_forks, state.forks.size)
    else -> stringResource(R.string.sub_settings)
}
