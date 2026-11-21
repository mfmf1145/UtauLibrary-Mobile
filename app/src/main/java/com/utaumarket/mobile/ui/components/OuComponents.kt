package com.utaumarket.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.utaumarket.mobile.ui.theme.OuTheme

/**
 * OpenUtau 原版控件风格的 Compose 实现。
 *
 * 全部规则来自:
 *   OpenUtau/Styles/Styles.axaml     —— 高度 / 内边距 / 字号 / 圆角
 *   OpenUtau/Colors/Brushes.axaml    —— 各状态的画刷
 *   OpenUtau/Colors/{Dark,Light}Theme.axaml —— 具体颜色
 *
 * 刻意不使用 Material 的涟漪、阴影、圆角与默认配色。
 */

@Composable
private fun Modifier.noRippleClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    this.clickable(
        enabled = enabled,
        indication = null,
        interactionSource = remember { MutableInteractionSource() },
        onClick = onClick,
    )

/** 触摸目标辅助修饰符：保持视觉不变，但保证最小可点击区域。 */
private fun Modifier.touchTarget(min: Dp): Modifier = this.then(Modifier.height(min))

// ---------------------------------------------------------------- surface / card

/**
 * 卡片：对应 OpenUtau 的 HeaderedContentControl.groupbox
 * (Background=SystemControlBackgroundAltHighBrush, BorderBrush=SystemControlForegroundBaseMediumBrush, CornerRadius=0)
 */
@Composable
fun OuCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val bg = when {
        pressed -> c.backgroundPressed
        selected -> c.listHighlight
        hovered -> c.backgroundPointerOver
        else -> c.background
    }
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
        )
    } else Modifier

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(d.controlCornerRadius))
            .border(d.borderWidth, c.border, RoundedCornerShape(d.controlCornerRadius))
            .then(clickModifier)
            .padding(d.gutter),
        content = content,
    )
}

/** 分组容器，等价于 OpenUtau 的 .groupbox */
@Composable
fun OuGroupBox(
    title: String? = null,
    modifier: Modifier = Modifier,
    /** 折叠状态下显示的一行摘要，让收起的分组也有信息量 */
    summary: String? = null,
    /** 是否可折叠。设置页用它把长页面压成一列stringResource(com.utaumarket.mobile.R.string.dir) */
    collapsible: Boolean = false,
    defaultExpanded: Boolean = false,
    /** 点击分组标题时的额外回调（设置页用它做「连点五次进开发者模式」） */
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    var expanded by rememberSaveable { mutableStateOf(defaultExpanded) }
    val open = !collapsible || expanded

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = d.groupBoxMargin)
            // 不再给整块刷深色背景 + 粗边框（用户反馈"深色框很怪"）。
            // 改成：页面底色 + 左侧一条强调色竖条做分隔，视觉轻很多。
            .padding(start = 2.dp),
    ) {
        if (!title.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 【互斥】有 onHeaderClick 时，点标题只触发回调解数，
                    // 不再同时折叠/展开 —— 否则连点 5 次要顺带把列表开合 5 次（用户反馈误触）。
                    // 折叠交给右侧的箭头图标单独处理。
                    .then(
                        when {
                            onHeaderClick != null -> Modifier.clickable { onHeaderClick() }
                            collapsible -> Modifier.clickable { expanded = !expanded }
                            else -> Modifier
                        }
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = OuTheme.typography.title, color = c.foreground)
                    if (!open && !summary.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            summary,
                            style = OuTheme.typography.small,
                            color = c.foregroundDisabled,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (collapsible) {
                    // 用一个小箭头代替"收起/展开"文字，更清爽
                    Icon(
                        painterResource(
                            if (expanded) com.utaumarket.mobile.ui.theme.OuIcons.chevronUp
                            else com.utaumarket.mobile.ui.theme.OuIcons.chevronDown,
                        ),
                        // 【一直可点】之前写成 enabled = (onHeaderClick != null)，
                        // 结果普通分组的箭头挂着一个 disabled 的 clickable —— 它照样吃掉点击事件，
                        // 父级的整行点击也到不了，看起来就是"箭头点不动"（用户反馈）。
                        // 现在箭头永远自己处理折叠，和整行点击互不影响。
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { expanded = !expanded }
                            .padding(6.dp),
                        contentDescription = stringResource(
                            if (expanded) com.utaumarket.mobile.R.string.collapse
                            else com.utaumarket.mobile.R.string.expand,
                        ),
                        tint = c.accent,
                    )
                }
            }
        }
        if (open) {
            Column(Modifier.padding(bottom = 10.dp)) { content() }
        }
        // 分组之间用一条浅分隔线收尾，替代原来的粗边框
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border.copy(alpha = 0.35f)))
        Spacer(Modifier.height(10.dp))
    }
}

