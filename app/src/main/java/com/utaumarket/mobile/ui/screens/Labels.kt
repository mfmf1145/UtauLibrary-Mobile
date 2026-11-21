package com.utaumarket.mobile.ui.screens

import androidx.compose.material3.Text
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.utaumarket.mobile.ui.theme.OuTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.utaumarket.mobile.R
import com.utaumarket.mobile.data.model.EngineType
import com.utaumarket.mobile.data.model.LanguageTag
import com.utaumarket.mobile.data.model.NetworkStatus
import com.utaumarket.mobile.data.model.PhonemizerLanguage
import com.utaumarket.mobile.data.model.PluginCategory
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.data.model.VoicebankFamily

/**
 * 枚举上的中文标签全部搬到这里，换成字符串资源。
 * 数据层（data.model）不再持有任何文案 —— 它只管 id，显示交给 UI 决定语言。
 */

@Composable
internal fun ResourceKind.labelText(): String = when (this) {
    ResourceKind.VOICEBANK -> stringResource(R.string.kind_voicebank)
    ResourceKind.PLUGIN -> stringResource(R.string.kind_plugin)
    ResourceKind.PHONEMIZER -> stringResource(R.string.kind_phonemizer)
}

@Composable
internal fun EngineType.labelText(): String = when (this) {
    EngineType.UTAU -> stringResource(R.string.engine_utau)
    EngineType.DIFFSINGER -> stringResource(R.string.engine_diffsinger)
    EngineType.OTHER -> stringResource(R.string.engine_other)
}

@Composable
internal fun VoicebankFamily.labelText(): String = when (this) {
    VoicebankFamily.UTAU -> stringResource(R.string.family_utau)
    VoicebankFamily.DIFFSINGER -> stringResource(R.string.family_diffsinger)
    VoicebankFamily.OTHER -> stringResource(R.string.family_other)
}

@Composable
internal fun PluginCategory.labelText(): String = when (this) {
    PluginCategory.LYRICS -> stringResource(R.string.cat_lyrics)
    PluginCategory.PITCH -> stringResource(R.string.cat_pitch)
    PluginCategory.MARKER -> stringResource(R.string.cat_marker)
    PluginCategory.SOUND -> stringResource(R.string.cat_sound)
    PluginCategory.UTILITY -> stringResource(R.string.cat_utility)
}

@Composable
internal fun PhonemizerLanguage.labelText(): String = when (this) {
    PhonemizerLanguage.JA -> stringResource(R.string.lang_ja)
    PhonemizerLanguage.ZH -> stringResource(R.string.lang_zh)
    PhonemizerLanguage.EN -> stringResource(R.string.lang_en)
    PhonemizerLanguage.OTHER -> stringResource(R.string.lang_other)
}

@Composable
internal fun LanguageTag.labelText(): String = when (this) {
    LanguageTag.JA -> stringResource(R.string.lang_ja)
    LanguageTag.ZH -> stringResource(R.string.lang_zh)
    LanguageTag.EN -> stringResource(R.string.lang_en)
    LanguageTag.KO -> stringResource(R.string.lang_ko)
    LanguageTag.MULTI -> stringResource(R.string.lang_multi)
    LanguageTag.OTHER -> stringResource(R.string.lang_other)
}

/** 语言字段可能是 "ja,zh" 这种多值，逐段翻译后用 / 连接。 */
@Composable
internal fun displayLanguage(id: String?): String {
    if (id.isNullOrBlank()) return ""
    // joinToString 的 lambda 不是 @Composable，只能先 for 循环把标签取好
    val parts = id.split(',', '/')
    val labels = ArrayList<String>(parts.size)
    for (part in parts) {
        labels.add(LanguageTag.fromId(part.trim()).labelText())
    }
    return labels.joinToString(" / ")
}

@Composable
internal fun networkEmoji(status: NetworkStatus): String =
    status.emoji + " " + networkLabel(status)

/** 只要文字，不带 emoji —— 圆点改用 DrawScope 画，避免彩色 emoji 和界面风格冲突 */
@Composable
internal fun networkLabel(status: NetworkStatus): String = stringResource(
    when (status) {
        NetworkStatus.GREEN -> R.string.net_green
        NetworkStatus.YELLOW -> R.string.net_yellow
        NetworkStatus.RED -> R.string.net_red
        NetworkStatus.PROXY -> R.string.net_proxy
        NetworkStatus.UNKNOWN -> R.string.net_unknown
    }
)

/**
 * 网络状态圆点。
 *
 * 【为什么不用 emoji】🟢🟡🔴 是系统彩色 emoji：字体由系统决定，
 * 在深色/浅色主题下观感不一致，而且和企业风的直角界面放在一起很跳。
 * 改成用主题色画出来的实心圆，颜色跟着配色方案走，任何主题下都协调。
 */
/**
 * 网络状态圆点的颜色。
 *
 * 「直连缓慢」原先用主题里的 warning 色，在深色底上偏棕，几乎看不出是黄 ——
 * 这里改成明确的琥珀黄 #F0B429，深浅主题下都能一眼辨认。
 * 绿/红也一并固定为不受配色方案影响的语义色，避免选某些主题时变味。
 */
@Composable
internal fun networkDotColor(status: NetworkStatus): androidx.compose.ui.graphics.Color {
    val c = OuTheme.colors
    return when (status) {
        // 【与 emoji 取同色】这三组值是从本机截图里直接采样 🟢🟡🔴 得到的：
        //   🟢 #3EC65A   🟡 #FECC31   🔴 #E52728
        // 这样切换「圆点 / Emoji」两种样式时，颜色完全一致，不会有割裂感。
        NetworkStatus.GREEN -> androidx.compose.ui.graphics.Color(0xFF3EC65A)
        NetworkStatus.YELLOW -> androidx.compose.ui.graphics.Color(0xFFFECC31)
        NetworkStatus.RED -> androidx.compose.ui.graphics.Color(0xFFE52728)
        NetworkStatus.PROXY -> c.accent
        NetworkStatus.UNKNOWN -> c.foregroundDisabled
    }
}

/** 网络状态指示样式："dot" = 画的圆点，"emoji" = 系统彩色 emoji */
val LocalNetIndicatorStyle = androidx.compose.runtime.staticCompositionLocalOf { "dot" }

/**
 * 网络状态指示器。
 *
 * 两种样式由设置页切换：
 *  · dot   —— 主题色实心圆（默认，平直风格）
 *  · emoji —— 🟢🟡🔴（醒目，但字体由系统决定）
 */
@Composable
internal fun NetworkIndicator(
    status: NetworkStatus,
    size: androidx.compose.ui.unit.Dp = 10.dp,
) {
    if (LocalNetIndicatorStyle.current == "emoji") {
        Text(status.emoji, style = OuTheme.typography.small)
    } else {
        NetworkDot(status, size = size)
    }
}

@Composable
internal fun NetworkDot(
    status: NetworkStatus,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    size: androidx.compose.ui.unit.Dp = 10.dp,
) {
    val color = networkDotColor(status)
    androidx.compose.foundation.Canvas(modifier.size(size)) {
        drawCircle(color)
    }
}
