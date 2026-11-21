package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.utaumarket.mobile.data.model.PhonemizerLanguage
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.data.model.NetworkStatus
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
import com.utaumarket.mobile.ui.vm.UiState

@Composable
fun PhonemizersScreen(
    vm: AppViewModel,
    state: UiState,
    onRequestDownload: (ResourceItem) -> Unit,
    onPickDirectory: (ResourceKind) -> Unit,
) {
    val d = OuTheme.dimens
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var detail by remember { mutableStateOf<ResourceItem?>(null) }
    // 筛选（之前音素器页完全没有筛选，只有搜索）
    var showFilter by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var net by rememberSaveable { mutableStateOf("all") }

    // 【修正】原来 tabs 直接就是 [stringResource(com.utaumarket.mobile.R.string.lang_ja),stringResource(com.utaumarket.mobile.R.string.lang_zh),stringResource(com.utaumarket.mobile.R.string.lang_en),stringResource(com.utaumarket.mobile.R.string.lang_other)]，而过滤写成 tab == 0 表示stringResource(com.utaumarket.mobile.R.string.all) ——
    // 可 tab 0 本身就是「日语」。结果选日语时显示全部、其他类别全空。
    // 现在显式加一个「全部」标签，语言标签顺延。
    // map 的 lambda 不是 @Composable，标签先 for 循环取好
    val languageTabs = ArrayList<String>(PhonemizerLanguage.entries.size)
    for (l in PhonemizerLanguage.entries) languageTabs.add(l.labelText())
    val tabs = listOf(stringResource(com.utaumarket.mobile.R.string.all)) + languageTabs
    // 「仅资料」条目默认隐藏（可在设置里关掉）
    var filtered = if (state.settings.hideReference) {
        state.phonemizers.filter { x -> !x.isInfoOnly() }
    } else {
        state.phonemizers
    }
        .filter { tab == 0 || it.phonemizerLanguage == PhonemizerLanguage.entries[tab - 1] }
        .filter { query.isBlank() || it.name.contains(query, true) || it.description.contains(query, true) }
        .filter { net == "all" || it.network.id == net }
        .filter { net.isNotBlank() }
    filtered = when (sort) {
        1 -> filtered.sortedBy { it.name.lowercase() }
        2 -> filtered.sortedByDescending { it.fileSize }
        else -> filtered.sortedWith(
            compareByDescending<ResourceItem> { it.stars }.thenBy { it.name.lowercase() },
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (vm.settings.rootFor(ResourceKind.PHONEMIZER).isBlank()) {
            PathBanner(
                text = stringResource(com.utaumarket.mobile.R.string.ph_shared_dir),
                actionLabel = stringResource(com.utaumarket.mobile.R.string.authorize),
                onAction = { onPickDirectory(ResourceKind.PHONEMIZER) },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = d.pagePadding, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OuTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(com.utaumarket.mobile.R.string.ph_filter_hint),
                leadingRes = OuIcons.search,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            // 筛选入口：和插件页一致（排序 + 网络状态）
            OuButton(
                stringResource(com.utaumarket.mobile.R.string.filter),
                onClick = { showFilter = true },
                variant = if (sort == 0 && net == "all") OuButtonVariant.Clear else OuButtonVariant.Normal,
                leadingIconRes = OuIcons.filter,
            )
            Spacer(Modifier.width(6.dp))
            OuButton(
                if (state.phonemizerLoading) stringResource(com.utaumarket.mobile.R.string.searching) else stringResource(com.utaumarket.mobile.R.string.search_action),
                onClick = { vm.loadPhonemizers(force = true) },
                variant = OuButtonVariant.Normal,
                leadingIconRes = OuIcons.refresh,
                enabled = !state.phonemizerLoading,
            )
        }
        OuTabRow(tabs = tabs, selectedIndex = tab, onSelect = { tab = it })

        if (showFilter) {
            OuFilterPanel(
                title = stringResource(com.utaumarket.mobile.R.string.filter_phonemizers),
                onDismiss = { showFilter = false },
                onReset = { sort = 0; net = "all" },
            ) {
                OuFilterLabel(stringResource(com.utaumarket.mobile.R.string.sort))
                OuChipRow {
                    OuChip("★ 星标", sort == 0) { sort = 0 }
                    Spacer(Modifier.width(6.dp))
                    OuChip(stringResource(com.utaumarket.mobile.R.string.sort_name), sort == 1) { sort = 1 }
                    Spacer(Modifier.width(6.dp))
                    OuChip(stringResource(com.utaumarket.mobile.R.string.field_size), sort == 2) { sort = 2 }
                }
                OuFilterLabel(stringResource(com.utaumarket.mobile.R.string.vb_net_title))
                OuChipRow {
                    OuChip(stringResource(com.utaumarket.mobile.R.string.all), net == "all") { net = "all" }
                    listOf("green", "yellow", "red").forEach { n ->
                        Spacer(Modifier.width(6.dp))
                        OuChip(NetworkStatus.fromId(n).emoji, net == n) { net = n }
                    }
                }
            }
        }

        when {
            state.phonemizerLoading -> LoadingRow(stringResource(com.utaumarket.mobile.R.string.ph_searching_api))
            state.phonemizerError.isNotBlank() -> InfoBox(
                stringResource(com.utaumarket.mobile.R.string.ph_api_failed, state.phonemizerError) +
                    stringResource(com.utaumarket.mobile.R.string.ph_github_tip)
            )
            filtered.isEmpty() -> EmptyHint(
                stringResource(com.utaumarket.mobile.R.string.ph_empty) +
                    stringResource(com.utaumarket.mobile.R.string.ph_dll_tip)
            )
            // 用统一的多列列表：横屏下 3 列，否则单列全宽会留一大片空白
            else -> ResourceList(
                items = filtered,
                columns = listColumns(),
                key = { it.id },
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