// ---------------------------------------------------------------- button

enum class OuButtonVariant {
    Normal,
    Clear,
    Accent,
    /**
     * 描边按钮：透明底 + 强调色 1dp 边框 + 强调色文字。
     *
     * 用于「查看版本」这类**二级动作** —— 既不像 Accent 实心那样抢焦点，
     * 又因为有彩色一圈边框而明显"能点"（用户反馈纯灰描边像禁用的）。
     */
    Outlined,
}

/**
 * 按钮，对应 OpenUtau 的 Button / Button.clear / .normal:
 *   BorderThickness=0, FontSize=12, Padding=4,0,4,2, CornerRadius=0
 * 移动端仅把触摸高度放宽到 32dp（桌面 20px）。
 */
@Composable
fun OuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: OuButtonVariant = OuButtonVariant.Normal,
    leadingIcon: Painter? = null,
    leadingIconRes: Int? = null,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()

    val bg = when (variant) {
        OuButtonVariant.Clear, OuButtonVariant.Outlined -> when {
            pressed -> c.backgroundPressed
            hovered -> c.backgroundPointerOver
            else -> Color.Transparent
        }
        OuButtonVariant.Accent -> when {
            pressed -> c.accentDark1
            hovered -> c.accentLight1
            else -> c.accent
        }
        OuButtonVariant.Normal -> when {
            pressed -> c.backgroundPressed
            hovered -> c.backgroundPointerOver
            else -> c.background
        }
    }
    val fg = when {
        !enabled -> c.foregroundDisabled
        variant == OuButtonVariant.Accent -> if (c.isDark) Color(0xFF101014) else Color.White
        variant == OuButtonVariant.Outlined -> c.accent
        else -> c.foreground
    }
    // 描边按钮的一圈彩色边框 —— 这是"能点"的视觉信号
    val ringColor = if (variant == OuButtonVariant.Outlined && enabled) c.accent else Color.Transparent
    val ringWidth = if (variant == OuButtonVariant.Outlined) 1.dp else 0.dp

    Row(
        modifier = modifier
            .height(d.buttonHeight)
            .background(bg, RoundedCornerShape(d.controlCornerRadius))
            .border(ringWidth, ringColor, RoundedCornerShape(d.controlCornerRadius))
            .noRippleClickable(enabled) { onClick() }
            .padding(horizontal = d.buttonPaddingH, vertical = d.buttonPaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val painter = leadingIcon ?: leadingIconRes?.let { painterResource(it) }
        if (painter != null) {
            Icon(
                painter = painter,
                contentDescription = null,
                tint = if (variant == OuButtonVariant.Accent) fg else c.accent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = OuTheme.typography.body,
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 图标按钮：对应 OpenUtau 工具栏里的透明按钮。 */
@Composable
fun OuIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    size: Dp = 20.dp,
    touch: Dp = OuTheme.dimens.touchTarget,
    /** >0 时在右上角显示数量角标（方形，跟随 OpenUtau 的无圆角风格） */
    badge: Int = 0,
    badgeColor: Color? = null,
    /** 有任务正在跑时让图标呼吸闪烁，一眼能看出stringResource(com.utaumarket.mobile.R.string.downloading) */
    pulsing: Boolean = false,
) {
    val c = OuTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val pulse = if (pulsing) {
        val t = rememberInfiniteTransition(label = "icon-pulse")
        val a by t.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "icon-pulse-a",
        )
        a
    } else {
        1f
    }
    val bg = when {
        !enabled -> Color.Transparent
        pressed -> c.backgroundPressed
        hovered -> c.backgroundPointerOver
        else -> Color.Transparent
    }
    Box(
        modifier = modifier
            .height(touch)
            .width(touch)
            .background(bg, RoundedCornerShape(0.dp))
            .then(if (onClick != null) Modifier.noRippleClickable(enabled) { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // 角标要贴着图标本身，而不是贴在这个 48dp 的点击热区上，否则会飘到很远的地方
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = painter,
                contentDescription = contentDescription,
                tint = (tint ?: if (enabled) c.foreground else c.foregroundDisabled).copy(alpha = pulse),
                modifier = Modifier.size(size),
            )
            if (badge > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 7.dp, y = (-7).dp)
                        // 用背景色做一圈stringResource(com.utaumarket.mobile.R.string.style_soft_outline)，比硬边框柔和
                        .background(c.background, RoundedCornerShape(7.dp))
                        .padding(1.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .background(badgeColor ?: c.accent, RoundedCornerShape(6.dp))
                            .padding(horizontal = 4.dp, vertical = 0.5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (badge > 99) "99+" else badge.toString(),
                            style = TextStyle(fontSize = 9.sp, lineHeight = 11.sp),
                            color = if (c.isDark) Color(0xFF101014) else Color.White,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- text field

/**
 * 输入框，对应 OpenUtau 的 TextBox:
 *   MinHeight=20, Padding=4,1, FontSize=12, CornerRadius=0
 * 移动端触摸高度 36dp。
 */
@Composable
fun OuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    leadingRes: Int? = null,
    enabled: Boolean = true,
    textStyle: TextStyle = OuTheme.typography.body,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(d.textFieldHeight)
            .background(
                if (enabled) c.background else c.backgroundDisabled,
                RoundedCornerShape(d.controlCornerRadius)
            )
            .border(
                d.borderWidth,
                if (hovered) c.borderPointerOver else c.border,
                RoundedCornerShape(d.controlCornerRadius)
            )
            .padding(horizontal = d.textFieldPaddingH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingRes != null) {
            Icon(
                painter = painterResource(leadingRes),
                contentDescription = null,
                tint = c.foregroundDisabled,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(
                    placeholder,
                    style = textStyle,
                    color = c.foregroundDisabled,
                    maxLines = if (singleLine) 1 else 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = singleLine,
                textStyle = textStyle.copy(color = c.foreground),
                cursorBrush = SolidColor(c.accent),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ---------------------------------------------------------------- small parts

/** 标签，对应 OpenUtau Border.playback / Border.tips 的 1px 描边小方块（圆角 4）。 */
@Composable
fun OuTag(
    text: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    tone: Color? = null,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    val borderColor = tone ?: if (accent) c.accent else c.neutralAccentSemi
    val textColor = tone ?: if (accent) c.accent else c.foreground
    Row(
        modifier = modifier
            .background(
                if (tone != null) c.background else Color.Transparent,
                RoundedCornerShape(d.smallCornerRadius)
            )
            .border(d.borderWidth, borderColor, RoundedCornerShape(d.smallCornerRadius))
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = OuTheme.typography.small, color = textColor, maxLines = 1)
    }
}

/**
 * 扁平列表行。
 *
 * OpenUtau 的 ListBox 是stringResource(com.utaumarket.mobile.R.string.style_row_highlight)的密集列表，而不是一张张独立卡片。
 * 这里刻意不加边框、不加圆角：底部一条 1px 分隔线，按下/悬停用 Brushes.axaml 里的
 * ListLow / BackgroundPointerOver 画刷。
 */
@Composable
fun OuListRow(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    contentPadding: Dp = 8.dp,
    /** 左侧 3dp 分类色条，用 OpenUtau 的 AccentColor1/2/3 区分引擎或分类 */
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    val shape = RoundedCornerShape(d.itemCornerRadius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    // 卡片面色用 OpenUtau 的 TrackBackgroundAltColor（深色 #302C38 / 浅色 #F0F0F0），
    // 和页面底色拉开一点点层次，但不用阴影、不用 Material 抬升。
    val bg = when {
        pressed -> c.backgroundPressed
        selected -> c.listHighlight
        hovered -> c.backgroundPointerOver
        else -> c.trackBackgroundAlt
    }
    val stripeWidth = 3.dp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                val a = accent
                if (a != null) {
                    drawRect(color = a, size = Size(stripeWidth.toPx(), size.height))
                }
            }
            .background(bg, shape)
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
            .padding(
                start = d.pagePadding + stripeWidth,
                end = d.pagePadding,
                top = contentPadding,
                bottom = contentPadding,
            ),
        content = content,
    )
}

/**
 * 筛选片：OpenUtau 没有独立 Chip 组件，这里沿用 Border.playback / Border.tips 的
 * 1px 描边小方块（圆角 4）。未选中时无描边、无底色，视觉更安静。
 */
@Composable
fun OuChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .height(d.chipHeight)
            .background(
                when {
                    pressed -> c.backgroundPressed
                    selected -> c.neutralAccentSemi
                    else -> Color.Transparent
                },
                RoundedCornerShape(d.smallCornerRadius)
            )
            .then(
                if (selected) Modifier.border(d.borderWidth, c.accent, RoundedCornerShape(d.smallCornerRadius))
                else Modifier
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = OuTheme.typography.small,
            color = if (selected) c.foregroundPointerOver else c.foregroundDisabled,
            maxLines = 1,
        )
    }
}

@Composable
fun OuDivider(modifier: Modifier = Modifier, color: Color? = null) {
    val c = OuTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color ?: c.border)
    )
}

@Composable
fun OuVerticalDivider(color: Color? = null) {
    val c = OuTheme.colors
    Box(
        Modifier
            .width(1.dp)
            .height(OuTheme.dimens.gutter * 2)
            .background(color ?: c.border)
    )
}

/**
 * 进度条，对应 OpenUtau 的 ProgressBar:
 *   Background=Transparent, Foreground=AccentBrush1, CornerRadius=0
 */
@Composable
fun OuProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    indeterminate: Boolean = false,
) {
    val c = OuTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.Transparent)
    ) {
        if (indeterminate) {
            // 之前这里是固定画 35% 宽度，看着像stringResource(com.utaumarket.mobile.R.string.style_half_progress)。
            // 改成来回滑动的片段，一眼就能看出是在进行中。
            val transition = rememberInfiniteTransition(label = "ou-progress")
            val x by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1100, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "ou-progress-x",
            )
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.fillMaxWidth().height(height)
            ) {
                val seg = maxWidth * 0.32f
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .offset(x = (maxWidth + seg) * x - seg)
                        .width(seg)
                        .height(height)
                        .background(c.accent)
                )
            }
        } else {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(height)
                    .background(c.accent)
            )
        }
    }
}

