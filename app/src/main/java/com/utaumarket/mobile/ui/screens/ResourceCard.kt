package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utaumarket.mobile.data.net.NetworkProbe
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.utaumarket.mobile.R
import com.utaumarket.mobile.data.model.NetworkStatus
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuInfoRow
import com.utaumarket.mobile.ui.components.OuListRow
import com.utaumarket.mobile.ui.components.OuTag
import com.utaumarket.mobile.ui.theme.OuIcons
import com.utaumarket.mobile.ui.theme.OuTheme

fun humanSize(bytes: Long): String {
    // 未知大小（0）返回空串，调用处据此不显示，避免出现 "0 B" 这种噪音
    if (bytes <= 0L) return ""
    if (bytes <= 0) return ""
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", mb)
    return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0)
}

/** 第二行：分类 / 语言 / 格式 / 体积 / 版本 / 网络状态，一行放完。 */
@Composable
private fun infoLine(
    item: ResourceItem,
    net: NetworkStatus,
    prefix: String? = null,
    sizeBytes: Long = item.fileSize,
): String {
    // 文案必须在 composable 上下文里取好，buildList 的 lambda 不是 composable
    // 配布在国内网盘的「仅资料」条目：显示「需网盘」而不是「未探测」—— 它本来就没直链可探
    val plat = platformOf(item)
    val netText = if (plat != DownloadPlatform.NONE) {
        // 「到 BowlRoll 下载」「需网盘」这类明确说明，比"未探测"有用得多
        "🌐 " + stringResource(com.utaumarket.mobile.R.string.net_platform, plat.label)
    } else {
        networkLabel(net)
    }
    val viaProxy = stringResource(R.string.card_via_proxy)
    val engineText = item.engine.labelText()
    val pluginCategoryText = item.pluginCategory.labelText()
    val phonemizerLangText = item.phonemizerLanguage.labelText()
    val languageText = displayLanguage(item.language)
    // 拿不到大小就**直接不显示**这一段（用户反馈显示"—"像漏渲染）。
    // 后台会用 HEAD 请求把真实大小补回来。
    val sizeText = humanSize(sizeBytes)
    val useProxy = net == NetworkStatus.RED &&
        com.utaumarket.mobile.download.UrlCandidates.isGitHub(item.url)
    return buildList {
        prefix?.takeIf { it.isNotBlank() }?.let { add(it) }
        when (item.kind) {
            ResourceKind.VOICEBANK -> {
                add(engineText)
                languageText.takeIf { it.isNotBlank() }?.let { add(it) }
                item.recordingType.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            ResourceKind.PLUGIN -> {
                add(pluginCategoryText)
                item.format.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                if (item.stars > 0) add("\u2605 " + item.stars)
            }
            ResourceKind.PHONEMIZER -> {
                add(phonemizerLangText)
                add("DLL")
                if (item.stars > 0) add("★ ${item.stars}")
            }
        }
        sizeText.takeIf { it.isNotBlank() }?.let { add(it) }
        item.version.takeIf { it.isNotBlank() }?.let { add("v" + it.removePrefix("v")) }
        // GitHub 直连基本不通，但我们内置了加速线路 —— 说清楚，别让人以为下不了
        add(if (useProxy) netText + viaProxy else netText)
    }.joinToString(" · ")
}

/** 左侧色条取色：全部使用 OpenUtau 自带的 AccentColor1/2/3 与 NeutralAccent。 */
@Composable
internal fun accentFor(item: ResourceItem): androidx.compose.ui.graphics.Color {
    val c = OuTheme.colors
    return when (item.kind) {
        ResourceKind.VOICEBANK -> when (item.family) {
            com.utaumarket.mobile.data.model.VoicebankFamily.UTAU -> c.accentColor1
            com.utaumarket.mobile.data.model.VoicebankFamily.DIFFSINGER -> c.accentColor2
            else -> c.neutralAccent
        }
        ResourceKind.PLUGIN -> when (item.pluginCategory) {
            com.utaumarket.mobile.data.model.PluginCategory.LYRICS -> c.accentColor1
            com.utaumarket.mobile.data.model.PluginCategory.PITCH -> c.accentColor2
            com.utaumarket.mobile.data.model.PluginCategory.MARKER -> c.accentColor3
            com.utaumarket.mobile.data.model.PluginCategory.SOUND -> c.accentLight1
            else -> c.neutralAccent
        }
        ResourceKind.PHONEMIZER -> when (item.phonemizerLanguage) {
            com.utaumarket.mobile.data.model.PhonemizerLanguage.JA -> c.accentColor1
            com.utaumarket.mobile.data.model.PhonemizerLanguage.ZH -> c.accentColor3
            com.utaumarket.mobile.data.model.PhonemizerLanguage.EN -> c.accentColor2
            else -> c.neutralAccent
        }
    }
}

/**
 * 资源列表行：密集两行 + 左侧分类色条 + 右侧扁平「下载」。
 * 不是卡片 —— 靠 1px 分隔线区隔，和 OpenUtau 的 ListBox 一致。
 */
@Composable
fun ResourceCard(
    item: ResourceItem,
    installed: Boolean,
    onDownload: () -> Unit,
    onDetail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OuTheme.colors
    // 只有界面语言是中文时才显示中文译名 —— 用英文/日文界面时，
    // 名字旁边再挂一个中文名会显得杂乱（用户要求）。
    // 标题始终是原文（不加括号）；译名放第二行的小字里（用户明确要求）
    // 【筛选后不要把「系列 + 版本」当成另一个歌手】
    // 有 series 的条目，标题统一用**系列名**，版本名放到旁边的标签里。
    // 例如搜索 english 时，卡片会显示成「重音テト [English]」，
    // 而不是「重音テト English」—— 后者看起来像另收录了一个歌手（用户反馈）。
    val title = item.series.ifBlank { item.name }
    val versionTag = if (item.series.isNotBlank() && item.name.startsWith(item.series)) {
        item.name.removePrefix(item.series).trim()
    } else ""
    val translated = item
        .localizedName(com.utaumarket.mobile.core.AppLocale.effectiveLanguage)
        .takeIf { it.isNotBlank() }
    // 用实测到的网络状态；没测过就退回清单里的标记
    val probed by NetworkProbe.statuses.collectAsStateWithLifecycle()
    val net = probed[item.url.lowercase()]
        ?: NetworkProbe.statusFor(item.url)
        ?: item.network
    // 清单里没有 file_size 的条目（插件/音素器）用探测时拿到的 Content-Length 补上
    val probedSizes by NetworkProbe.sizes.collectAsStateWithLifecycle()
    // 大小以实测为准：清单里的 file_size 会过期（用户反馈"大小对不上"）
    val sizeBytes = probedSizes[item.url.lowercase()]
        ?: NetworkProbe.sizeFor(item.url).takeIf { it > 0 }
        ?: item.fileSize
    // 还拿不到就让探测顺手补一次（GitHub 走加速线路，只取 1 字节拿 Content-Range）
    androidx.compose.runtime.LaunchedEffect(item.url) {
        if (sizeBytes <= 0) NetworkProbe.requestSize(item.url)
    }

    OuListRow(
        modifier = modifier.then(
            // 横屏 3 列时，卡片内容长短不一会导致高度参差，网格看起来很乱。
            // 给一个统一的最小高度，让每一排都对齐。
            if (isLandscape()) Modifier.heightIn(min = 86.dp) else Modifier,
        ),
        onClick = onDetail,
        accent = accentFor(item),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = OuTheme.typography.cardTitle,
                        color = c.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 版本名做标签：重音テト [English]
                    if (versionTag.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        OuTag(versionTag)
                    }
                    if (installed) {
                        Spacer(Modifier.width(6.dp))
                        OuTag(stringResource(R.string.tag_installed), accent = true)
                    }
                    if (item.mobileWarning.isNotBlank()) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            painterResource(OuIcons.warning),
                            contentDescription = stringResource(R.string.cd_mobile_warning),
                            tint = c.accentColor3,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                CardInfoRow(item, net, translated, sizeBytes)
                // 官网和萌娘百科，有哪个显示哪个；两个都没有就一个入口都不给
                CardLinks(
                    item.detailUrl,
                    if (item.kind == ResourceKind.VOICEBANK) moegirlUrl(item).ifBlank { null } else null,
                    null,
                    item.xUrl.ifBlank { null },
                    item.videoUrl.ifBlank { null },
                )
            }
            Spacer(Modifier.width(if (isLandscape()) 6.dp else 10.dp))
            if (item.referenceOnly) {
                // 资料条目：如果配布在国内网盘上，就把「下载」换成「跳转网盘」，
                // 否则只给一个「仅资料」标签（点了没反应比没有按钮更糟）。
                val nd = netdiskUrl(item)
                val nctx = androidx.compose.ui.platform.LocalContext.current
                if (nd.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 有提取码就一并复制到剪贴板，省得用户抄
                        if (item.netdiskPwd.isNotBlank()) {
                            Text(
                                stringResource(com.utaumarket.mobile.R.string.pwd_prefix, item.netdiskPwd),
                                style = OuTheme.typography.small,
                                color = c.foregroundDisabled,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        OuButton(
                            // 按钮直接用平台名：BowlRoll / BOOTH / MEGA / 百度网盘…
                            platformOf(item).buttonLabel(
                                stringResource(com.utaumarket.mobile.R.string.link_netdisk)
                            ).ifBlank {
                                stringResource(com.utaumarket.mobile.R.string.link_netdisk)
                            },
                            onClick = {
                                // 提取码先放剪贴板，再跳浏览器
                                if (item.netdiskPwd.isNotBlank()) {
                                    runCatching {
                                        val cm = nctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                            as? android.content.ClipboardManager
                                        cm?.setPrimaryClip(
                                            android.content.ClipData.newPlainText(
                                                "pwd", item.netdiskPwd,
                                            )
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
                    }
                } else {
                    OuTag(stringResource(R.string.tag_reference_only), tone = c.foregroundDisabled)
                }
            } else {
                // 「使用规定」：蓝字 + 下划线，明显是可跳转链接（放在下载按钮左边）
                if (item.termsUrl.isNotBlank()) {
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    Text(
                        stringResource(com.utaumarket.mobile.R.string.terms_of_use),
                        style = OuTheme.typography.small,
                        color = c.accent,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        maxLines = 1,
                        modifier = Modifier
                            .clickable {
                                runCatching {
                                    ctx.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(item.termsUrl),
                                        )
                                    )
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                // 横屏是 3 列、每列很窄，按钮只留图标，把宽度让给文字
                OuButton(
                    if (isLandscape()) "" else stringResource(R.string.action_download),
                    onClick = onDownload,
                    variant = OuButtonVariant.Accent,
                    leadingIconRes = OuIcons.download,
                )
            }
        }
    }
}

/**
 * 系列卡片：同一角色的多个分支只占一行，点进去看二级版本页。
 * 这样重音テト 11 个分支不会把整个列表淹掉。
 */
@Composable
fun SeriesCard(
    group: SeriesGroup,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OuTheme.colors
    val probed by NetworkProbe.statuses.collectAsStateWithLifecycle()
    val net = probed[group.head.url.lowercase()]
        ?: NetworkProbe.statusFor(group.head.url)
        ?: group.network
    OuListRow(
        modifier = modifier.then(if (isLandscape()) Modifier.heightIn(min = 86.dp) else Modifier),
        onClick = onOpen,
        accent = accentFor(group.head),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 系列卡片标题保持原名；多版本的正确写法在第二行（series_zh）
                    Text(
                        group.head.series.ifBlank { group.head.name },
                        style = OuTheme.typography.cardTitle,
                        color = c.foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    OuTag(stringResource(R.string.series_version_count, group.versions.size), accent = true)
                }
                Spacer(Modifier.height(3.dp))
                // 状态单独一行 + 细节一行，都从卡片左边缘开始（见 SeriesInfoBlock 注释）
                SeriesInfoBlock(group, net)
                // 从整个系列里找链接（不是只看 head，head 可能恰好没填）
                CardLinks(
                    group.detailUrl,
                    if (group.head.kind == ResourceKind.VOICEBANK) group.moegirlUrl.ifBlank { null } else null,
                    null,
                    group.xUrl.ifBlank { null },
                    group.videoUrl.ifBlank { null },
                )
            }
            Spacer(Modifier.width(6.dp))
            // 「使用规定」：系列里任一版本有就显示
            val gTerms = group.versions.firstOrNull { it.termsUrl.isNotBlank() }?.termsUrl
            if (!gTerms.isNullOrBlank()) {
                val gctx = androidx.compose.ui.platform.LocalContext.current
                Text(
                    stringResource(com.utaumarket.mobile.R.string.terms_of_use),
                    style = OuTheme.typography.small,
                    color = c.accent,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    maxLines = 1,
                    modifier = Modifier
                        .clickable {
                            runCatching {
                                gctx.startActivity(
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(gTerms),
                                    )
                                )
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.width(if (isLandscape()) 6.dp else 10.dp))
            // 「查看版本」：实心强调色块，和之前一样（用户明确要求改回来）
            OuButton(
                if (isLandscape()) "" else stringResource(R.string.series_view_versions),
                onClick = onOpen,
                variant = OuButtonVariant.Accent,
                leadingIconRes = OuIcons.list,
            )
        }
    }
}

/**
 * 卡片底部的低调文字链：官网 / 萌娘百科。
 *
 * 之前用的是两个图标按钮挤在下载按钮旁边 —— 又突兀、又没有文字提示、
 * 还把卡片挤得很紧；而且系列卡片当初根本没加，所以像重音テト这种多版本的
 * 卡片上一个跳转入口都没有。改成一行文字链后三个问题一起解决。
 */
/**
 * 入口链接的「类型」——决定卡片上显示什么文字。
 *
 * 用户反馈：不要所有都写「官网」。GitHub 仓库、UTAU Wiki 页面、
 * B站/YouTube 配布视频、作者介绍页性质完全不同，应该分别标注。
 */
enum class LinkKind(val label: String) {
    GITHUB("GitHub"),
    BILI("配布视频"),
    YOUTUBE("配布视频"),
    NICO("配布视频"),
    UTAU_WIKI("UTAU Wiki"),
    FANDOM("Fandom"),
    MOEGIRL("萌娘百科"),
    VCPEDIA("VCPedia"),
    BOOTH("BOOTH"),
    KOFI("Ko-fi"),
    BOWLROLL("BowlRoll"),
    TWITTER("个人账号"),
    DISCORD("Discord"),
    ONEDRIVE("OneDrive"),
    SITE("官网"),
    INTRO("介绍页"),
    OTHER("链接"),
    NONE(""),
}

/** 根据 URL 判断入口类型 */
internal fun linkKindOf(url: String): LinkKind {
    val u = url.lowercase()
    if (u.isBlank()) return LinkKind.NONE
    return when {
        u.contains("github.com") || u.contains("gitee.com") -> LinkKind.GITHUB
        u.contains("bilibili.com") || u.contains("b23.tv") -> LinkKind.BILI
        u.contains("youtube.com") || u.contains("youtu.be") -> LinkKind.YOUTUBE
        u.contains("nicovideo.jp") || u.contains("nico.ms") -> LinkKind.NICO
        u.contains("moegirl") -> LinkKind.MOEGIRL
        u.contains("vcpedia") -> LinkKind.VCPEDIA
        u.contains("fandom.com") || u.contains("huijiwiki") -> LinkKind.UTAU_WIKI
        u.contains("booth.pm") -> LinkKind.BOOTH
        u.contains("ko-fi.com") -> LinkKind.KOFI
        u.contains("bowlroll") -> LinkKind.BOWLROLL
        u.contains("twitter.com") || u.contains("x.com/") -> LinkKind.TWITTER
        u.contains("discord.") -> LinkKind.DISCORD
        u.contains("1drv.ms") || u.contains("onedrive") -> LinkKind.ONEDRIVE
        else -> LinkKind.SITE
    }
}

/** 萌娘百科是资料页，不是官网 —— 不能拿它冒充「官网」入口 */
internal fun String.isOfficialSite(): Boolean =
    isNotBlank() && !contains("moegirl.org.cn", ignoreCase = true)

@Composable
internal fun CardLinks(
    detailUrl: String,
    moegirl: String?,
    searchFor: String? = null,
    xUrl: String? = null,
    /** 配布视频（B站/YouTube/Nico）；有就单独显示一个入口 */
    videoUrl: String? = null,
) {
    val c = OuTheme.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun open(url: String) {
        runCatching {
            ctx.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            )
        }
    }
    val official = detailUrl.takeIf { it.isOfficialSite() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        if (official != null) {
            // 按链接类型显示不同文字：GitHub / UTAU Wiki / 配布视频 / 官网 …
            // 之前一律写「官网」，把 wiki 和代码仓库都叫成官网，误导用户。
            val kind = linkKindOf(official)
            Text(
                if (kind == LinkKind.SITE) stringResource(R.string.link_official) else kind.label,
                style = OuTheme.typography.small,
                color = c.accent,
                modifier = Modifier.clickable { open(official) },
            )
            // 分隔点统一由下面「下一个链接」自己补，这里不要再加，
            // 否则会出现「官网 · · 萌娘百科」——中间多出一个空档。
        }
        // 说明：这里以前有个「搜官网」兜底。用户明确要求
        // "没有萌百页面、又没有官网的就不要显示能跳转了"，所以去掉了。
        //
        // 只有歌手才给萌娘百科，而且必须是**确认过条目页**的（moegirlUrl 为空就不显示）；
        // 之前无条件加，点过去只会是一个"没有找到"的页面
        if (moegirl != null) {
            if (official != null) Text("  ·  ", style = OuTheme.typography.small, color = c.foregroundDisabled)
            Text(
                stringResource(R.string.link_moegirl),
                style = OuTheme.typography.small,
                color = c.accent,
                modifier = Modifier.clickable { open(moegirl) },
            )
        }
        // 配布视频：有就单独显示，点击直接跳视频
        if (!videoUrl.isNullOrBlank() && videoUrl != official) {
            Text("  ·  ", style = OuTheme.typography.small, color = c.foregroundDisabled)
            Text(
                linkKindOf(videoUrl).label.ifBlank { stringResource(R.string.link_video) },
                style = OuTheme.typography.small,
                color = c.accent,
                modifier = Modifier.clickable { open(videoUrl) },
            )
        }
        // 个别条目有作者/角色的 X 主页，样式与「官网」一致
        if (!xUrl.isNullOrBlank()) {
            Text("  ·  ", style = OuTheme.typography.small, color = c.foregroundDisabled)
            Text(
                stringResource(R.string.link_x),
                style = OuTheme.typography.small,
                color = c.accent,
                modifier = Modifier.clickable { open(xUrl) },
            )
        }
    }
}

/** 萌娘百科链接：用搜索式链接，不必为每个声库维护词条名，零维护成本。 */
/**
 * 条目最终要跳去的「下载平台」。
 *
 * 不同平台性质不同，界面要分别显示「到 XXX 下载」并把按钮换成平台名：
 * - BowlRoll / BOOTH / MEGA / getuploader / axfc —— 需要账号或客户端，不能直链
 * - 百度 / 夸克 / 微云 / 蓝奏 / 123 / 阿里 —— 国内网盘
 */
enum class DownloadPlatform(val label: String, val host: String) {
    BOWLROLL("BowlRoll", "bowlroll.net"),
    BOOTH("BOOTH", "booth.pm"),
    KOFI("Ko-fi", "ko-fi.com"),
    MEGA("MEGA", "mega.nz"),
    GETUPLOADER("getuploader", "getuploader.com"),
    AXFC("axfc", "axfc.net"),
    BAIDU("百度网盘", "pan.baidu.com"),
    QUARK("夸克网盘", "quark.cn"),
    WEIYUN("腾讯微云", "weiyun.com"),
    LANZOU("蓝奏云", "lanzou"),
    P123("123 网盘", "123"),
    ALIYUN("阿里云盘", "aliyun"),
    NONE("", ""),
    ;

    companion object {
        fun of(url: String): DownloadPlatform {
            val u = url.lowercase()
            if (u.isBlank()) return NONE
            return entries.firstOrNull { it != NONE && u.contains(it.host) } ?: NONE
        }
    }
}

/** 国内网盘统一显示短标签「网盘」；BowlRoll/BOOTH/MEGA 这类才显示平台名 */
internal fun DownloadPlatform.buttonLabel(fallback: String): String = when (this) {
    DownloadPlatform.NONE -> fallback
    DownloadPlatform.BAIDU, DownloadPlatform.QUARK, DownloadPlatform.WEIYUN,
    DownloadPlatform.LANZOU, DownloadPlatform.P123, DownloadPlatform.ALIYUN -> ""
    else -> label
}

/** 条目要跳去的平台（优先显式 netdisk_url，其次备注/官网里出现的平台链接） */
internal fun platformOf(item: com.utaumarket.mobile.data.model.ResourceItem): DownloadPlatform {
    DownloadPlatform.of(item.netdiskUrl).takeIf { it != DownloadPlatform.NONE }?.let { return it }
    for (src in listOf(item.remark, item.detailUrl, item.url)) {
        val p = DownloadPlatform.of(src)
        if (p != DownloadPlatform.NONE) return p
    }
    return DownloadPlatform.NONE
}

/** 识别条目里携带的网盘链接（百度/夸克/阿里/123/蓝奏/微云/115/MEGA/Drive） */
// 只认**国内**网盘：这些没有可程序化下载的直链，只能让用户跳过去手动下。
// MediaFire / Dropbox / Google Drive 属于"国外有直链"，由 LinkResolver 处理，不算网盘。
private val NETDISK = Regex(
    "(https?://[^\\s\"'<>]*?(?:pan\\.baidu\\.com|yun\\.baidu\\.com|quark\\.cn|aliyundrive\\.com|alipan\\.com" +
    "|123pan\\.com|123684\\.com|lanzou[a-z]*\\.com|share\\.weiyun\\.com|115\\.com|pan\\.xunlei\\.com" +
    "|bowlroll\\.net|booth\\.pm|mega\\.nz|getuploader\\.com|axfc\\.net)[^\\s\"'<>]*)",
    RegexOption.IGNORE_CASE,
)

/**
 * 条目里能拿到的网盘地址；没有就返回空串。
 * 「仅资料」条目如果配布在网盘上，至少让用户能一键跳过去。
 */
internal fun netdiskUrl(item: com.utaumarket.mobile.data.model.ResourceItem): String {
    // 清单里显式标注的优先（那是抓取时真正确认过的）
    item.netdiskUrl.takeIf { it.isNotBlank() }?.let { return it }
    for (src in listOf(item.url, item.detailUrl, item.remark)) {
        if (src.isBlank()) continue
        NETDISK.find(src)?.let { return it.value }
    }
    return ""
}

/** 当前界面语言是否为中文（简体或繁体）。用于决定要不要显示中文译名。 */
internal fun isChineseLocale(): Boolean = com.utaumarket.mobile.core.AppLocale.isChinese()

/**
 * 萌娘百科条目页地址；**没有确认过就返回空串**（界面据此不显示入口）。
 *
 * 之前会退化成搜索页，用户点进去看到的是搜索结果而不是条目 —— 既然没有条目，
 * 那就干脆不给这个入口（用户的要求）。
 */
internal fun moegirlUrl(item: ResourceItem): String {
    item.moegirlUrl.takeIf { it.isNotBlank() }?.let { return it }
    if (item.detailUrl.contains("moegirl.org.cn", ignoreCase = true)) return item.detailUrl
    return ""
}

/** 详情里的"萌娘百科"按钮 */
@Composable
internal fun MoeGirlButton(item: ResourceItem) {
    val octx = androidx.compose.ui.platform.LocalContext.current
    OuButton(
        stringResource(R.string.link_moegirl),
        onClick = {
            runCatching {
                octx.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(moegirlUrl(item)),
                    )
                )
            }
        },
        variant = OuButtonVariant.Clear,
        leadingIconRes = OuIcons.document,
    )
}

/** 系列卡片第二行：引擎 / 语言 / 合计体积 / 网络状态 */
@Composable
internal fun seriesInfoLine(group: SeriesGroup, net: NetworkStatus): String {
    val netText = networkLabel(net)
    val totalText = if (group.totalSize > 0) {
        stringResource(R.string.series_total_size, humanSize(group.totalSize))
    } else {
        ""
    }
    // map / joinToString 的 lambda 不是 @Composable，先 for 循环取好引擎标签
    val engineLabels = LinkedHashMap<Any, String>()
    for (v in group.versions) {
        if (!engineLabels.containsKey(v.engine)) engineLabels[v.engine] = v.engine.labelText()
    }
    val engines = engineLabels.values.joinToString(" / ")
    val langs = group.versions.map { it.language }.distinct()
        .mapNotNull { displayLanguage(it).takeIf { s -> s.isNotBlank() } }
    // 第二行首项：中文界面用「系列译名」（不是第一个版本的完整名字）
    val lead = if (isChineseLocale()) group.head.seriesZh else ""
    // 注意：网络状态**不再拼进这一行**，它单独占一行（见 SeriesInfoBlock）。
    // 否则窄屏上「国内可能无法访问」会被劈成「国内无」/「法访问」两行。
    return buildList {
        if (lead.isNotBlank()) add(lead)
        add(engines)
        if (langs.isNotEmpty()) add(langs.joinToString("/"))
        totalText.takeIf { it.isNotBlank() }?.let { add(it) }
    }.joinToString(" · ")
}

/** 系列卡片的说明区：状态独占一行，细节另起一行（都从卡片左边缘开始） */
@Composable
private fun SeriesInfoBlock(group: SeriesGroup, net: NetworkStatus) {
    val c = OuTheme.colors
    val plat = platformOf(group.head)
    val hasPlat = plat != DownloadPlatform.NONE
    val statusText = if (hasPlat) {
        stringResource(com.utaumarket.mobile.R.string.net_platform, plat.label)
    } else {
        networkLabel(net)
    }
    // 【只按当前显示的那条状态判断】
    // 之前这里还多算了一项 "系列里任意版本有网盘就算有平台"，
    // 结果「足立レイ」这种 7 个版本里混了一个网盘版本的系列，
    // 整组状态被强制按"有平台"处理 —— 红色警告被吞掉了（用户反馈）。
    // 状态行显示的是 net 的文字，颜色就该由 net 决定。
    val statusColor = networkTextColor(net, hasPlat)
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasPlat) {
                Icon(
                    painterResource(OuIcons.globe),
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(11.dp),
                )
            } else {
                NetworkIndicator(net)
            }
            Spacer(Modifier.width(5.dp))
            Text(
                statusText,
                style = OuTheme.typography.small,
                color = statusColor,
                maxLines = 1,
                softWrap = false,
            )
        }
        val rest = seriesInfoLine(group, net)
        if (rest.isNotBlank()) {
            Text(
                rest,
                style = OuTheme.typography.small,
                color = c.foregroundDisabled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 版本在系列内的短标签：「重音テト 単独音」→「単独音」。
 *
 * 但如果去掉前缀后只剩「标准版 / 非官方版」这种**没有信息量**的词，
 * 就退回完整名字 —— 否则用户会看到一排"标准版"，根本分不清是哪个
 * （用户反馈：「足立零这种不要只显示标准版」）。
 */
@Composable
internal fun versionLabel(group: SeriesGroup, item: ResourceItem): String {
    val s = group.head.series
    val short = if (s.isNotBlank() && item.name.startsWith(s)) item.name.removePrefix(s).trim() else item.name
    val generic = short.isBlank() ||
        short.length <= 3 ||
        GenericVersionWords.any { short.contains(it) }
    return if (generic) item.name.ifBlank { stringResource(R.string.version_standard) } else short
}

/** 这些词单独出现没有区分度 */
private val GenericVersionWords = listOf("标准版", "非官方版", "网盘版", "官方版")

/** 详情：列表里省略的字段全部在这里，缺字段显示 —，布局保持整齐。 */
@Composable
fun ResourceDetailDialog(
    item: ResourceItem,
    installed: Boolean,
    installedAt: String = "",
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    val c = OuTheme.colors
    val closeText = stringResource(R.string.close)
    com.utaumarket.mobile.ui.components.OuDialog(
        title = item.name,
        confirmText = if (item.referenceOnly) closeText else stringResource(R.string.action_download),
        dismissText = if (item.referenceOnly) null else closeText,
        onConfirm = { if (item.referenceOnly) onDismiss() else onDownload() },
        onDismiss = onDismiss,
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (installed) {
                OuTag(stringResource(R.string.detail_installed_repeat), accent = true)
                Spacer(Modifier.height(6.dp))
            }
            OuInfoRow(stringResource(R.string.field_name), item.name)
            if (item.nameTranslated.isNotBlank() && isChineseLocale()) {
                OuInfoRow(stringResource(R.string.field_name_translated), item.nameTranslated)
            }
            when (item.kind) {
                ResourceKind.VOICEBANK -> {
                    OuInfoRow(stringResource(R.string.field_engine), item.engine.labelText())
                    OuInfoRow(stringResource(R.string.field_category), item.family.labelText())
                    OuInfoRow(stringResource(R.string.field_language), displayLanguage(item.language))
                    OuInfoRow(stringResource(R.string.field_recording_type), item.recordingType)
                    OuInfoRow(stringResource(R.string.field_range), item.range)
                    OuInfoRow(stringResource(R.string.field_scale_count), item.scaleCount)
                }
                ResourceKind.PLUGIN -> OuInfoRow(stringResource(R.string.field_category), item.pluginCategory.labelText())
                ResourceKind.PHONEMIZER -> {
                    OuInfoRow(stringResource(R.string.field_language), item.phonemizerLanguage.labelText())
                    OuInfoRow("Stars", if (item.stars > 0) item.stars.toString() else "")
                }
            }
            OuInfoRow(stringResource(R.string.field_author), item.author)
            OuInfoRow(stringResource(R.string.field_format), item.format.uppercase())
            // 清单缺 file_size 时显示破折号，别让整行凭空消失（看起来像 UI bug）
            if (item.fileSize > 0) OuInfoRow(stringResource(R.string.field_size), humanSize(item.fileSize))
            OuInfoRow(stringResource(R.string.field_version), item.version)
            OuInfoRow(stringResource(R.string.field_release_date), item.releaseDate)
            OuInfoRow(stringResource(R.string.field_source), item.sourcePlatform)
            OuInfoRow(stringResource(R.string.field_license), item.license)
            OuInfoRow(
                stringResource(R.string.field_network),
                if (item.referenceOnly && netdiskUrl(item).isNotBlank())
                    "🌐 " + stringResource(com.utaumarket.mobile.R.string.net_netdisk)
                else
                    networkLabel(NetworkProbe.statusFor(item.url) ?: item.network),
            )
            // 自动扫描出来的安装路径，让用户知道这个歌手装在哪
            if (installed && installedAt.isNotBlank()) {
                OuInfoRow(stringResource(R.string.field_install_location), installedAt)
            }
            OuInfoRow(stringResource(R.string.field_direct_link), item.url)
            if (item.mirrorUrl.isNotBlank()) {
                OuInfoRow(stringResource(R.string.field_mirror), item.mirrorUrl)
            }
            // 萌娘百科地址只当"资料"，不冒充"官网/详情页"
            if (item.detailUrl.isOfficialSite()) {
                OuInfoRow(stringResource(R.string.field_detail_page), item.detailUrl)
                Spacer(Modifier.height(8.dp))
                val ctx = androidx.compose.ui.platform.LocalContext.current
                OuButton(
                    stringResource(R.string.action_open_official),
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(item.detailUrl),
                                )
                            )
                        }
                    },
                    variant = OuButtonVariant.Clear,
                    leadingIconRes = OuIcons.globe,
                )
                if (item.kind == ResourceKind.VOICEBANK && moegirlUrl(item).isNotBlank()) {
                    Spacer(Modifier.width(6.dp))
                    MoeGirlButton(item)
                }
            }
            if (item.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.field_description), style = OuTheme.typography.small, color = c.foregroundDisabled)
                Text(item.description, style = OuTheme.typography.body, color = c.foreground)
            }
            if (item.remark.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(item.remark, style = OuTheme.typography.small, color = c.foregroundDisabled)
            }
            if (item.mobileWarning.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(item.mobileWarning, style = OuTheme.typography.small, color = c.accentColor3)
            }
            if (item.network == NetworkStatus.RED) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.detail_net_red_warning), style = OuTheme.typography.small, color = c.accentColor3)
            }
        }
    }
}






