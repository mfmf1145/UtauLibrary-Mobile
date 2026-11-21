package com.utaumarket.mobile.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.utaumarket.mobile.R
import com.utaumarket.mobile.ui.components.OuButton
import com.utaumarket.mobile.ui.components.OuButtonVariant
import com.utaumarket.mobile.ui.theme.OuTheme

/**
 * 首次启动时的两步设置教程。
 *
 * 【为什么需要】本 App 只是 OpenUtau Mobile 的资源管理器，
 * 两个 App 必须指向同一个存储目录，下载的声库才能被 OpenUtau 读到。
 * 这一步对普通用户很不直观（涉及 SAF 授权 + OpenUtau 的额外歌手目录），
 * 所以做成带图的引导，只在首次进入时弹一次。
 *
 * 样式沿用 OpenUtau：直角、1px 边框、无阴影、紧凑排版。
 */
@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    val c = OuTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                // 同理：最高 85% 屏高，正文可滚动，底部按钮永远露出来
                .fillMaxHeight(0.85f)
                .background(c.background)
                .border(1.dp, c.border),
        ) {
            // ---- 标题区
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.backgroundPointerOver)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    stringResource(R.string.tutorial_title),
                    style = OuTheme.typography.title,
                    color = c.foreground,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tutorial_intro),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

            // ---- 可滚动正文
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                TutorialStep(
                    index = 1,
                    title = stringResource(R.string.tutorial_step1_title),
                    imageRes = R.drawable.tutorial_step1,
                ) {
                    Text(
                        stringResource(R.string.tutorial_step1_body),
                        style = OuTheme.typography.small,
                        color = c.foreground,
                    )
                    Spacer(Modifier.height(4.dp))
                    BulletLine(stringResource(R.string.tutorial_step1_bullet1))
                    Spacer(Modifier.height(2.dp))
                    BulletLine(stringResource(R.string.tutorial_step1_bullet2))
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.tutorial_step1_note),
                        style = OuTheme.typography.small,
                        color = c.foregroundDisabled,
                    )
                }

                Spacer(Modifier.height(12.dp))

                TutorialStep(
                    index = 2,
                    title = stringResource(R.string.tutorial_step2_title),
                    imageRes = R.drawable.tutorial_step2,
                ) {
                    Text(
                        stringResource(R.string.tutorial_step2_body),
                        style = OuTheme.typography.small,
                        color = c.foreground,
                    )
                    Spacer(Modifier.height(6.dp))
                    // 提示条：左侧一条强调色竖线，和正文区分开
                    Row(Modifier.fillMaxWidth()) {
                        Box(Modifier.width(2.dp).height(34.dp).background(c.accent))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.tutorial_step2_tip),
                            style = OuTheme.typography.small,
                            color = c.foregroundDisabled,
                        )
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))

            // ---- 底部按钮
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OuButton(
                        stringResource(R.string.tutorial_later),
                        onClick = onDismiss,
                        variant = OuButtonVariant.Clear,
                    )
                    Spacer(Modifier.width(8.dp))
                    OuButton(
                        stringResource(R.string.tutorial_ok),
                        onClick = onDismiss,
                        variant = OuButtonVariant.Accent,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tutorial_footer),
                    style = OuTheme.typography.small,
                    color = c.foregroundDisabled,
                )
            }
        }
    }
}

/** 一个步骤：序号 + 标题 + 配图 + 正文 */
@Composable
private fun TutorialStep(
    index: Int,
    title: String,
    imageRes: Int,
    content: @Composable () -> Unit,
) {
    val c = OuTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 序号方块
            Box(
                Modifier
                    .width(18.dp)
                    .height(18.dp)
                    .background(c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    index.toString(),
                    style = OuTheme.typography.small,
                    color = if (c.isDark) Color(0xFF101014) else Color.White,
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(title, style = OuTheme.typography.body, color = c.foreground)
        }
        Spacer(Modifier.height(6.dp))
        // 配图：加细边框，和 OpenUtau 的卡片风格一致
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, c.border),
            contentScale = ContentScale.FillWidth,
        )
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun BulletLine(text: String) {
    val c = OuTheme.colors
    Row(Modifier.fillMaxWidth()) {
        Text("• ", style = OuTheme.typography.small, color = c.accent)
        Text(text, style = OuTheme.typography.small, color = c.foreground)
    }
}
