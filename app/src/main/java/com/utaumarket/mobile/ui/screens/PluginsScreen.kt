package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utaumarket.mobile.data.model.PluginCategory
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuChip
import com.utaumarket.mobile.ui.components.OuChipRow
import com.utaumarket.mobile.ui.components.OuFilterLabel
import com.utaumarket.mobile.ui.components.OuFilterPanel
import com.utaumarket.mobile.ui.components.OuTabRow
import com.utaumarket.mobile.ui.components.OuTextField
import com.utaumarket.mobile.ui.theme.OuIcons
import com.utaumarket.mobile.ui.theme.OuTheme
import com.utaumarket.mobile.ui.vm.AppViewModel
import com.utaumarket.mobile.ui.components.OuIconButton
import com.utaumarket.mobile.ui.vm.UiState

@Composable
fun PluginsScreen(
    vm: AppViewModel,
    state: UiState,
    onRequestDownload: (ResourceItem) -> Unit,
    onPickDirectory: (ResourceKind) -> Unit,
    /** true = 显示「分支/改版」条目（复用同一套列表逻辑） */
    forksOnly: Boolean = false,
) {
    val d = OuTheme.dimens
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var showFilter by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ResourceItem?>(null) }
    // 分支改版页展开的系列
    var forksSeries by remember { mutableStateOf<com.utaumarket.mobile.ui.screens.SeriesGroup?>(null) }

    // map 的 lambda 不是 @Composable，标签先 for 循环取好（否则插件分类 tab 会是中文）
    val categoryTabs = ArrayList<String>(PluginCategory.entries.size)
    for (c in PluginCategory.entries) categoryTabs.add(c.labelText())
    val tabs = listOf(stringResource(com.utaumarket.mobile.R.string.all)) + categoryTabs
    // 「仅资料」条目默认隐藏（可在设置里关掉）
    val source = (if (forksOnly) state.forks else state.plugins)
        .let { if (state.settings.hideReference) it.filter { x -> !x.isInfoOnly() } else it }
    var list = if (forksOnly || tab == 0) source
               else source.filter { it.pluginCategory == PluginCategory.entries[tab - 1] }
    if (query.isNotBlank()) {
        list = list.filter { it.name.contains(query, true) || it.nameTranslated.contains(query, true) || it.author.contains(query, true) }
    }
    list = when (sort) {
        1 -> list.sortedBy { it.name.lowercase() }
        // 默认：按 GitHub 星标降序（星标高的更可能是成熟好用的插件）；
        // 没有星标数据（非 GitHub 来源）的排在后面，内部再按名称排。
        2 -> list.sortedByDescending { it.stars }
        else -> list.sortedWith(
            compareByDescending<com.utaumarket.mobile.data.model.ResourceItem> { it.stars }
                .thenBy { it.name.lowercase() }
        )
    }

    // 分支改版：点「查看版本」后进入**整页系列详情**（和声库页一致），
    // 之前只是弹个小对话框，信息太少（用户反馈）。
    if (forksOnly && forksSeries != null) {
        com.utaumarket.mobile.ui.screens.SeriesScreen(
            vm = vm,
            state = state,
            group = forksSeries!!,
            onBack = { forksSeries = null },
            onRequestDownload = onRequestDownload,
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        if ((state.settings.rootTreeUri.isBlank() && state.settings.pluginTreeUri.isBlank())) {
            PathBanner(
                text = stringResource(com.utaumarket.mobile.R.string.no_plugin_dir),
                actionLabel = stringResource(com.utaumarket.mobile.R.string.authorize),
                onAction = { onPickDirectory(ResourceKind.PLUGIN) },
            )
        }
        OuTabRow(tabs = tabs, selectedIndex = tab, onSelect = { tab = it }, scrollable = tabs.size > 4)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = d.pagePadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OuTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(com.utaumarket.mobile.R.string.search_name_author),
                leadingRes = OuIcons.search,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            OuButton(
                stringResource(com.utaumarket.mobile.R.string.filter),
                onClick = { showFilter = true },
                variant = if (sort != 0) OuButtonVariant.Normal else OuButtonVariant.Clear,
                leadingIconRes = OuIcons.filter,
            )
        }

        if (list.isEmpty()) {
            EmptyHint(stringResource(com.utaumarket.mobile.R.string.empty_plugins))
        } else {
            // 分支改版页：同一个 series 的多个架构（arm64/arm32/x64）合成一张卡片，
            // 不再各占一行（用户反馈：DLL 支持版被拆成三张卡很乱）。
            if (forksOnly) {
                val groups = list.groupBySeries()
                ResourceList(
                    items = groups,
                    columns = listColumns(),
                    key = { it.key },
                    resetKey = listOf(tab, sort, query),
                ) { g ->
                    if (g.isSeries) {
                        SeriesCard(
                            group = g,
                            onOpen = { forksSeries = g },
                        )
                    } else {
                        ResourceCard(
                            item = g.head,
                            installed = vm.isInstalled(g.head),
                            onDownload = { onRequestDownload(g.head) },
                            onDetail = { detail = g.head },
                        )
                    }
                }
            } else {
                ResourceList(
                    items = list,
                    columns = listColumns(),
                    key = { it.id },
                    resetKey = listOf(tab, sort, query),
                ) { item ->
                    ResourceCard(
                        item = item,
                        installed = vm.isInstalled(item),
                        onDownload = { onRequestDownload(item) },
                        onDetail = { detail = item },
                    )
                }
            }
        }
    }

    if (showFilter) {
        OuFilterPanel(
            title = stringResource(com.utaumarket.mobile.R.string.filter_plugins),
            onDismiss = { showFilter = false },
            onReset = { sort = 0 },
        ) {
            OuFilterLabel(stringResource(com.utaumarket.mobile.R.string.sort))
            OuChipRow {
                OuChip(stringResource(com.utaumarket.mobile.R.string.sort_default), sort == 0) { sort = 0 }
                Spacer(Modifier.width(6.dp))
                OuChip(stringResource(com.utaumarket.mobile.R.string.sort_name), sort == 1) { sort = 1 }
                Spacer(Modifier.width(6.dp))
                OuChip("★ 星标", sort == 2) { sort = 2 }
            }
            OuFilterLabel(stringResource(com.utaumarket.mobile.R.string.note))
            androidx.compose.material3.Text(
                stringResource(com.utaumarket.mobile.R.string.warn_exe),
                style = OuTheme.typography.small,
                color = OuTheme.colors.foregroundDisabled,
            )
        }
    }

    detail?.let { d0 ->
        ResourceDetailDialog(
            item = d0,
            installed = vm.isInstalled(d0),
            installedAt = vm.installedAt(d0),
            onDismiss = { detail = null },
            onDownload = { detail = null; onRequestDownload(d0) },
        )
    }
}


/** 分支改版的版本选择对话框：列出各架构，各自可下载 */
@Composable
internal fun ForksVersionsDialog(
    group: com.utaumarket.mobile.ui.screens.SeriesGroup,
    onDismiss: () -> Unit,
    onPick: (ResourceItem) -> Unit,
) {
    val c = OuTheme.colors
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.background)
                .border(1.dp, c.border),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    group.head.series.ifBlank { group.head.name },
                    style = OuTheme.typography.body,
                    color = c.foreground,
                    modifier = Modifier.weight(1f),
                )
                OuIconButton(
                    painterResource(OuIcons.close),
                    null,
                    onDismiss,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            // 各架构
            group.versions.forEach { v ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            versionLabel(group, v),
                            style = OuTheme.typography.body,
                            color = c.foreground,
                        )
                        Text(
                            humanSize(v.fileSize),
                            style = OuTheme.typography.small,
                            color = c.foregroundDisabled,
                        )
                    }
                    OuButton(
                        stringResource(com.utaumarket.mobile.R.string.action_download),
                        onClick = { onPick(v) },
                        variant = OuButtonVariant.Accent,
                    )
                }
            }
        }
    }
}

