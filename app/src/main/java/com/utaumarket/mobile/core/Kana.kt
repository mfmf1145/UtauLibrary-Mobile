package com.utaumarket.mobile.core

/**
 * 假名 → 罗马音（平文式，简化）。
 *
 * 【为什么需要】清单里的歌手名大量是日文，用户却常按罗马音搜索
 * （搜 "teto" 找「重音テト」、搜 "kasane" 找「重音」）。
 * 别名里未必覆盖所有写法，这里做一次机械转换，搜索时两边都能命中。
 *
 * 实现只做**平假名/片假名到罗马音的逐音节替换**，不处理汉字读音
 * （那需要辞书，体积不允许）。
 */
object Kana {

    /** 两拍以上的组合音（清音 + 拗音）优先匹配 */
    private val DIGRAPHS: Map<String, String> = mapOf(
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        "てぃ" to "ti", "でぃ" to "di", "ふぁ" to "fa", "ふぃ" to "fi",
        "ふぇ" to "fe", "ふぉ" to "fo", "うぃ" to "wi", "うぇ" to "we",
        "ゔぁ" to "va", "ゔぃ" to "vi", "ゔぇ" to "ve", "ゔぉ" to "vo",
    )

    private val MONO: Map<String, String> = mapOf(
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "ゐ" to "i", "ゑ" to "e", "を" to "o", "ん" to "n",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        "ゔ" to "vu",
        "ぁ" to "a", "ぃ" to "i", "ぅ" to "u", "ぇ" to "e", "ぉ" to "o",
        "ゃ" to "ya", "ゅ" to "yu", "ょ" to "yo", "っ" to "",
    )

    /** 片假名 → 平假名（同一个音） */
    private fun toHiragana(s: String): String = buildString(s.length) {
        for (ch in s) {
            val c = ch.code
            append(if (c in 0x30A1..0x30F6) (c - 0x60).toChar() else ch)
        }
    }

    /**
     * 把字符串里的假名转成罗马音，其余字符（汉字、拉丁字母、数字）原样保留。
     * 结果里会去掉空格，方便直接做 contains 匹配。
     */
    fun toRomaji(input: String): String {
        if (input.isBlank()) return ""
        val s = toHiragana(input)
        val sb = StringBuilder(s.length * 2)
        var i = 0
        while (i < s.length) {
            // 促音：っ + 下一个辅音 → 双写辅音
            if (s[i] == 'っ' && i + 1 < s.length) {
                val next = if (i + 2 <= s.length) s.substring(i + 1, minOf(i + 3, s.length)) else ""
                val roma = DIGRAPHS[next] ?: MONO[s[i + 1].toString()]
                if (roma != null && roma.isNotEmpty() && roma[0].isLetter()) sb.append(roma[0])
                i += 1
                continue
            }
            // 长音符 ー：把前一个元音重复一次
            if (s[i] == 'ー') {
                val last = sb.lastOrNull()
                if (last != null && "aeiou".contains(last)) sb.append(last)
                i += 1
                continue
            }
            val two = if (i + 2 <= s.length) s.substring(i, i + 2) else ""
            val d = DIGRAPHS[two]
            if (d != null) { sb.append(d); i += 2; continue }
            val m = MONO[s[i].toString()]
            if (m != null) { sb.append(m); i += 1; continue }
            sb.append(s[i]); i += 1
        }
        return sb.toString()
    }

    /** 是否含有假名（用来判断"这条是否值得参与罗马音匹配"） */
    fun hasKana(input: String): Boolean = input.any { ch ->
        val c = ch.code
        c in 0x3041..0x3096 || c in 0x30A1..0x30FA
    }
}
