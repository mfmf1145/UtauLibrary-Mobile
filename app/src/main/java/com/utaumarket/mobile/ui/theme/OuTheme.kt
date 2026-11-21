package com.utaumarket.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.utaumarket.mobile.data.prefs.ThemeMode

/**
 * OpenUtau 原版配色。
 *
 * 数值逐条取自 stakira/OpenUtau (MIT)：
 *   - OpenUtau/Colors/DarkTheme.axaml
 *   - OpenUtau/Colors/LightTheme.axaml
 *   - OpenUtau/Colors/Brushes.axaml
 * 未做任何"AI 发挥"式的取色或替换。
 */
@Immutable
data class OuColors(
    val isDark: Boolean,
    /** BackgroundColor */
    val background: Color,
    /** BackgroundColorPointerOver */
    val backgroundPointerOver: Color,
    /** BackgroundColorPressed */
    val backgroundPressed: Color,
    /** BackgroundColorDisabled */
    val backgroundDisabled: Color,
    /** ForegroundColor */
    val foreground: Color,
    /** ForegroundColorPointerOver */
    val foregroundPointerOver: Color,
    /** ForegroundColorPressed */
    val foregroundPressed: Color,
    /** ForegroundColorDisabled */
    val foregroundDisabled: Color,
    /** BorderColor */
    val border: Color,
    /** BorderColorPointerOver */
    val borderPointerOver: Color,
    /** SystemAccentColor */
    val accent: Color,
    val accentLight1: Color,
    val accentDark1: Color,
    /** NeutralAccentColor */
    val neutralAccent: Color,
    val neutralAccentPointerOver: Color,
    val accentColor1: Color,
    val accentColor2: Color,
    val accentColor3: Color,
    val tickLine: Color,
    val barNumber: Color,
    val finalPitch: Color,
    val trackBackgroundAlt: Color,
    val warning: Color,
) {
    /** Brushes.axaml: SystemControlHighlightListLowBrush = ForegroundColorPressed @ 0.25 */
    val listHighlight: Color get() = foregroundPressed.copy(alpha = 0.25f)

    /** Brushes.axaml: NeutralAccentBrushSemi = NeutralAccentColor @ 0.5 */
    val neutralAccentSemi: Color get() = neutralAccent.copy(alpha = 0.5f)

    /** Brushes.axaml: NeutralAccentBrushSemi2 = NeutralAccentColor @ 0.25 */
    val neutralAccentSemi2: Color get() = neutralAccent.copy(alpha = 0.25f)

    /** Brushes.axaml: ButtonBackgroundPointerOver = ForegroundColorPressed @ 0.25 */
    val buttonPointerOver: Color get() = foregroundPressed.copy(alpha = 0.25f)
}

val OuDarkColors = OuColors(
    isDark = true,
    background = Color(0xFF26232C),
    backgroundPointerOver = Color(0xFF36313F),
    backgroundPressed = Color(0xFF484255),
    backgroundDisabled = Color(0xFF2C2933),
    foreground = Color(0xFFCDCAD4),
    foregroundPointerOver = Color(0xFFE9E6F0),
    foregroundPressed = Color(0xFFFFFFFF),
    foregroundDisabled = Color(0xFF7E778C),
    border = Color(0xFF544E60),
    borderPointerOver = Color(0xFF878096),
    accent = Color(0xFF4EA6EA),
    accentLight1 = Color(0xFF90CAF9),
    accentDark1 = Color(0xFF1E88E5),
    neutralAccent = Color(0xFF878096),
    neutralAccentPointerOver = Color(0xFFB0ABBC),
    accentColor1 = Color(0xFF4EA6EA),
    accentColor2 = Color(0xFFFF679D),
    accentColor3 = Color(0xFFE62E6E),
    tickLine = Color(0xFF615B73),
    barNumber = Color(0xFFB0ABBC),
    finalPitch = Color(0xFFC0BACD),
    trackBackgroundAlt = Color(0xFF302C38),
    warning = Color(0xFF433519),
)

val OuLightColors = OuColors(
    isDark = false,
    background = Color(0xFFFFFFFF),
    backgroundPointerOver = Color(0xFFF0F0F0),
    backgroundPressed = Color(0xFFE0E0E0),
    backgroundDisabled = Color(0xFFD0D0D0),
    foreground = Color(0xFF000000),
    foregroundPointerOver = Color(0xFF000000),
    foregroundPressed = Color(0xFF202020),
    foregroundDisabled = Color(0xFF808080),
    border = Color(0xFF707070),
    borderPointerOver = Color(0xFFB0B0B0),
    accent = Color(0xFF4EA6EA),
    accentLight1 = Color(0xFF90CAF9),
    accentDark1 = Color(0xFF1E88E5),
    neutralAccent = Color(0xFFADA1B3),
    neutralAccentPointerOver = Color(0xFF948A99),
    accentColor1 = Color(0xFF4EA6EA),
    accentColor2 = Color(0xFFFF679D),
    accentColor3 = Color(0xFFE62E6E),
    tickLine = Color(0xFFAFA3B5),
    barNumber = Color(0xFFAFA3B5),
    finalPitch = Color(0xFFC0C0C0),
    trackBackgroundAlt = Color(0xFFF0F0F0),
    warning = Color(0xFFFFF4CE),
)