/** 复选框，对应 OpenUtau CheckBox: 20x20, 勾选色 AccentBrush1, 无圆角 */
@Composable
fun OuCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    Box(
        modifier
            .size(d.checkBoxSize)
            .border(if (checked) 0.dp else d.borderWidth, c.border)
            .noRippleClickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                painter = painterResource(com.utaumarket.mobile.ui.theme.OuIcons.checkmark),
                contentDescription = null,
                tint = c.accent,
                modifier = Modifier.size(d.checkBoxSize - 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- bars

/** 顶部栏：OpenUtau 主窗口标题栏的移动端对应物（同底色、1px 下边框、0 圆角）。 */
@Composable
fun OuTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    // 横屏垂直空间很紧张，标题+副标题两行占掉太多。横屏时把副标题挪到标题右边同一行，
    // 并压低压栏高度，把省下来的空间留给内容。
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    Column(modifier.fillMaxWidth().background(c.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (landscape) 32.dp else d.topBarHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (landscape) {
                Text(
                    title,
                    style = OuTheme.typography.topTitle,
                    color = c.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        subtitle,
                        style = OuTheme.typography.small,
                        color = c.foregroundDisabled,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Text(title, style = OuTheme.typography.topTitle, color = c.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!subtitle.isNullOrBlank()) {
                        Text(subtitle, style = OuTheme.typography.small, color = c.foregroundDisabled, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            actions()
        }
        OuDivider()
    }
}

data class OuNavItem(val label: String, val iconRes: Int)

/**
 * 底部导航：完全沿用 OpenUtau 工具栏 ToggleButton 的配色规则
 *   未选中 = BackgroundColor，选中 = NeutralAccentBrushSemi，按下 = NeutralAccentBrushPointerOver
 */
@Composable
fun OuBottomNav(
    items: List<OuNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    Column(modifier.fillMaxWidth().background(c.background)) {
        OuDivider()
        Row(Modifier.fillMaxWidth().height(d.navBarHeight)) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val bg = when {
                    pressed -> c.neutralAccentPointerOver
                    selected -> c.neutralAccentSemi
                    else -> c.background
                }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(bg)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = item.label,
                        tint = if (selected) c.foregroundPointerOver else c.foreground,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.label,
                        style = OuTheme.typography.small,
                        color = if (selected) c.foregroundPointerOver else c.foreground,
                        maxLines = 1,
                        // 英文标签较长（Phonemizer / Voicebanks），不要硬切掉字母
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 左侧导航轨：横屏时替代底部导航，腾出垂直空间（参考布局里也是左侧图标栏）。
 * 选中项仍然使用 OpenUtau 工具栏 ToggleButton 的 NeutralAccentBrushSemi 底色。
 */
@Composable
fun OuNavRail(
    items: List<OuNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OuTheme.colors
    Row(modifier.fillMaxHeight()) {
        // 【关键修复】之前每个条目固定 62dp，7 个就是 434dp；
        // 横屏可用高度只有 400dp 左右，最后一项「设置」会被直接挤出屏幕 ——
        // 用户强制横屏后就再也点不到设置，等于把自己锁死。
        // 现在：条目高度按可用空间自适应（44~62dp），并且整列可滚动兜底。
        Column(
            Modifier
                .width(76.dp)
                .fillMaxHeight()
                .background(c.background)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(4.dp))
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val bg = when {
                    pressed -> c.neutralAccentPointerOver
                    selected -> c.neutralAccentSemi
                    else -> c.background
                }
                Column(
                    Modifier
                        .fillMaxWidth()
                        // 44dp 是安卓可访问性最小触摸尺寸，低于它会点不准
                        .height(52.dp)
                        .background(bg)
                        .clickable(interactionSource = interaction, indication = null) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = item.label,
                        tint = if (selected) c.foregroundPointerOver else c.foreground,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.label,
                        style = OuTheme.typography.small,
                        color = if (selected) c.foregroundPointerOver else c.foreground,
                        maxLines = 1,
                        // 英文标签较长（Phonemizer / Voicebanks），不要硬切掉字母
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            // 底部留白：最后一项贴着屏幕边缘不好点
            Spacer(Modifier.height(6.dp))
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
    }
}

/** 分页标签：沿用 OpenUtau 的 Fluent TabControl（选中项底部 2px AccentBrush1 指示条）。 */
@Composable
fun OuTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = false,
) {
    val c = OuTheme.colors
    Column(modifier.fillMaxWidth().background(c.background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val hovered by interaction.collectIsHoveredAsState()
                val sizing = if (scrollable) Modifier.padding(horizontal = 16.dp) else Modifier.weight(1f)
                Column(
                    sizing.clickable(interactionSource = interaction, indication = null) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .then(if (scrollable) Modifier else Modifier.fillMaxWidth())
                            .height(40.dp)
                            .background(
                                when {
                                    pressed -> c.backgroundPressed
                                    hovered -> c.backgroundPointerOver
                                    else -> Color.Transparent
                                }
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = OuTheme.typography.body,
                            color = if (selected) c.foregroundPointerOver else c.foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .then(if (scrollable) Modifier else Modifier.fillMaxWidth())
                            .height(2.dp)
                            .background(if (selected) c.accent else Color.Transparent)
                    )
                }
            }
        }
        OuDivider()
    }
}

/** 信息行：左标签右值，OpenUtau 属性面板的排布方式。 */
@Composable
fun OuInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val c = OuTheme.colors
    Row(modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = OuTheme.typography.small,
            color = c.foregroundDisabled,
            modifier = Modifier.width(76.dp),
        )
        Text(
            value.ifBlank { "—" },
            style = OuTheme.typography.small,
            color = valueColor ?: c.foreground,
        )
    }
}

// ---------------------------------------------------------------- dialog

/** 对话框：OpenUtau 的 OverlayCornerRadius = 0，因此这里也是直角。 */
@Composable
fun OuDialog(
    title: String,
    message: String? = null,
    confirmText: String = stringResource(com.utaumarket.mobile.R.string.ok),
    dismissText: String? = stringResource(com.utaumarket.mobile.R.string.cancel),
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    destructive: Boolean = false,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    Dialog(
        onDismissRequest = { onDismiss?.invoke() },
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        Column(
            Modifier
                .widthIn(min = 260.dp, max = 400.dp)
                .background(c.background, RoundedCornerShape(d.overlayCornerRadius))
                .border(d.borderWidth, c.border, RoundedCornerShape(d.overlayCornerRadius))
                .padding(16.dp)
        ) {
            Text(title, style = OuTheme.typography.title, color = c.foreground)
            // 【关键】正文区可滚动并限高，否则内容一长（比如免责声明、使用须知）
            // 会把底部的「确定 / 取消」按钮顶出屏幕 —— 用户看不到按钮就等于卡死。
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, style = OuTheme.typography.body, color = c.foreground)
                }
                if (content != null) {
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (dismissText != null) {
                    OuButton(dismissText, onClick = { onDismiss?.invoke() }, variant = OuButtonVariant.Clear)
                    Spacer(Modifier.width(8.dp))
                }
                OuButton(
                    confirmText,
                    onClick = onConfirm,
                    variant = if (destructive) OuButtonVariant.Normal else OuButtonVariant.Normal,
                )
            }
        }
    }
}

/**
 * 底部筛选面板。
 *
 * 借鉴参考布局里stringResource(com.utaumarket.mobile.R.string.style_left_filter)的分工：把筛选收进按需弹出的面板，
 * 主列表就能保持干净。外观仍是 OpenUtau 的直角 + 1px 描边。
 */
@Composable
fun OuFilterPanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = OuTheme.colors
    val d = OuTheme.dimens
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() }
            )
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .background(c.background)
                    .border(d.borderWidth, c.border)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = OuTheme.typography.title, color = c.foreground, modifier = Modifier.weight(1f))
                    if (onReset != null) {
                        OuButton(stringResource(com.utaumarket.mobile.R.string.reset), onClick = onReset, variant = OuButtonVariant.Clear)
                    }
                    OuIconButton(painterResource(com.utaumarket.mobile.ui.theme.OuIcons.close), stringResource(com.utaumarket.mobile.R.string.close), onDismiss)
                }
                OuDivider()
                Spacer(Modifier.height(10.dp))
                content()
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OuButton(stringResource(com.utaumarket.mobile.R.string.done), onClick = onDismiss)
                }
            }
        }
    }
}