/**
 * 卡片的说明行。
 *
 * 【为什么拆成 Row 而不是一个 Text】
 * 之前整行是一个 Text，内容形如「UTAU · 日语/中文 · 43.5 MB · 🔴 国内可能无法访问」。
 * 中文可以任意断行，窄屏上「国内可能无法访问」会被劈成「国内无」/「法访问」两行，
 * 观感非常糟。现在把**网络状态放最前面并禁止换行**，其余信息跟在后面换行/省略。
 */
@Composable
private fun CardInfoRow(
    item: ResourceItem,
    net: NetworkStatus,
    translated: String?,
    sizeBytes: Long,
) {
    val c = OuTheme.colors
    val plat = platformOf(item)
    val hasPlat = plat != DownloadPlatform.NONE
    val statusText = if (hasPlat) {
        stringResource(com.utaumarket.mobile.R.string.net_platform, plat.label)
    } else {
        networkLabel(net)
    }
    val statusColor = networkTextColor(net, hasPlat)
    // 【排版】拆成两行，各自从卡片左边缘起排：
    //   第一行：网络/平台状态（短，绝不换行）
    //   第二行：引擎 · 语言 · 类型 · 体积 · 版本
    // 之前是 Row + weight 把状态和其余塞在同一行，换行时第二行从「状态右边」开始，
    // 看起来缩进错位、很怪（用户反馈）。
    Column(Modifier.fillMaxWidth()) {
        // 圆点用画出来的实心圆，不再用彩色 emoji（字体由系统决定，跨主题不协调）
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasPlat) {
                Icon(
                    painterResource(OuIcons.globe),
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(11.dp),
                )
            } else {
                NetworkIndicator(net)
            }
            Spacer(Modifier.width(5.dp))
            Text(
                statusText,
                style = OuTheme.typography.small,
                color = statusColor,
                maxLines = 1,
                softWrap = false,
            )
        }
        val rest = infoLineRest(item, translated, sizeBytes)
        if (rest.isNotBlank()) {
            Text(
                rest,
                style = OuTheme.typography.small,
                color = c.foregroundDisabled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** infoLine 去掉网络状态后的部分（引擎 / 语言 / 体积 / 版本 …） */
@Composable
private fun infoLineRest(
    item: ResourceItem,
    prefix: String? = null,
    sizeBytes: Long = item.fileSize,
): String {
    val engineText = item.engine.labelText()
    val pluginCategoryText = item.pluginCategory.labelText()
    val phonemizerLangText = item.phonemizerLanguage.labelText()
    val languageText = displayLanguage(item.language)
    val sizeText = humanSize(sizeBytes)
    return buildList {
        prefix?.takeIf { it.isNotBlank() }?.let { add(it) }
        when (item.kind) {
            ResourceKind.VOICEBANK -> {
                add(engineText)
                languageText.takeIf { it.isNotBlank() }?.let { add(it) }
                item.recordingType.takeIf { it.isNotBlank() }?.let { add(it) }
            }
            ResourceKind.PLUGIN -> {
                add(pluginCategoryText)
                item.format.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                if (item.stars > 0) add("\u2605 " + item.stars)
            }
            ResourceKind.PHONEMIZER -> {
                add(phonemizerLangText)
                add("DLL")
                if (item.stars > 0) add("\u2605 " + item.stars)
            }
        }
        sizeText.takeIf { it.isNotBlank() }?.let { add(it) }
        item.version.takeIf { it.isNotBlank() }?.let { add("v" + it.removePrefix("v")) }
    }.joinToString("  ·  ")
}


/**
 * 网络 / 平台状态的文字颜色。
 *
 * 之前只有 RED 会变红，其余状态和普通信息一样是灰色 —— 看起来像"有的字上色了、
 * 有的没上色"。现在四种状态各自对应一个颜色，与前面的圆点一致：
 * 规则（按用户要求）：**只有「国内可能无法访问」变红**，
 * 其余状态（直连可用 / 直连缓慢 / 需代理 / 待检测）一律用和正文一样的默认色。
 *
 * 也就是说：颜色不跟随圆点。红色是"警告"，其余只是信息 ——
 * 满屏彩字反而看不出重点。
 */
@Composable
internal fun networkTextColor(net: NetworkStatus, hasPlatform: Boolean): androidx.compose.ui.graphics.Color {
    val c = OuTheme.colors
    if (hasPlatform) return c.foregroundDisabled
    return if (net == NetworkStatus.RED) c.accentColor3 else c.foregroundDisabled
}
