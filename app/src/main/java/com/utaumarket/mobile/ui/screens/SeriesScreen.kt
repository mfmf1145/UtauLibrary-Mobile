package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utaumarket.mobile.R
import com.utaumarket.mobile.data.model.NetworkStatus
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.net.NetworkProbe
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuCard
import com.utaumarket.mobile.ui.components.OuListRow
import com.utaumarket.mobile.ui.components.OuTag
import com.utaumarket.mobile.ui.theme.OuIcons
import com.utaumarket.mobile.ui.theme.OuTheme
import com.utaumarket.mobile.ui.vm.AppViewModel
import com.utaumarket.mobile.ui.vm.UiState

/**
 * 二级页面：一个声库系列的所有分支版本。
 *
 * 主列表里同一角色只出一条，点进来才展开各分支（単独音 / 連続音 / Lite / English …）。
 * 头部保留系列的作者、引擎、体积合计与网络状态，避免点进来后丢失上下文。
 */
@Composable
fun SeriesScreen(
    vm: AppViewModel,
    state: UiState,
    group: SeriesGroup,
    onBack: () -> Unit,
    onRequestDownload: (ResourceItem) -> Unit,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    var detail by remember { mutableStateOf<ResourceItem?>(null) }

    val probed by NetworkProbe.statuses.collectAsStateWithLifecycle()
    val net = probed[group.head.url.lowercase()]
        ?: NetworkProbe.statusFor(group.head.url)
        ?: group.network

    Column(Modifier.fillMaxSize()) {
        OuCard(modifier = Modifier.fillMaxWidth().padding(d.pagePadding)) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        group.head.series.ifBlank { group.head.name },
                        style = OuTheme.typography.title,
                        color = c.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    OuTag(stringResource(R.string.series_version_count, group.versions.size), accent = true)
                }
                Spacer(Modifier.height(3.dp))
                if (group.head.author.isNotBlank()) {
                    Text(
                        stringResource(R.string.series_author, group.head.author),
                        style = OuTheme.typography.small,
                        color = c.foregroundDisabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    seriesInfoLine(group, net),
                    style = OuTheme.typography.small,
                    // 这一行是「系列译名 · 引擎 · 语言 · 总体积」，属于资料信息，
                    // 不该因为某个版本连不上就整行变红（之前就是这么染的，很刺眼）。
                    color = c.foregroundDisabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.series_branch_help),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
                Spacer(Modifier.height(8.dp))
                OuButton(
                    stringResource(R.string.action_back_to_list),
                    onClick = onBack,
                    variant = OuButtonVariant.Clear,
                    leadingIconRes = OuIcons.back,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ResourceList(
            // 二级列表按体积从大到小排（用户要求）：大包通常内容更全，
            // 体积为 0（未知）的沉到最后。
            items = group.versions.sortedWith(
                // 先按手动指定的优先级（>0 的排前面，升序），其余按体积从大到小
                compareBy<com.utaumarket.mobile.data.model.ResourceItem> { if (it.sortOrder > 0) 0 else 1 }
                    .thenBy { it.sortOrder }
                    .thenByDescending { it.fileSize }
                    .thenBy { it.name.lowercase() }
            ),
            columns = if (isLandscape()) 2 else 1,
            key = { it.id },
        ) { item ->
            VersionRow(
                group = group,
                item = item,
                installed = vm.isInstalled(item),
                onDownload = { onRequestDownload(item) },
                onDetail = { detail = item },
            )
        }
    }

    detail?.let { item ->
        ResourceDetailDialog(
            item = item,
            installed = vm.isInstalled(item),
            installedAt = vm.installedAt(item),
            onDismiss = { detail = null },
            onDownload = { detail = null; onRequestDownload(item) },
        )
    }
}

/** 单个分支版本一行 */
@Composable
private fun VersionRow(
    group: SeriesGroup,
    item: ResourceItem,
    installed: Boolean,
    onDownload: () -> Unit,
    onDetail: () -> Unit,
) {
    val c = OuTheme.colors
    val probed by NetworkProbe.statuses.collectAsStateWithLifecycle()
    val net = probed[item.url.lowercase()] ?: NetworkProbe.statusFor(item.url) ?: item.network

    OuListRow(onClick = onDetail, accent = accentFor(item)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        versionLabel(group, item),
                        style = OuTheme.typography.cardTitle,
                        color = c.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (installed) {
                        Spacer(Modifier.width(6.dp))
                        OuTag(stringResource(R.string.tag_installed), accent = true)
                    }
                }
                Spacer(Modifier.height(3.dp))
                val engineText = item.engine.labelText()
                // 网盘条目显示「需网盘」而不是「未探测」—— 它本来就没有直链可探
                val platV = platformOf(item)
                // 平台条目用文字说明；普通条目用「圆点符号」而不是彩色 emoji
                // —— 圆点作为文本字符渲染，颜色由下面的 SpanStyle 控制，任何主题都协调。
                // 平台条目用文字说明；普通条目用圆点符号，颜色由 SpanStyle 控制
                val isPlat = platV != DownloadPlatform.NONE
                val netLabel = if (isPlat) {
                    stringResource(com.utaumarket.mobile.R.string.net_platform, platV.label)
                } else {
                    networkLabel(net)
                }
                val sizeText = humanSize(item.fileSize)
                val infoPart = buildList {
                    // 不再把整名字塞进副标题 —— 标题已经显示了，重复很难看
                    add(engineText)
                    item.recordingType.takeIf { it.isNotBlank() }?.let { add(it) }
                    sizeText.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString(" · ")
                // 【行内状态单独上色】整行变红会把体积、录音方式也染红；
                // 全都不上色又看不到警告。这里用 AnnotatedString，只给状态那一段上色。
                val rowStatusColor = networkTextColor(net, isPlat)
                val sub = androidx.compose.ui.text.buildAnnotatedString {
                    append(infoPart)
                    if (netLabel.isNotBlank()) {
                        if (infoPart.isNotBlank()) append(" · ")
                        if (isPlat) {
                            // 网盘条目：和主列表一致，前面放一个蓝色地球标记
                            withStyle(androidx.compose.ui.text.SpanStyle(color = c.accent)) {
                                append("🌐 ")
                            }
                        } else {
                            if (LocalNetIndicatorStyle.current == "emoji") {
                                append(net.emoji + " ")
                            } else {
                                // 【尺寸对齐】行内圆点是文字符号，默认按正文字号渲染，
                                // 在主列表 10dp 的实心圆旁边显得很小。单独放大这一段的字号
                                // （● 的直径约为字号的 0.6 倍，15sp ≈ 9~10dp），颜色仍由圆点色控制。
                                withStyle(
                                    androidx.compose.ui.text.SpanStyle(
                                        color = networkDotColor(net),
                                        fontSize = 15.sp,
                                    ),
                                ) {
                                    append("\u25CF ")
                                }
                            }
                        }
                        withStyle(androidx.compose.ui.text.SpanStyle(color = rowStatusColor)) {
                            append(netLabel)
                        }
                    }
                }
                // sub 把「引擎 · 语言 · 体积 · 状态」拼成一行，整行统一用默认色。
                // 只有卡片上方那行独立的状态文字才会在红状态下变红。
                Text(
                    sub,
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            // 网盘条目没有直链，按钮换成「跳转网盘」
            val nd = netdiskUrl(item)
            if (item.referenceOnly && nd.isNotBlank()) {
                val nctx = androidx.compose.ui.platform.LocalContext.current
                // 提取码显示在按钮左边（和主卡片一致）
                if (item.netdiskPwd.isNotBlank()) {
                    Text(
                        stringResource(com.utaumarket.mobile.R.string.pwd_prefix, item.netdiskPwd),
                        style = OuTheme.typography.small,
                        color = c.foregroundDisabled,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                OuButton(
                    platformOf(item).buttonLabel(
                        stringResource(com.utaumarket.mobile.R.string.link_netdisk)
                    ).ifBlank {
                        stringResource(com.utaumarket.mobile.R.string.link_netdisk)
                    },
                    onClick = {
                        if (item.netdiskPwd.isNotBlank()) {
                            runCatching {
                                val cm = nctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as? android.content.ClipboardManager
                                cm?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("pwd", item.netdiskPwd)
                                )
                            }
                        }
                        runCatching {
                            nctx.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(nd),
                                )
                            )
                        }
                    },
                    variant = OuButtonVariant.Accent,
                    leadingIconRes = OuIcons.link,
                )
            } else if (item.referenceOnly || item.url.isBlank()) {
                // 既没直链也没网盘：只显示「仅资料」标签，不给点了没反应的下载按钮
                OuTag(
                    stringResource(R.string.tag_reference_only),
                    tone = c.foregroundDisabled,
                )
            } else {
                OuButton(
                    stringResource(R.string.action_download),
                    onClick = onDownload,
                    variant = OuButtonVariant.Accent,
                    leadingIconRes = OuIcons.download,
                )
            }
        }
    }
}
