package com.utaumarket.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * 配色方案。
 *
 * 设计原则：**不改变 OpenUtau 的视觉语言**（0 圆角、1px 边框、12/11/13sp 字号、紧凑间距），
 * 只替换「色相 / 饱和度」这一层 —— 所以无论选哪套方案，看起来仍然是 OpenUtau，
 * 只是整体色调变了。
 *
 * 实现方式：以 OpenUtau 原版调色板为基准，对**强调色系**做 HSV 色相偏移，
 * 背景只做极轻微的色相偏移（保持原有的明度层次），
 * 而「警告红 / 成功绿」这类语义色**不参与偏移**，避免失去语义。
 */
enum class ColorScheme(
    val id: String,
    /** 强调色的目标色相（OpenUtau 原版是 207°） */
    val accentHue: Float,
    val accentSatMul: Float,
    /** 强调色的明度系数：<1 更沉稳，>1 更亮 */
    val accentValMul: Float,
    /** 背景的色相偏移（很小，只做氛围） */
    val bgHueShift: Float,
    val zh: String,
    val zhtw: String,
    val en: String,
    val ja: String,
    /**
     * 背景饱和度系数。<1 会把背景压成更中性的灰，
     * 用于模仿 Synthesizer V 那种"纯灰底 + 高饱和荧光色"的观感。
     */
    val bgSatMul: Float = 1f,
) {
    OPENUTAU("openutau", 207f, 1.00f, 1.00f, 0f, "OpenUtau 原版", "OpenUtau 原版", "OpenUtau", "OpenUtau"),
    // 樱花：降一点亮度，避免荧光粉
    SAKURA("sakura", 335f, 0.92f, 0.94f, 18f, "樱花", "櫻花", "Sakura", "桜"),
    // 抹茶：明显降饱和 + 降亮度，做出「茶」的沉稳绿
    MATCHA("matcha", 112f, 0.58f, 0.82f, -14f, "抹茶", "抹茶", "Matcha", "抹茶"),
    DEEPSEA("deepsea", 186f, 0.98f, 0.96f, -6f, "深海", "深海", "Deep Sea", "深海"),
    AMBER("amber", 34f, 0.95f, 0.96f, 10f, "琥珀", "琥珀", "Amber", "琥珀"),
    VIOLET("violet", 268f, 0.90f, 0.94f, 26f, "紫罗兰", "紫羅蘭", "Violet", "すみれ"),
    // 极简灰：饱和几乎为 0 且压暗，得到中性灰而不是白
    MONO("mono", 210f, 0.05f, 0.72f, 0f, "极简灰", "極簡灰", "Mono", "モノ"),
    /**
     * Synthesizer V：深灰底 + 荧光黄绿。
     *
     * 色值取自 SynthV 编辑器截图：强调色 #B2E36E（H≈85°），
     * 背景 #2B2B2B 是**几乎无彩**的灰 —— 所以这里除了换色相，
     * 还要把背景饱和度压到 0.18，才能得到 SynthV 那种冷灰而不是带色偏的底。
     */
    SYNTHV("synthv", 85f, 0.78f, 0.98f, 0f, "Synthesizer V", "Synthesizer V", "Synthesizer V", "Synthesizer V", bgSatMul = 0.18f),
    VOCALOID("vocaloid", 197f, 1.0f, 1.0f, 0f, "VOCALOID", "VOCALOID", "VOCALOID", "VOCALOID", bgSatMul = 0f),
    /**
     * 陶土 Clay：暖米白底 + 赤陶色强调。
     *
     * 强调 #D97757 / 背景 #FAF9F5 / 文字 #141413 / 辅助填充 #E3DACC。
     * 这是一套**本身就是亮色**的方案，所以恒定亮色（和 Synthesizer V、
     * VOCALOID 恒深色的道理一样）。
     */
    CLAY("clay", 16f, 0.75f, 0.95f, 0f, "陶土", "陶土", "Clay", "テラコッタ"),
    ;

    companion object {
        fun fromId(id: String?): ColorScheme =
            entries.firstOrNull { it.id.lowercase() == id?.lowercase() } ?: OPENUTAU
    }
}

// ---------------------------------------------------------------- 颜色工具

private fun Color.hsv(): FloatArray =
    FloatArray(3).also { android.graphics.Color.colorToHSV(this.toArgb(), it) }

