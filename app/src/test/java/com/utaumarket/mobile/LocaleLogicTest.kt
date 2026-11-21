package com.utaumarket.mobile

import com.utaumarket.mobile.core.AppLocale
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.ui.screens.humanSize
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * 纯 JVM 回归测试：这些逻辑以前出过错（界面是简体中文、译名却不显示），
 * 而且不需要真机就能验证。
 */
class LocaleLogicTest {

    private lateinit var originalDefault: Locale

    @Before
    fun setUp() {
        originalDefault = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalDefault)
        AppLocale.overrideTag = ""
    }

    // ------------------------------------------------ 语言判定

    @Test
    fun manualChineseWins_overEnglishSystemLocale() {
        // 复现线上问题：系统是英文，用户在设置里选了简体中文
        Locale.setDefault(Locale.ENGLISH)
        AppLocale.overrideTag = "zh-CN"
        assertTrue("手动选简体中文时必须判定为中文", AppLocale.isChinese())
    }

    @Test
    fun manualChineseTraditional_isAlsoChinese() {
        Locale.setDefault(Locale.ENGLISH)
        AppLocale.overrideTag = "zh-TW"
        assertTrue(AppLocale.isChinese())
    }

    @Test
    fun manualJapanese_noChineseTranslation() {
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        AppLocale.overrideTag = "ja"
        assertFalse("手动选日文时不能显示中文译名", AppLocale.isChinese())
    }

    @Test
    fun followSystem_usesProcessLocale() {
        AppLocale.overrideTag = ""
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE)
        assertTrue(AppLocale.isChinese())
        Locale.setDefault(Locale.JAPANESE)
        assertFalse(AppLocale.isChinese())
    }

    // ------------------------------------------------ 名字回退

    private fun item(
        name: String = "重音テト",
        translated: String = "重音特ト",
        ja: String = "",
        en: String = "",
        zh: String = "",
    ) = ResourceItem(
        id = "x",
        kind = ResourceKind.VOICEBANK,
        name = name,
        nameTranslated = translated,
        nameJa = ja,
        nameEn = en,
        nameZh = zh,
    )

    @Test
    fun chinese_usesNameZhThenFallsBackToTranslated() {
        assertEquals("重音特ト", item().localizedName("zh-CN"))
        assertEquals("重音特ト2", item(zh = "重音特ト2").localizedName("zh"))
    }

    @Test
    fun englishAndJapanese_neverReturnChineseTranslation() {
        assertEquals("", item().localizedName("en"))
        assertEquals("", item().localizedName("ja"))
        assertEquals("Kasane Teto", item(en = "Kasane Teto").localizedName("en"))
        // name_ja 与原文相同 → 返回空串，避免同一个名字显示两遍
        assertEquals("", item(ja = "重音テト").localizedName("ja"))
        assertEquals("重音テト（新）", item(ja = "重音テト（新）").localizedName("ja"))
    }

    @Test
    fun identicalName_isNotRepeated() {
        assertEquals("", item(name = "重音テト", translated = "重音テト").localizedName("zh"))
    }

    @Test
    fun chineseTitle_showsRomanizedNameInBrackets() {
        // 线上真实数据：name_zh 由「かな→ローマ字」生成，形如 重音テト 単独音 -> 重音Teto 単独音
        val it = item(
            name = "重音テト 単独音",
            translated = "重音テト",
            zh = "重音Teto 単独音",
        )
        assertEquals("重音Teto 単独音", it.localizedName("zh-CN"))
        assertEquals("重音テト 単独音（重音Teto 単独音）", it.displayName(true))
        assertEquals("重音テト 単独音", it.displayName(false))
        // 英文界面不显示任何中文/罗马字补充
        assertEquals("", it.localizedName("en"))
    }

    // ------------------------------------------------ 安装进度（字节级）

    @Test
    fun byteTracker_reportsMonotonicProgressAndFinal() {
        val seen = mutableListOf<Pair<Long, Long>>()
        val t = com.utaumarket.mobile.saf.SafManager.ByteTracker(1000L) { done, total ->
            seen.add(done.toLong() to total.toLong())
        }
        t.add(300)
        t.add(300)   // 250ms 内会被节流掉
        t.finish()
        assertEquals("总长必须原样上报", 1000L, seen.last().second)
        assertEquals("finish 时上报累计值", 600L, seen.last().first)
        // 累计值单调不减
        seen.zipWithNext().forEach { (a, b) -> assertTrue("进度必须单调", b.first >= a.first) }
    }

    @Test
    fun byteTracker_zeroTotal_doesNotCrash() {
        val seen = mutableListOf<Pair<Long, Long>>()
        val t = com.utaumarket.mobile.saf.SafManager.ByteTracker(0L) { d, tot -> seen.add(d to tot) }
        t.add(0)
        t.finish()
        assertEquals(0L, seen.last().first)
        assertEquals(0L, seen.last().second)
    }

    // ------------------------------------------------ 体积格式化

    @Test
    fun humanSize_isStableAcrossLocales() {
        // 曾经用默认 Locale 格式化数字，切到某些语言会出现不同写法
        Locale.setDefault(Locale.GERMANY)
        assertEquals("4.5 MB", humanSize(4_718_592L))
        Locale.setDefault(Locale.JAPAN)
        assertEquals("4.5 MB", humanSize(4_718_592L))
        assertEquals("", humanSize(0))
        assertEquals("512 B", humanSize(512))
    }
}
