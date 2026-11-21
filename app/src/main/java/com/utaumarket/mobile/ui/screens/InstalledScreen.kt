package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.utaumarket.mobile.R
import com.utaumarket.mobile.data.model.InstalledItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuCard
import com.utaumarket.mobile.ui.components.OuCheckbox
import com.utaumarket.mobile.ui.components.OuDialog
import com.utaumarket.mobile.ui.components.OuInfoRow
import com.utaumarket.mobile.ui.components.OuTabRow
import com.utaumarket.mobile.ui.components.OuTag
import com.utaumarket.mobile.ui.theme.OuIcons
import com.utaumarket.mobile.ui.components.OuIconButton
import com.utaumarket.mobile.ui.theme.OuTheme
import com.utaumarket.mobile.ui.vm.AppViewModel
import com.utaumarket.mobile.ui.vm.UiState
import com.utaumarket.mobile.ui.screens.isLandscape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@Composable
fun InstalledScreen(
    vm: AppViewModel,
    state: UiState,
    onPickDirectory: (ResourceKind) -> Unit,
    onGoSettings: () -> Unit,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    val ctx = LocalContext.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectionVersion by remember { mutableIntStateOf(0) }
    val selected = remember { mutableSetOf<String>() }
    var detail by remember { mutableStateOf<InstalledItem?>(null) }
    var pendingDelete by remember { mutableStateOf<List<InstalledItem>?>(null) }
    // 本地声库体检
    var healthScanning by remember { mutableStateOf(false) }
    var healthReports by remember { mutableStateOf<List<com.utaumarket.mobile.data.installed.VoicebankHealth.Report>?>(null) }
    val healthScope = rememberCoroutineScope()

    // 【分支改版特例】导航栏里没有它时，这一栏也不显示。
    // 其余三类（声库/插件/音素器）始终显示，不管导航有没有隐藏。
    val showForkTab = remember(state.settings.hiddenTabs) {
        !state.settings.hiddenTabs.split(",").map { it.trim() }.contains("forks")
    }
    val vbLabel = stringResource(R.string.kind_voicebank)
    val plLabel = stringResource(R.string.kind_plugin)
    val phLabel = stringResource(R.string.kind_phonemizer)
    // 【为什么不叫「分支改版」】官方版与 DLL 支持版共用同一个包名
    // pers.vocoder712.openutaumobile，系统内只能存在一个，无法从包信息区分来源。
    // 之前叫「分支改版」，结果官方版本体被列在里面，看起来像被"并进改版"了。
    val forkLabel = stringResource(R.string.installed_host_title)
    val tabs = if (showForkTab) listOf(vbLabel, plLabel, phLabel, forkLabel)
               else listOf(vbLabel, plLabel, phLabel)
    val kind = when (tab) {
        0 -> ResourceKind.VOICEBANK
        1 -> ResourceKind.PLUGIN
        else -> ResourceKind.PHONEMIZER
    }
    val list = when (tab) {
        0 -> state.installedVoicebanks
        1 -> state.installedPlugins
        2 -> state.installedPhonemizers
        else -> emptyList()
    }
    val treeUri = vm.settings.rootFor(kind)
    @Suppress("UNUSED_EXPRESSION") selectionVersion

    Column(Modifier.fillMaxSize()) {
        OuTabRow(tabs = tabs, selectedIndex = tab, onSelect = {
            tab = it; selected.clear(); selectionMode = false; selectionVersion++
        })

        // 只授权一个根目录就够了；子目录由程序自动识别。
        // 之前用 rootFor(kind)（= 单独授权的那一类）来判断，根目录已授权时它仍是空的，
        // 于是"明明授权了还让你再授权"。
        if (state.settings.rootTreeUri.isBlank()) {
            PathBanner(
                text = stringResource(R.string.installed_no_root),
                actionLabel = stringResource(R.string.action_authorize),
                onAction = { onGoSettings() },
            )
        } else {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = d.pagePadding, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    // 已安装数量 + 当前分类的总占用大小（声库通常几百 MB，用户很关心）
                    val totalBytes = list.sumOf { it.sizeBytes }
                    // 分支改版栏的数据来自 PackageManager，不在 list 里，计数要单独取
                    val countNow = if (showForkTab && tab == 3) state.installedForks.size else list.size
                    Text(
                        if (totalBytes > 0) {
                            stringResource(R.string.installed_count, countNow) +
                                "  ·  " + humanSize(totalBytes)
                        } else {
                            stringResource(R.string.installed_count, countNow)
                        },
                        style = OuTheme.typography.small,
                        color = c.foregroundDisabled,
                    )
                    val busy = when {
                        state.deletingCount > 0 -> stringResource(R.string.installed_deleting, state.deletingCount)
                        state.installedLoading -> stringResource(R.string.installed_refreshing)
                        else -> ""
                    }
                    if (busy.isNotEmpty()) {
                        Text(busy, style = OuTheme.typography.small, color = c.accent)
                    }
                }
                if (!selectionMode) {
                    OuButton(stringResource(R.string.installed_multi_select), onClick = { selectionMode = true }, variant = OuButtonVariant.Clear)
                    Spacer(Modifier.width(4.dp))
                    OuButton(
                        stringResource(R.string.action_refresh),
                        // 用户主动点刷新 → 绕过 30 秒限流，真扫一遍
                        onClick = { vm.refreshInstalled(force = true) },
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = OuIcons.refresh,
                    )
                } else {
                    OuButton(
                        if (selected.size == list.size && list.isNotEmpty()) {
                            stringResource(R.string.installed_unselect_all)
                        } else {
                            stringResource(R.string.installed_select_all)
                        },
                        onClick = {
                            if (selected.size == list.size) selected.clear()
                            else { selected.clear(); list.forEach { selected.add(it.uri) } }
                            selectionVersion++
                        },
                        variant = OuButtonVariant.Clear,
                    )
                    Spacer(Modifier.width(4.dp))
                    OuButton(
                        stringResource(R.string.installed_delete_n, selected.size),
                        onClick = { pendingDelete = list.filter { selected.contains(it.uri) } },
                        variant = OuButtonVariant.Clear,
                        enabled = selected.isNotEmpty(),
                        leadingIconRes = OuIcons.delete,
                    )
                    Spacer(Modifier.width(4.dp))
                    OuButton(
                        stringResource(R.string.installed_clean_residue),
                        onClick = { vm.cleanResidue(kind) },
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = OuIcons.delete,
                    )
                    Spacer(Modifier.width(4.dp))
                    OuButton(
                        stringResource(R.string.installed_health_check),
                        onClick = {
                            healthScanning = true
                            healthScope.launch {
                                val target = state.installedVoicebanks
                                val res = withContext(Dispatchers.IO) {
                                    com.utaumarket.mobile.data.installed.VoicebankHealth.check(ctx, target)
                                }
                                healthReports = res
                                healthScanning = false
                            }
                        },
                        variant = OuButtonVariant.Clear,
                        enabled = !healthScanning && state.installedVoicebanks.isNotEmpty(),
                        leadingIconRes = OuIcons.info,
                    )
                    Spacer(Modifier.width(4.dp))
                    OuButton(
                        stringResource(R.string.installed_exit_selection),
                        onClick = { selectionMode = false; selected.clear(); selectionVersion++ },
                        variant = OuButtonVariant.Clear,
                    )
                }
            }
        }

        // 刷新提示只在"统计行"里出现一次（见上面的 busy）。
        // 这里以前还有一份同样的文案，于是页面上会同时出现两个「正在刷新…」。

        when {
            // 列表为空且还在扫 → 才显示整行 loading；
            // 有缓存内容时继续显示列表，只在顶部提示"正在刷新"，避免每次进页都空一下
            state.installedLoading && list.isEmpty() -> LoadingRow(stringResource(R.string.installed_scanning))
            // 分支改版：装完是系统里的应用，不在声库目录，单独渲染
            showForkTab && tab == 3 -> ForkInstalledList(state.installedForks, ctx)
            list.isEmpty() && treeUri.isNotBlank() -> EmptyHint(stringResource(R.string.installed_empty, tabs[tab]))
            else -> ResourceList(
                items = list,
                columns = listColumns(),
                key = { it.uri },
            ) { item ->
                    val checked = selected.contains(item.uri)
                    com.utaumarket.mobile.ui.components.OuListRow(
                        selected = checked,
                        onClick = {
                            if (selectionMode) {
                                if (checked) selected.remove(item.uri) else selected.add(item.uri)
                                selectionVersion++
                            } else {
                                detail = item
                            }
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 【本地头像】声库目录里自带的 icon.png / icon.bmp，
                            // 和 UTAU / OpenUtau 读的是同一个文件，不联网。
                            if (kind == ResourceKind.VOICEBANK) {
                                com.utaumarket.mobile.ui.components.SingerIcon(
                                    uri = item.iconUri,
                                    size = 44.dp,
                                    fallbackText = item.name,
                                )
                                Spacer(Modifier.width(10.dp))
                            }
                            if (selectionMode) {
                                OuCheckbox(checked = checked, onCheckedChange = {
                                    if (it) selected.add(item.uri) else selected.remove(item.uri)
                                    selectionVersion++
                                })
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        item.name,
                                        style = OuTheme.typography.cardTitle,
                                        color = c.foreground,
                                        // 【两行】加了头像以后横向空间更紧张，
                                        // 单行时 "AdachiRei"、"Zetsuon..." 这种长名字会被截成一半。
                                        // 允许折两行，配合下面的按钮缩进，基本能完整显示。
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (item.version.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        OuTag(item.version)
                                    }
                                }
                                val sub = buildList {
                                    if (item.author.isNotBlank()) add(item.author)
                                    if (item.engine.isNotBlank()) add(item.engine)
                                    if (item.recordingType.isNotBlank()) add(item.recordingType)
                                }.joinToString(" · ")
                                if (sub.isNotBlank()) {
                                    Text(
                                        sub,
                                        style = OuTheme.typography.cardMeta,
                                        color = c.foreground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                // 文案必须在 composable 上下文里取好，再做位置参数拼接
                                val modifiedText = if (item.lastModified > 0) {
                                    dateFmt.format(Date(item.lastModified))
                                } else {
                                    stringResource(R.string.installed_date_unknown)
                                }
                                val statsText = stringResource(
                                    R.string.installed_file_stats,
                                    item.childCount,
                                    humanSize(item.sizeBytes),
                                    modifiedText,
                                )
                                Text(
                                    statsText,
                                    style = OuTheme.typography.small,
                                    color = c.foregroundDisabled,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            // 【按钮横排 + 垂直居中】
                            // 竖排会把卡片撑高一大截（三个 44dp 按钮叠起来），左边文字只有三行，
                            // 结果右边很高、左边很空 —— 观感很怪。
                            // 现在只保留「打开 / 删除」两个按钮横排，垂直居中；
                            // 「修复编码」这种低频操件挪到详情弹窗里。
                            if (!selectionMode) {
                                Spacer(Modifier.width(4.dp))
                                OuButton(
                                    if (isLandscape()) "" else stringResource(R.string.action_open),
                                    onClick = {
                                        vm.openFolder(item)?.let { intent ->
                                            runCatching { ctx.startActivity(intent) }
                                        }
                                    },
                                    variant = OuButtonVariant.Clear,
                                    leadingIconRes = OuIcons.folder,
                                )
                                Spacer(Modifier.width(2.dp))
                                OuButton(
                                    if (isLandscape()) "" else stringResource(R.string.action_delete),
                                    onClick = { pendingDelete = listOf(item) },
                                    variant = OuButtonVariant.Clear,
                                    leadingIconRes = OuIcons.delete,
                                )
                            }
                        }
                    }
                }
        }
    }

    val item = detail
    if (item != null) {
        OuDialog(
            title = item.name,
            confirmText = stringResource(R.string.close),
            dismissText = null,
            onConfirm = { detail = null },
            onDismiss = { detail = null },
        ) {
            Column(Modifier.fillMaxWidth()) {
                OuInfoRow(stringResource(R.string.field_category), tabs[tab])
                OuInfoRow(stringResource(R.string.field_author), item.author)
                OuInfoRow(stringResource(R.string.field_engine), item.engine)
                OuInfoRow(stringResource(R.string.field_recording_type), item.recordingType)
                OuInfoRow(stringResource(R.string.field_version), item.version)
                OuInfoRow(stringResource(R.string.installed_field_file_count), item.childCount.toString())
                OuInfoRow(stringResource(R.string.field_size), humanSize(item.sizeBytes))
                OuInfoRow(
                    stringResource(R.string.installed_field_modified),
                    if (item.lastModified > 0) dateFmt.format(Date(item.lastModified)) else "",
                )
                OuInfoRow(stringResource(R.string.field_install_location), item.uri)
                if (item.detailLines.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.installed_metadata_title), style = OuTheme.typography.small, color = c.foregroundDisabled)
                    item.detailLines.forEach { (k, v) -> OuInfoRow(k, v) }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    // 「修复编码」从列表行移到详情里 —— 低频操作，不占列表宽度
                    OuButton(
                        stringResource(R.string.installed_repair_encoding),
                        onClick = { detail = null; vm.repairEncoding(item) },
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = OuIcons.refresh,
                    )
                    Spacer(Modifier.width(6.dp))
                    // 能在清单里对上号的话，给一个直达官网的入口
                    val official = remember(item.uri) { vm.officialUrlFor(item) }
                    if (official.isNotBlank()) {
                        OuButton(
                            stringResource(R.string.action_open_official),
                            onClick = {
                                runCatching {
                                    ctx.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(official),
                                        )
                                    )
                                }
                            },
                            variant = OuButtonVariant.Clear,
                            leadingIconRes = OuIcons.globe,
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    OuButton(
                        stringResource(R.string.installed_open_folder),
                        onClick = {
                            vm.openFolder(item)?.let { intent -> runCatching { ctx.startActivity(intent) } }
                        },
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = OuIcons.folder,
                    )
                    Spacer(Modifier.width(6.dp))
                    OuButton(
                        stringResource(R.string.action_delete),
                        onClick = { detail = null; pendingDelete = listOf(item) },
                        variant = OuButtonVariant.Clear,
                        leadingIconRes = OuIcons.delete,
                    )
                }
            }
        }
    }

    val targets = pendingDelete
    if (targets != null) {
        OuDialog(
            title = stringResource(R.string.installed_delete_title),
            message = stringResource(
                R.string.installed_delete_message,
                targets.joinToString("\n") { "· ${it.name}" },
            ),
            confirmText = stringResource(R.string.action_delete),
            dismissText = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                pendingDelete = null
                selected.clear()
                selectionVersion++
                vm.deleteInstalled(kind, targets) { }
            },
            onDismiss = { pendingDelete = null },
        )
    }

    // 体检结果：列出有问题的声库与原因
    val reports = healthReports
    if (reports != null) {
        OuDialog(
            title = stringResource(R.string.installed_health_title),
            confirmText = stringResource(R.string.close),
            dismissText = null,
            onConfirm = { healthReports = null },
            onDismiss = { healthReports = null },
        ) {
            if (reports.isEmpty()) {
                Text(
                    stringResource(R.string.installed_health_ok),
                    style = OuTheme.typography.body,
                    color = c.foreground,
                )
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        stringResource(R.string.installed_health_summary, reports.size),
                        style = OuTheme.typography.small,
                        color = c.foregroundDisabled,
                    )
                    Spacer(Modifier.height(8.dp))
                    reports.forEach { rep ->
                        Text(
                            rep.item.name,
                            style = OuTheme.typography.body,
                            color = if (rep.severity >= 2) c.accentColor3 else c.foreground,
                        )
                        rep.problems.forEach { p ->
                            Text(
                                "· $p",
                                style = OuTheme.typography.small,
                                color = c.foregroundDisabled,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/**
 * 「已安装 → 分支改版」列表。
 *
 * 分支改版是 APK，安装后是系统里的一个应用，不在声库/插件目录，
 * 所以这一栏的数据来自 PackageManager（见 AppViewModel.scanInstalledForks）。
 */
@Composable
private fun ForkInstalledList(
    forks: List<com.utaumarket.mobile.data.model.InstalledFork>,
    ctx: android.content.Context,
) {
    val c = OuTheme.colors
    if (forks.isEmpty()) {
        EmptyHint(stringResource(R.string.fork_installed_empty))
        return
    }
    com.utaumarket.mobile.ui.screens.ResourceList(
        items = forks,
        columns = 1,
        key = { it.packageName },
    ) { f ->
        com.utaumarket.mobile.ui.components.OuListRow(
            selected = false,
            onClick = {},
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        f.label,
                        style = OuTheme.typography.cardTitle,
                        color = c.foreground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        f.packageName,
                        style = OuTheme.typography.cardMeta,
                        color = c.foregroundDisabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.fork_installed_version, f.versionName, f.versionCode),
                        style = OuTheme.typography.small,
                        color = c.foregroundDisabled,
                    )
                }
                OuButton(
                    stringResource(R.string.action_open),
                    onClick = {
                        runCatching {
                            val i = ctx.packageManager.getLaunchIntentForPackage(f.packageName)
                            if (i != null) ctx.startActivity(i)
                        }
                    },
                    variant = OuButtonVariant.Clear,
                    leadingIconRes = OuIcons.open,
                )
            }
        }
    }
}