private fun fromHsv(h: FloatArray): Color =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(
        ((h[0] % 360f) + 360f) % 360f,
        h[1].coerceIn(0f, 1f),
        h[2].coerceIn(0f, 1f),
    )))

/** 只改色相/饱和度，明度保持不变 */
private fun Color.rehue(targetHue: Float, satMul: Float, valMul: Float = 1f): Color {
    val h = hsv()
    h[0] = targetHue
    h[1] = (h[1] * satMul).coerceIn(0f, 1f)
    h[2] = (h[2] * valMul).coerceIn(0f, 1f)
    return fromHsv(h)
}

/** 只压饱和度，色相和明度不动（把背景压成中性灰） */
private fun Color.desat(mul: Float): Color {
    if (mul >= 1f) return this
    val h = hsv()
    h[1] = (h[1] * mul).coerceIn(0f, 1f)
    return fromHsv(h)
}

/** 轻微偏移色相（用于背景氛围），明度不动 */
private fun Color.shiftHue(deg: Float): Color {
    if (deg == 0f) return this
    val h = hsv()
    h[0] += deg
    return fromHsv(h)
}

// ---------------------------------------------------------------- 方案生成

/**
 * 把「强调色系」按方案重新着色；背景做很轻的色相偏移；
 * 语义色（accentColor2 粉 / accentColor3 红 / warning）保持原样。
 */
fun OuColors.recolored(scheme: ColorScheme): OuColors {
    if (scheme == ColorScheme.OPENUTAU) return this
    val h = scheme.accentHue
    val s = scheme.accentSatMul
    val v = scheme.accentValMul
    val bg = scheme.bgHueShift
    val bs = scheme.bgSatMul
    return copy(
        background = background.desat(bs).shiftHue(bg),
        backgroundPointerOver = backgroundPointerOver.desat(bs).shiftHue(bg),
        backgroundPressed = backgroundPressed.desat(bs).shiftHue(bg),
        backgroundDisabled = backgroundDisabled.desat(bs).shiftHue(bg),
        trackBackgroundAlt = trackBackgroundAlt.desat(bs).shiftHue(bg),
        border = border.shiftHue(bg * 0.5f),
        borderPointerOver = borderPointerOver.shiftHue(bg * 0.5f),
        accent = accent.rehue(h, s, v),
        accentLight1 = accentLight1.rehue(h, s, v),
        accentDark1 = accentDark1.rehue(h, s, v),
        accentColor1 = accentColor1.rehue(h, s, v),
        neutralAccent = neutralAccent.rehue(h, s * 0.5f, v),
        neutralAccentPointerOver = neutralAccentPointerOver.rehue(h, s * 0.5f, v),
        tickLine = tickLine.shiftHue(bg),
        barNumber = barNumber.shiftHue(bg),
    )
}

/**
 * Synthesizer V 的完整调色板。
 *
 * 这套方案不能靠"从 OpenUtau 原版偏移色相"得到 —— 用户给的是**精确色值**：
 *   背景 #2B2B2B / 强调 #A9D065 / 文字 #FFFFFF / 次要 #6B6B6B
 * 所以这里直接构造一份独立的 OuColors，而不是走 recolored()。
 */
private val SynthVDark = OuColors(
    isDark = true,
    background = Color(0xFF2B2B2B),
    // 悬停/按下比底色略亮，保持"浮起"的层次
    backgroundPointerOver = Color(0xFF383838),
    backgroundPressed = Color(0xFF444444),
    backgroundDisabled = Color(0xFF232323),
    // 【文字层次】纯白 #FFFFFF 在 #2B2B2B 上过亮，正文看着"发白刺眼"；
    // 而次要文字 #6B6B6B 对比不足，小字看着"不明显"。
    // 把正文压到 #EDEDED、次要文字提到 #A0A0A0，两级都落在舒服的对比区间。
    foreground = Color(0xFFEDEDED),
    foregroundPointerOver = Color(0xFFA9D065),
    foregroundPressed = Color(0xFFA9D065),
    foregroundDisabled = Color(0xFFA0A0A0),
    border = Color(0xFF3F3F3F),
    borderPointerOver = Color(0xFF7A7A7A),
    accent = Color(0xFFA9D065),
    accentLight1 = Color(0xFFC3EC85),
    accentDark1 = Color(0xFF86B03C),
    // 【选中态】之前用中性灰 #6B6B6B，半透明叠在 #2B2B2B 上是一块发灰的"脏白"，
    // 既不明显又和整体色调不搭。改成深橄榄绿 —— 半透明叠加后是"偏绿的选中块"，
    // 和荧光绿的强调色是一个色系，选中状态一眼能看出来。
    neutralAccent = Color(0xFF3F5222),
    neutralAccentPointerOver = Color(0xFF556D2E),
    accentColor1 = Color(0xFFA9D065),
    // 语义色保持不变：粉/红/警告需要跨方案一致
    accentColor2 = OuDarkColors.accentColor2,
    accentColor3 = OuDarkColors.accentColor3,
    tickLine = Color(0xFF4A4A4A),
    barNumber = Color(0xFFA0A0A0),
    finalPitch = OuDarkColors.finalPitch,
    trackBackgroundAlt = Color(0xFF323232),
    warning = OuDarkColors.warning,
)

