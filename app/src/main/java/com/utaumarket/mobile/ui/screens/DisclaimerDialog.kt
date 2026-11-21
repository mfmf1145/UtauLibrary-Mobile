package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.components.OuChip
import com.utaumarket.mobile.ui.theme.OuTheme
import java.util.Locale

/**
 * 首次启动的「使用前须知」（免责声明），支持简中 / 繁中 / English / 日本語。
 *
 * 单独写成这个组件而不是复用 OuDialog，是因为正文较长，需要可滚动区域；
 * 条款类文本要让人能安静看完，不能挤在一个不能滚的小框里。
 */
@Composable
fun DisclaimerDialog(onAccept: () -> Unit) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    var lang by remember { mutableStateOf(defaultLang()) }
    val t = DISCLAIMER[lang] ?: DISCLAIMER.getValue("zh")

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Column(
            Modifier
                .widthIn(min = 320.dp, max = 460.dp)
                // 最高只占屏幕 85%，保证底部按钮一定露出来
                .fillMaxHeight(0.85f)
                .background(c.background, RoundedCornerShape(d.overlayCornerRadius))
                .border(d.borderWidth, c.border, RoundedCornerShape(d.overlayCornerRadius))
                .padding(16.dp),
        ) {
            Text(t.title, style = OuTheme.typography.title, color = c.foreground)
            Spacer(Modifier.height(8.dp))
            Text(t.subtitle, style = OuTheme.typography.small, color = c.foregroundDisabled)
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                LANGS.forEach { (code, label) ->
                    OuChip(label, lang == code) { lang = code }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(10.dp))

            // 【关键】正文用 weight(1f) 吃掉剩余高度并内部滚动，
            // 标题、语言切换、底部「我已阅读并同意」始终可见。
            // 之前是固定 max 400dp，小屏上正文一长就把按钮顶出屏幕（用户反馈被遮挡）。
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                t.sections.forEachIndexed { i, s ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    if (s.heading.isNotBlank()) {
                        Text(s.heading, style = OuTheme.typography.body, color = c.foreground)
                        Spacer(Modifier.height(3.dp))
                    }
                    Text(s.body, style = OuTheme.typography.small, color = c.foreground)
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OuButton(
                    t.accept,
                    onClick = onAccept,
                    variant = OuButtonVariant.Accent,
                )
            }
        }
    }
}

private fun defaultLang(): String {
    val l = Locale.getDefault()
    return when {
        l.language == "ja" -> "ja"
        l.language == "en" -> "en"
        l.language == "zh" && (l.country == "TW" || l.country == "HK" || l.country == "MO") -> "zh-Hant"
        l.language == "zh" -> "zh"
        else -> "en"
    }
}

internal data class Section(val heading: String, val body: String)
internal data class Doc(val title: String, val subtitle: String, val accept: String, val sections: List<Section>)
private val LANGS = listOf(
    "zh" to "简体中文",
    "zh-Hant" to "繁體中文",
    "en" to "English",
    "ja" to "日本語",
)