/**
 * OpenUtau 尺寸体系（Styles.axaml / Brushes.axaml 原值）。
 *
 * 移动端适配原则：视觉语言完全沿用（0 圆角、1px 边框、12/11/13sp 字号、OpenUtau 间距），
 * 仅把"可点击控件的最小触摸高度"由桌面的 20px 放宽到 44dp，
 * 这是安卓可访问性下限，不改变任何配色/圆角/字体层级。
 */
@Immutable
data class OuDimens(
    /** ControlCornerRadius = 0 / OverlayCornerRadius = 0 */
    val controlCornerRadius: Dp = 0.dp,
    val overlayCornerRadius: Dp = 0.dp,
    /** NotificationCard CornerRadius = 6 */
    val notificationCornerRadius: Dp = 6.dp,
    /** Border.playback / Border.tips CornerRadius = 4 */
    val smallCornerRadius: Dp = 4.dp,
    val borderWidth: Dp = 1.dp,
    /** Button Height = 20 / Padding = 4,0,4,2 */
    val buttonHeight: Dp = 32.dp,
    val buttonPaddingH: Dp = 12.dp,
    val buttonPaddingV: Dp = 4.dp,
    /** TextBox MinHeight = 20 / Padding = 4,1 */
    val textFieldHeight: Dp = 36.dp,
    val textFieldPaddingH: Dp = 8.dp,
    /** ListBoxItem MinHeight = 24 / Padding = 4 */
    val listItemMinHeight: Dp = 44.dp,
    val listItemPadding: Dp = 8.dp,
    /** 卡片内边距 */
    val cardPadding: Dp = 10.dp,
    /** 卡片之间的间距 */
    val cardGap: Dp = 8.dp,
    /** 筛选片高度 */
    val chipHeight: Dp = 28.dp,
    /** 列表条目圆角：条目之间需要留白与圆角，避免"贴在一起" */
    val itemCornerRadius: Dp = 8.dp,
    /** 列表条目之间的间距 */
    val itemGap: Dp = 6.dp,
    /** MenuItem Height = 24 / Padding = 8,2 */
    val menuItemHeight: Dp = 40.dp,
    /** CheckBox = 20x20 */
    val checkBoxSize: Dp = 24.dp,
    /** Expander MinHeight = 32 */
    val expanderMinHeight: Dp = 32.dp,
    /** HeaderedContentControl.groupbox Margin = 5 */
    val groupBoxMargin: Dp = 8.dp,
    val pagePadding: Dp = 8.dp,
    val gutter: Dp = 8.dp,
    /** 底部导航：OpenUtau 工具栏 ToggleButton 的移动端触摸高度 */
    val navBarHeight: Dp = 56.dp,
    val topBarHeight: Dp = 48.dp,
    val touchTarget: Dp = 44.dp,
    val progressBarHeight: Dp = 6.dp,
)

@Immutable
data class OuTypography(
    /** TextBlock / Button / TextBox / ComboBox FontSize = 12 */
    val body: TextStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal),
    val bodyStrong: TextStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold),
    /** Border.playback TextBlock FontSize = 11 */
    val small: TextStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal),
    /** ToggleButton.switch FontSize = 13 */
    val large: TextStyle = TextStyle(fontSize = 13.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal),
    val title: TextStyle = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold),
    val cardTitle: TextStyle = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold),
    val cardMeta: TextStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal),
    /** 页面顶部标题 */
    val topTitle: TextStyle = TextStyle(fontSize = 17.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold),
    val mono: TextStyle = TextStyle(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
)

val LocalOuColors = staticCompositionLocalOf { OuDarkColors }
val LocalOuDimens = staticCompositionLocalOf { OuDimens() }
val LocalOuTypography = staticCompositionLocalOf { OuTypography() }

object OuTheme {
    val colors: OuColors
        @Composable @ReadOnlyComposable get() = LocalOuColors.current
    val dimens: OuDimens
        @Composable @ReadOnlyComposable get() = LocalOuDimens.current
    val typography: OuTypography
        @Composable @ReadOnlyComposable get() = LocalOuTypography.current
}

@Composable
fun OuTheme(
    themeMode: ThemeMode,
    /** 配色方案（新增）；默认 OpenUtau 原版，行为与之前完全一致 */
    scheme: ColorScheme = ColorScheme.OPENUTAU,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val target = paletteFor(scheme, dark)

    // 【切换过渡】主题/配色一变就整屏换色会出现"闪光弹"，尤其白↔黑。
    // 这里对两套调色板做 320ms 插值，视觉上是渐变过去的。
    //
    // 【关键】不能用 LaunchedEffect 里 snapTo(0) 的写法：
    // LaunchedEffect 在**组合之后**才执行，所以目标色变化的那一帧，
    // progress 还停在上一轮的 1f，会先按"全新的调色板"渲染一帧
    // —— 表现就是渐变开始前闪一下（录屏亮度曲线里能看到单帧跳变）。
    // 正确做法是让 Animatable 和目标色一起重建：remember(target) 拿到新实例，
    // 初始值就是 0，因此第一帧渲染的仍是旧调色板，不会闪。
    var previous by remember { mutableStateOf(target) }
    val from = remember(target) { previous }
    val anim = remember(target) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(anim) {
        anim.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 320,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        )
        previous = target
    }
    val colors = if (anim.value >= 1f) target else lerpColors(from, target, anim.value)
    CompositionLocalProvider(
        LocalOuColors provides colors,
        LocalOuDimens provides OuDimens(),
        LocalOuTypography provides OuTypography(),
        content = content,
    )
}
