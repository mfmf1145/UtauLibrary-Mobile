package com.utaumarket.mobile.core

/**
 * 界面语言的唯一判定入口。
 *
 * 为什么需要它：`MainActivity.attachBaseContext` 只用 `createConfigurationContext`
 * 换了资源语言，**进程默认 Locale 仍然是系统语言**。于是「简体中文界面下要不要显示
 * 中文译名」这类判断（`isChineseLocale()`）会跟着系统语言走，出现"界面是简体中文、
 * 译名却不显示"的错位。
 *
 * 规则：
 * - 用户在设置里手动指定了语言 → 就以它为准；
 * - 「跟随系统」→ 以进程 Locale 为准（系统给了什么就用什么）。
 */
object AppLocale {

    /** 设置里手动指定的语言 tag；空串 = 跟随系统 */
    @Volatile
    var overrideTag: String = ""

    /** 当前界面实际使用的语言代码（zh / ja / en …），用于语言相关的内容判断 */
    val effectiveLanguage: String
        get() = if (overrideTag.isNotBlank()) {
            java.util.Locale.forLanguageTag(overrideTag).language
        } else {
            java.util.Locale.getDefault().language
        }

    /** 中文界面（简体或繁体）时，才显示清单里的中文译名 */
    fun isChinese(): Boolean = effectiveLanguage.equals("zh", ignoreCase = true)
}
