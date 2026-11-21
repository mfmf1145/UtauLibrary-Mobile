package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.utaumarket.mobile.R
import com.utaumarket.mobile.data.model.LanguageTag
import com.utaumarket.mobile.data.model.NetworkStatus
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.data.model.VoicebankFamily
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuChip
import com.utaumarket.mobile.ui.components.OuChipRow
import com.utaumarket.mobile.ui.components.OuFilterLabel
import com.utaumarket.mobile.ui.components.OuFilterPanel
import com.utaumarket.mobile.ui.components.OuTabRow
import com.utaumarket.mobile.ui.components.OuTextField
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import com.utaumarket.mobile.ui.theme.OuIcons
import com.utaumarket.mobile.ui.theme.OuTheme
import com.utaumarket.mobile.ui.vm.AppViewModel
import com.utaumarket.mobile.ui.vm.UiState

@Composable
fun VoicebanksScreen(
    vm: AppViewModel,
    state: UiState,
    onRequestDownload: (ResourceItem) -> Unit,
    onPickDirectory: (ResourceKind) -> Unit,
    onOpenSeries: (SeriesGroup) -> Unit = {},
    /** 外部（工程页）请求按歌手名搜索：非空时自动填入搜索框并清空 */
    searchRequest: String = "",
    onSearchRequestHandled: () -> Unit = {},
) {
    val d = OuTheme.dimens
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var lang by rememberSaveable { mutableStateOf("all") }
    var rec by rememberSaveable { mutableStateOf("all") }       // 录音方式
    var src by rememberSaveable { mutableStateOf("all") }       // 来源
    var net by rememberSaveable { mutableStateOf("all") }       // 网络状态
    var sizeBand by rememberSaveable { mutableIntStateOf(0) }   // 0 全部 1 小 2 中 3 大
    var onlyMissing by rememberSaveable { mutableStateOf(false) }
    // 初始值跟随「设置 → 外观与交互 → 默认隐藏仅资料条目」，用户仍可在筛选面板临时改
    // 只认设置里那一个开关，筛选面板直接写回设置 —— 之前这里另存了一个局部变量，
    // 两边会打架：设置里改了，筛选面板还是旧值，得改两遍才一致（用户反馈）。
    val hideReference = state.settings.hideReference
    var sort by rememberSaveable { mutableIntStateOf(0) }
    // 出身筛选：all / jp / cn / other
    var origin by rememberSaveable { mutableStateOf("all") }    // 出身
    var query by rememberSaveable { mutableStateOf("") }

    // 工程页传来的「去声库找 XXX」：填进搜索框，并把筛选重置到"全部"，
    // 否则用户带着上一次的筛选条件会搜不到东西。
    LaunchedEffect(searchRequest) {
        if (searchRequest.isNotBlank()) {
            query = searchRequest
            lang = "all"; rec = "all"; src = "all"; net = "all"
            origin = "all"; sizeBand = 0; onlyMissing = false; vm.settings.setHideReference(false)
            onSearchRequestHandled()
        }
    }
    var showFilter by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ResourceItem?>(null) }

    val families = VoicebankFamily.entries
    // 分页标签与筛选片文案必须先在 composable 作用域里取好：
    // map / forEach / listOf 的 lambda 不是 composable，里面调不了 stringResource
    val familyLabels = ArrayList<String>(families.size)
    for (f in families) familyLabels.add(f.labelText())
    val langChips = listOf(
        "ja" to LanguageTag.JA.labelText(),
        "zh" to LanguageTag.ZH.labelText(),
        "en" to LanguageTag.EN.labelText(),
        "ko" to LanguageTag.KO.labelText(),
        "multi" to LanguageTag.MULTI.labelText(),
        "other" to LanguageTag.OTHER.labelText(),
    )
    // 【性能】整条「过滤 + 排序」管线包进 remember。
    // 之前是每帧重算（991 条 × 11 个筛选项），滚动/输入时明显掉帧。
    val list = androidx.compose.runtime.remember(
        state.voicebanks, tab, lang, rec, src, net, sizeBand, onlyMissing, hideReference, origin, query, sort,
        // 【刷新】「已安装」角标与「仅显示未安装」筛选都依赖已安装列表；
        // 之前这里没有它，删掉声库后列表不会重算，要切页才更新。
        state.installedVoicebanks, state.deletingCount,
    ) {
    var list = state.voicebanks.filter { it.family == families[tab] }
    if (lang != "all") {
        list = list.filter { it.language.split(',', '/').any { l -> l.trim().equals(lang, true) } }
    }
    if (rec != "all") {
        list = list.filter { it.recordingType.equals(rec, true) }
    }
    if (src != "all") {
        list = list.filter {
            val p = it.sourcePlatform.lowercase()
            when (src) {
                "github" -> p.contains("github")
                "official" -> p.contains("官网") || p.contains("official")
                else -> !p.contains("github") && !p.contains("官网") && !p.contains("official")
            }
        }
    }
    if (net != "all") {
        list = list.filter {
            val st = com.utaumarket.mobile.data.net.NetworkProbe.statusFor(it.url) ?: it.network
            st.id == net
        }
    }
    if (sizeBand != 0) {
        list = list.filter {
            when (sizeBand) {
                1 -> it.fileSize in 1..(20L * 1024 * 1024)
                2 -> it.fileSize > 20L * 1024 * 1024 && it.fileSize <= 100L * 1024 * 1024
                else -> it.fileSize > 100L * 1024 * 1024
            }
        }
    }
    if (onlyMissing) list = list.filter { !vm.isInstalled(it) }
    // 隐藏「仅资料」：只保留有任何下载入口的条目
    if (hideReference) list = list.filter { !it.isInfoOnly() }
    // 搜索支持别名：teto / kasane-teto 都能命中「重音テト」
    if (origin != "all") list = list.filter { it.origin == origin }
    if (query.isNotBlank()) list = list.filter { it.matches(query) }

    list = when (sort) {
        1 -> list.sortedBy { it.name.lowercase() }
        2 -> list.sortedByDescending { it.fileSize }
        3 -> list.sortedByDescending { it.releaseDate }
        // 4 = 仅「可下载优先」（不含热度）
        4 -> list.sortedWith(
            compareByDescending<com.utaumarket.mobile.data.model.ResourceItem> { it.url.isNotBlank() }
                .thenByDescending { rankNetwork(it) }
                .thenBy { it.name.lowercase() }
        )
        // 【默认】先按「可获取程度」分三档，同档内再按综合热度：
        //   2 = 有直链（能直接下）
        //   1 = 有网盘/平台入口（能跳过去手动下）
        //   0 = 仅资料（没任何下载途径）
        // 这样能下的永远在最前面，仅资料的沉底，档内热度顺序不变。
        // 注意：默认排序**不看清单顺序**，要调某个声库的先后，改它的 heat 值。
        //   2 = 有直链（能直接下）
        //   1 = 有网盘/平台入口（能跳过去手动下）
        //   0 = 仅资料（没任何下载途径）
        else -> list.sortedWith(
            compareByDescending<com.utaumarket.mobile.data.model.ResourceItem> { availabilityRank(it) }
                // 排序偏好（用户要求）：
                //   · 日文 / 中文 稍微靠前
                //   · UTAU Wiki 上那些冷门英文条目录靠后
                // 用「有效热度」加权而不是硬分档，这样真正知名的欧美条目仍能排在前面。
                .thenByDescending { effectiveHeat(it) }
                .thenByDescending { it.heat }
                .thenByDescending { rankNetwork(it) }
                .thenBy { it.name.lowercase() }
        )
    }
    list        // ← remember 的返回值（必须有，否则 lambda 返回 Unit）
    }

    val activeFilterCount = listOf(
        lang != "all", rec != "all", src != "all", net != "all", origin != "all",
        sizeBand != 0, onlyMissing, sort != 0,
    ).count { it }

    Column(Modifier.fillMaxSize()) {
        if ((state.settings.rootTreeUri.isBlank() && state.settings.voicebankTreeUri.isBlank())) {
            PathBanner(
                text = stringResource(R.string.vb_no_dir),
                actionLabel = stringResource(R.string.action_authorize),
                onAction = { onPickDirectory(ResourceKind.VOICEBANK) },
            )
        }
        if (isLandscape()) {
            // 横屏：引擎标签与「搜索 + 筛选」并到同一行，省下的约 100px 正好多显示一行卡片
            Row(
                Modifier.fillMaxWidth().padding(horizontal = d.pagePadding, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    OuTabRow(
                        tabs = familyLabels,
                        selectedIndex = tab,
                        onSelect = { tab = it },
                    )
                }
                Spacer(Modifier.width(10.dp))
                OuTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.vb_search_alias),
                    leadingRes = OuIcons.search,
                    modifier = Modifier.width(230.dp),
                )
                Spacer(Modifier.width(6.dp))
                OuButton(
                    if (activeFilterCount > 0) stringResource(R.string.vb_filter_count, activeFilterCount) else stringResource(R.string.filter),
                    onClick = { showFilter = true },
                    variant = if (activeFilterCount > 0) OuButtonVariant.Normal else OuButtonVariant.Clear,
                    leadingIconRes = OuIcons.filter,
                )
            }
        } else {
            OuTabRow(tabs = familyLabels, selectedIndex = tab, onSelect = { tab = it })
            Row(
                Modifier.fillMaxWidth().padding(horizontal = d.pagePadding, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OuTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.vb_search_name_alias_author),
                    leadingRes = OuIcons.search,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                OuButton(
                    if (activeFilterCount > 0) stringResource(R.string.vb_filter_count, activeFilterCount) else stringResource(R.string.filter),
                    onClick = { showFilter = true },
                    variant = if (activeFilterCount > 0) OuButtonVariant.Normal else OuButtonVariant.Clear,
                    leadingIconRes = OuIcons.filter,
                )
            }
        }

        if (list.isEmpty()) {
            EmptyHint(stringResource(R.string.vb_empty))
        } else {
            // 同一角色的多个分支并成一条，点进去看二级版本页
            // 把「固定位置」的系列插到指定下标上（1 起）。
            // 用户要求：足立レイ 保持原位（第 3），九十九紫苑、茶運めぐり 紧跟其后。
            val groups = remember(list) {
                val raw = list.groupBySeries()
                val pinned = raw.filter { it.pin > 0 }.sortedBy { it.pin }
                if (pinned.isEmpty()) return@remember raw
                val out = raw.filter { it.pin == 0 }.toMutableList()
                for (p in pinned) {
                    val idx = (p.pin - 1).coerceIn(0, out.size)
                    out.add(idx, p)
                }
                out.toList()
            }
            // 「仅资料」被默认隐藏时给出明确提示，避免用户以为筛选丢了条目
            ResourceList(
                items = groups,
                columns = listColumns(),
                key = { it.key },
                // 换排序 / 改筛选 / 换搜索词都回到顶部，否则用户以为没生效
                resetKey = listOf(sort, query, lang, rec, src, net, sizeBand, onlyMissing, hideReference, tab, origin),
            ) { g ->
                if (g.isSeries) {
                    SeriesCard(group = g, onOpen = { onOpenSeries(g) })
                } else {
                    ResourceCard(
                        item = g.head,
                        installed = vm.isInstalled(g.head),
                        onDownload = { onRequestDownload(g.head) },
                        onDetail = { detail = g.head },
                    )
                }
            }
        }
    }

    if (showFilter) {
        OuFilterPanel(
            title = stringResource(R.string.vb_filter_title),
            onDismiss = { showFilter = false },
            onReset = {
                lang = "all"; rec = "all"; src = "all"; net = "all"
                sizeBand = 0; onlyMissing = false; vm.settings.setHideReference(false); sort = 0; origin = "all"
            },
        ) {
            OuFilterLabel(stringResource(R.string.field_language))
            // 出身（按 wiki 的三分法：日本 / 中国 / 其他国家）
            OuChipRow {
                OuChip(stringResource(R.string.all), origin == "all") { origin = "all" }
                OuChip(stringResource(R.string.origin_jp), origin == "jp") { origin = "jp" }
                OuChip(stringResource(R.string.origin_cn), origin == "cn") { origin = "cn" }
                OuChip(stringResource(R.string.origin_other), origin == "other") { origin = "other" }
            }
            OuChipRow {
                OuChip(stringResource(R.string.all), lang == "all") { lang = "all" }
                langChips.forEach { (id, label) ->
                    Spacer(Modifier.width(6.dp))
                    OuChip(label, lang == id) { lang = id }
                }
            }
            OuFilterLabel(stringResource(R.string.field_recording_type))
            OuChipRow {
                OuChip(stringResource(R.string.all), rec == "all") { rec = "all" }
                listOf("CV", "VCV", "CVVC", "CVC").forEach { r ->
                    Spacer(Modifier.width(6.dp))
                    OuChip(r, rec == r) { rec = r }
                }
            }
            OuFilterLabel(stringResource(R.string.field_source))
            OuChipRow {
                OuChip(stringResource(R.string.all), src == "all") { src = "all" }
                Spacer(Modifier.width(6.dp))
                OuChip("GitHub", src == "github") { src = "github" }
                Spacer(Modifier.width(6.dp))
                OuChip(stringResource(R.string.vb_src_official), src == "official") { src = "official" }
                Spacer(Modifier.width(6.dp))
                OuChip(stringResource(R.string.vb_src_other), src == "other") { src = "other" }
            }
            OuFilterLabel(stringResource(R.string.vb_net_title))
            OuChipRow {
                OuChip(stringResource(R.string.all), net == "all") { net = "all" }
                listOf("green", "yellow", "red").forEach { n ->
                    Spacer(Modifier.width(6.dp))
                    OuChip(NetworkStatus.fromId(n).emoji, net == n) { net = n }
                }
            }
            OuFilterLabel(stringResource(R.string.field_size))
            OuChipRow {
                OuChip(stringResource(R.string.all), sizeBand == 0) { sizeBand = 0 }
                Spacer(Modifier.width(6.dp))
                OuChip("< 20 MB", sizeBand == 1) { sizeBand = 1 }
                Spacer(Modifier.width(6.dp))
                OuChip("20–100 MB", sizeBand == 2) { sizeBand = 2 }
                Spacer(Modifier.width(6.dp))
                OuChip("> 100 MB", sizeBand == 3) { sizeBand = 3 }
            }
            OuFilterLabel(stringResource(R.string.vb_install_status))
            OuChipRow {
                OuChip(stringResource(R.string.all), !onlyMissing) { onlyMissing = false }
                Spacer(Modifier.width(6.dp))
                OuChip(stringResource(R.string.vb_only_missing), onlyMissing) { onlyMissing = true }
            }
            OuFilterLabel(stringResource(R.string.vb_reference_title))
            OuChipRow {
                OuChip(stringResource(R.string.vb_show), !hideReference) { vm.settings.setHideReference(false) }
                Spacer(Modifier.width(6.dp))
                OuChip(stringResource(R.string.vb_hide), hideReference) { vm.settings.setHideReference(true) }
            }
            OuFilterLabel(stringResource(R.string.sort))
            OuChipRow {
                OuChip(stringResource(R.string.sort_default), sort == 0) { sort = 0 }
                Spacer(Modifier.width(6.dp))
                OuChip(stringResource(R.string.sort_name), sort == 1) { sort = 1 }
                Spacer(Modifier.width(6.dp))
                OuChip(stringResource(R.string.vb_sort_size_desc), sort == 2) { sort = 2 }
                Spacer(Modifier.width(6.dp))
                OuChip(stringResource(R.string.vb_sort_newest), sort == 3) { sort = 3 }
                Spacer(Modifier.width(8.dp))
                OuChip(stringResource(R.string.sort_downloadable), sort == 4) { sort = 4 }
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


/**
 * 排序用的「有效热度」。
 *
 * 用户要求：日文/中文的往前一些，UTAU Wiki 上那些冷门英文条目往后一些。
 * 这里用**加权**而不是硬分档 —— 真正知名的欧美声库（热度很高）依然能排在前面，
 * 只有"又冷门又是英文"的才会被明显压后。
 */
private fun effectiveHeat(item: com.utaumarket.mobile.data.model.ResourceItem): Float {
    val base = item.heat
    // 日 / 中 加成
    val originBoost = when (item.origin) {
        "jp", "cn" -> 12f
        else -> 0f
    }
    // 来自 UTAU Wiki（Fandom）的英文条目：冷门降权
    val wikiPenalty =
        if (item.origin == "other" && item.detailUrl.contains("fandom.com")) -15f else 0f
    return base + originBoost + wikiPenalty
}

/**
 * 「可获取程度」排序权重：
 * 2 = 有直链可直接下载；1 = 有网盘/平台入口可跳转；0 = 仅资料。
 */
private fun availabilityRank(item: com.utaumarket.mobile.data.model.ResourceItem): Int = when {
    item.url.isNotBlank() -> 2
    item.netdiskUrl.isNotBlank() -> 1
    com.utaumarket.mobile.ui.screens.platformOf(item) != com.utaumarket.mobile.ui.screens.DownloadPlatform.NONE -> 1
    else -> 0
}

/** 网络状态的排序权重：绿 > 黄 > 红 > 未知 */
private fun rankNetwork(item: com.utaumarket.mobile.data.model.ResourceItem): Int =
    when (com.utaumarket.mobile.data.net.NetworkProbe.statusFor(item.url) ?: item.network) {
        com.utaumarket.mobile.data.model.NetworkStatus.GREEN -> 3
        com.utaumarket.mobile.data.model.NetworkStatus.YELLOW -> 2
        com.utaumarket.mobile.data.model.NetworkStatus.RED -> 1
        else -> 0
    }