/**
 * VOCALOID6 编辑器的完整调色板（精确色值，恒为深色）。
 *
 *   全局底色   #181818
 *   工具栏     #3A3A3A
 *   按钮/选项卡 #5B5B5B
 *   高亮       #00A4E4
 *   网格线     #2D2D2D
 *   文字       #CCCCCC
 */
private val VocaloidDark = OuColors(
    isDark = true,
    background = Color(0xFF181818),
    backgroundPointerOver = Color(0xFF2D2D2D),
    backgroundPressed = Color(0xFF3A3A3A),
    backgroundDisabled = Color(0xFF111111),
    foreground = Color(0xFFCCCCCC),
    foregroundPointerOver = Color(0xFF00A4E4),
    foregroundPressed = Color(0xFF00A4E4),
    // 次要文字从 #5B5B5B 提到 #909090：#181818 底上 #5B5B5B 对比只有 3:1，
    // 说明性小字几乎看不清。提到 #909090 后层级仍在，但能读。
    foregroundDisabled = Color(0xFF909090),
    border = Color(0xFF3A3A3A),
    borderPointerOver = Color(0xFF5B5B5B),
    accent = Color(0xFF00A4E4),
    accentLight1 = Color(0xFF3FC0F0),
    accentDark1 = Color(0xFF0079AB),
    neutralAccent = Color(0xFF5B5B5B),
    neutralAccentPointerOver = Color(0xFF6E6E6E),
    accentColor1 = Color(0xFF00A4E4),
    accentColor2 = OuDarkColors.accentColor2,
    accentColor3 = OuDarkColors.accentColor3,
    tickLine = Color(0xFF2D2D2D),
    barNumber = Color(0xFF909090),
    finalPitch = OuDarkColors.finalPitch,
    trackBackgroundAlt = Color(0xFF2D2D2D),
    warning = OuDarkColors.warning,
)

/**
 * 陶土 Clay 的完整调色板（精确色值，恒为亮色）。
 *
 *   强调 Clay    #D97757
 *   背景 Ivory   #FAF9F5
 *   文字 Slate   #141413
 *   填充 Oat     #E3DACC
 */
private val ClayLight = OuColors(
    isDark = false,
    background = Color(0xFFFAF9F5),
    // 悬停比底色略深一点，保持暖白层次
    backgroundPointerOver = Color(0xFFF2EFE7),
    // 按下用辅助填充色，和分隔线同族
    backgroundPressed = Color(0xFFE3DACC),
    backgroundDisabled = Color(0xFFF5F3ED),
    foreground = Color(0xFF141413),
    foregroundPointerOver = Color(0xFFD97757),
    foregroundPressed = Color(0xFFD97757),
    // 次要文字：Slate 混背景，保持偏暖的灰
    foregroundDisabled = Color(0xFF8A877E),
    // 分隔线 / 淡色区块统一用 Oat
    border = Color(0xFFE3DACC),
    borderPointerOver = Color(0xFFCFC3B1),
    accent = Color(0xFFD97757),
    accentLight1 = Color(0xFFE79777),
    accentDark1 = Color(0xFFB85C3C),
    neutralAccent = Color(0xFFE3DACC),
    neutralAccentPointerOver = Color(0xFFD6C9B6),
    accentColor1 = Color(0xFFD97757),
    // 语义色沿用亮色版，保证警告/错误仍然醒目
    accentColor2 = OuLightColors.accentColor2,
    accentColor3 = OuLightColors.accentColor3,
    tickLine = Color(0xFFE3DACC),
    barNumber = Color(0xFF8A877E),
    finalPitch = OuLightColors.finalPitch,
    trackBackgroundAlt = Color(0xFFF1EDE4),
    warning = OuLightColors.warning,
)