/** 一行可换行的筛选片 */
@Composable
fun OuChipRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** 筛选分组标题 */
@Composable
fun OuFilterLabel(text: String) {
    Text(
        text,
        style = OuTheme.typography.small,
        color = OuTheme.colors.foregroundDisabled,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
}

/** 章节标题 */
@Composable
fun OuSectionHeader(text: String, modifier: Modifier = Modifier) {
    val c = OuTheme.colors
    Text(
        text,
        style = OuTheme.typography.title,
        color = c.foreground,
        modifier = modifier.padding(vertical = 6.dp),
    )
}

@Composable
fun OuIcon(
    resId: Int,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    size: Dp = 16.dp,
) {
    val c = OuTheme.colors
    Icon(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        tint = tint ?: c.foreground,
        modifier = modifier.size(size),
    )
}

@Composable
fun OuText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    color: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style ?: OuTheme.typography.body,
        color = color ?: OuTheme.colors.foreground,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/** 网络状态标记：🟢直连可用 / 🟡直连缓慢 / 🔴国内无法访问 */
@Composable
fun OuNetworkBadge(status: com.utaumarket.mobile.data.model.NetworkStatus) {
    val c = OuTheme.colors
    val label = androidx.compose.ui.res.stringResource(
        when (status) {
            com.utaumarket.mobile.data.model.NetworkStatus.GREEN -> com.utaumarket.mobile.R.string.net_green
            com.utaumarket.mobile.data.model.NetworkStatus.YELLOW -> com.utaumarket.mobile.R.string.net_yellow
            com.utaumarket.mobile.data.model.NetworkStatus.RED -> com.utaumarket.mobile.R.string.net_red
            com.utaumarket.mobile.data.model.NetworkStatus.PROXY -> com.utaumarket.mobile.R.string.net_proxy
            com.utaumarket.mobile.data.model.NetworkStatus.UNKNOWN -> com.utaumarket.mobile.R.string.net_unknown
        }
    )
    val text = status.emoji + " " + label
    val tone = when (status) {
        com.utaumarket.mobile.data.model.NetworkStatus.RED -> c.accentColor3
        com.utaumarket.mobile.data.model.NetworkStatus.YELLOW -> c.accentColor2
        else -> null
    }
    OuTag(text = text, tone = tone, accent = status == com.utaumarket.mobile.data.model.NetworkStatus.GREEN)
}

@Composable
fun LocalTextStyleCompat(): TextStyle = LocalTextStyle.current
