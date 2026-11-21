package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.utaumarket.mobile.R
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.theme.OuTheme
import androidx.compose.ui.res.stringResource

/**
 * 每次下载前的「下载须知」。
 *
 * 用户要求：可以勾选「不再提示」让以后不再显示，**默认不勾选**。
 * 勾选并继续后写入设置；之后可在「设置 → 下载须知」里重新打开。
 */
@Composable
fun DownloadNoticeDialog(
    dontShowAgain: Boolean,
    onDontShowAgainChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = true)) {
        Column(
            Modifier
                .widthIn(min = 300.dp, max = 420.dp)
                .background(c.background, RoundedCornerShape(d.overlayCornerRadius))
                .border(d.borderWidth, c.border, RoundedCornerShape(d.overlayCornerRadius))
                .padding(16.dp),
        ) {
            Text(
                stringResource(R.string.dl_notice_title),
                style = OuTheme.typography.title,
                color = c.foreground,
            )
            Spacer(Modifier.height(10.dp))
            // 整段用一个 Text，靠 \n 自然换行 —— 之前每行套一个 Row，
            // 行内不换行会横向溢出，看起来像"挤成一坨"。
            Text(
                text = stringResource(R.string.dl_notice_body),
                style = OuTheme.typography.small,
                color = c.foreground,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))

            // 「不再提示」——默认不勾选
            Row(
                Modifier
                    .fillMaxWidth()
                    // 去掉整行涟漪：默认 indication 会把整行涂成一条深色长条，
                    // 看起来像"点一下出现大黑边"（用户反馈）。勾选反馈靠方块本身变色即可。
                    .clickable(
                        interactionSource = androidx.compose.runtime.remember {
                            androidx.compose.foundation.interaction.MutableInteractionSource()
                        },
                        indication = null,
                    ) { onDontShowAgainChange(!dontShowAgain) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckBoxSquare(checked = dontShowAgain)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.dl_notice_dont_show),
                    style = OuTheme.typography.small,
                    color = c.foreground,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OuButton(
                    stringResource(R.string.dl_notice_cancel),
                    onClick = onDismiss,
                    variant = OuButtonVariant.Clear,
                )
                Spacer(Modifier.width(8.dp))
                OuButton(
                    stringResource(R.string.dl_notice_confirm),
                    onClick = onConfirm,
                    variant = OuButtonVariant.Accent,
                )
            }
        }
    }
}

/** 方形勾选框（不引入 Material Checkbox，保持 OpenUtau 的方角风格） */
@Composable
private fun CheckBoxSquare(checked: Boolean) {
    val c = OuTheme.colors
    androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
        val stroke = 1.5.dp.toPx()
        drawRect(
            color = if (checked) c.accent else c.border,
            size = size,
            style = if (checked) androidx.compose.ui.graphics.drawscope.Fill
            else androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )
        if (checked) {
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width * 0.22f, size.height * 0.52f)
                lineTo(size.width * 0.42f, size.height * 0.72f)
                lineTo(size.width * 0.80f, size.height * 0.28f)
            }
            drawPath(
                p,
                color = c.background,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke * 1.2f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
        }
    }
}