/** 保留一份亮色变体以备将来需要（当前不启用：SynthV 恒为深色） */
private val SynthVLight = SynthVDark.copy(
    isDark = false,
    // 亮色版：白底 + 深绿字，保证对比度
    background = Color(0xFFF7F7F7),
    backgroundPointerOver = Color(0xFFEDEDED),
    backgroundPressed = Color(0xFFE2E2E2),
    backgroundDisabled = Color(0xFFF0F0F0),
    foreground = Color(0xFF1F1F1F),
    foregroundPointerOver = Color(0xFF6F9A1F),
    foregroundPressed = Color(0xFF6F9A1F),
    foregroundDisabled = Color(0xFF9A9A9A),
    border = Color(0xFFD2D2D2),
    borderPointerOver = Color(0xFF9A9A9A),
    accent = Color(0xFF7FAC2E),
    accentLight1 = Color(0xFFA9D065),
    accentDark1 = Color(0xFF5F8420),
    neutralAccent = Color(0xFF9A9A9A),
    neutralAccentPointerOver = Color(0xFF808080),
    accentColor1 = Color(0xFF7FAC2E),
    tickLine = Color(0xFFC8C8C8),
    barNumber = Color(0xFF9A9A9A),
    trackBackgroundAlt = Color(0xFFEFEFEF),
)

/** 取某个方案在指定明暗下的调色板 */
fun paletteFor(scheme: ColorScheme, dark: Boolean): OuColors = when (scheme) {
    // Synthesizer V 是**始终深色**的方案：它模仿的是 SynthV 编辑器的深灰底，
    // 与系统亮/暗无关。之前跟随系统，亮色模式下变成白底，用户要的是同一种黑。
    ColorScheme.SYNTHV -> SynthVDark
    // VOCALOID 同样是恒深色的编辑器风格
    ColorScheme.VOCALOID -> VocaloidDark
    // 陶土本身就是暖亮色，不跟随系统明暗
    ColorScheme.CLAY -> ClayLight
    else -> (if (dark) OuDarkColors else OuLightColors).recolored(scheme)
}

/**
 * 两套调色板之间按 t（0..1）插值。
 *
 * 用于切换主题/配色时的过渡 —— 直接把颜色换掉会瞬间全屏变白或变黑，
 * 也就是用户说的「闪光弹」。逐个字段插值就能得到平滑的渐变。
 */
fun lerpColors(a: OuColors, b: OuColors, t: Float): OuColors {
    if (t <= 0f) return a
    if (t >= 1f) return b
    fun c(x: androidx.compose.ui.graphics.Color, y: androidx.compose.ui.graphics.Color) =
        androidx.compose.ui.graphics.lerp(x, y, t)
    return b.copy(
        background = c(a.background, b.background),
        backgroundPointerOver = c(a.backgroundPointerOver, b.backgroundPointerOver),
        backgroundPressed = c(a.backgroundPressed, b.backgroundPressed),
        backgroundDisabled = c(a.backgroundDisabled, b.backgroundDisabled),
        foreground = c(a.foreground, b.foreground),
        foregroundPointerOver = c(a.foregroundPointerOver, b.foregroundPointerOver),
        foregroundPressed = c(a.foregroundPressed, b.foregroundPressed),
        foregroundDisabled = c(a.foregroundDisabled, b.foregroundDisabled),
        border = c(a.border, b.border),
        borderPointerOver = c(a.borderPointerOver, b.borderPointerOver),
        accent = c(a.accent, b.accent),
        accentLight1 = c(a.accentLight1, b.accentLight1),
        accentDark1 = c(a.accentDark1, b.accentDark1),
        neutralAccent = c(a.neutralAccent, b.neutralAccent),
        neutralAccentPointerOver = c(a.neutralAccentPointerOver, b.neutralAccentPointerOver),
        accentColor1 = c(a.accentColor1, b.accentColor1),
        accentColor2 = c(a.accentColor2, b.accentColor2),
        accentColor3 = c(a.accentColor3, b.accentColor3),
        tickLine = c(a.tickLine, b.tickLine),
        barNumber = c(a.barNumber, b.barNumber),
        finalPitch = c(a.finalPitch, b.finalPitch),
        trackBackgroundAlt = c(a.trackBackgroundAlt, b.trackBackgroundAlt),
        warning = c(a.warning, b.warning),
    )
}
