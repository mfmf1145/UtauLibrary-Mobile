package com.utaumarket.mobile.download

/**
 * GitHub 直连在国内经常完全不通（实测超时），这里按设置生成候选 URL 依次尝试。
 *
 * 实测（本机网络）：
 *   github.com 直连        → 连接超时
 *   https://gh-proxy.com/  → 200，约 190 KB/s
 *   https://ghproxy.net/   → 200，约 85 KB/s
 */
object UrlCandidates {

    const val MODE_AUTO = "auto"
    const val MODE_DIRECT = "direct"
    const val MODE_PROXY_FIRST = "proxy_first"
    const val MODE_CUSTOM = "custom"

    /** id -> (显示名, 前缀) */
    // 2026-10-13 实测（取 release 资产前 1KB，看是否返回 PK 头）：
    //   gh-proxy.com   206 + PK  ✅
    //   gh-proxy.org   206 + PK  ✅
    //   ghproxy.net    206 + PK  ✅（速度较慢）
    //   其余（ghfast.top / gh.llkk.cc / github.moeyy.xyz / gh.ddlc.top / ghproxy.cc / gh.xxooo.cf）全部超时、证书过期或返回推广页
    val BUILT_IN = listOf(
        Triple("gh-proxy.com", "gh-proxy.com（推荐）", "https://gh-proxy.com/"),
        Triple("gh-proxy.org", "gh-proxy.org", "https://gh-proxy.org/"),
        Triple("ghproxy.net", "ghproxy.net", "https://ghproxy.net/"),
    )

    /** 这条链接是否走的是 GitHub（也就是能不能用加速线路） */
    fun isGitHub(url: String): Boolean =
        url.contains("://github.com/") ||
            url.contains("://objects.githubusercontent.com/") ||
            url.contains("://codeload.github.com/") ||
            url.contains(".githubusercontent.com/")

    /**
     * @param mode [SettingsStore.ghMode]
     * @param custom 自定义前缀（MODE_CUSTOM 时使用）
     */
    fun build(url: String, mode: String, custom: String): List<String> {
        if (!isGitHub(url)) return listOf(url)
        val prefix = when (mode) {
            MODE_CUSTOM -> custom.trim().let { if (it.isEmpty()) "" else if (it.endsWith("/")) it else "$it/" }
            else -> BUILT_IN.firstOrNull { it.first == mode }?.third ?: ""
        }
        return when (mode) {
            MODE_DIRECT -> listOf(url)
            MODE_PROXY_FIRST, MODE_CUSTOM ->
                (listOfNotNull(prefix.ifBlank { null }?.plus(url)) + url).distinct()
            else -> // auto：先直连（连不上再换加速）
                (listOf(url) + BUILT_IN.map { it.third + url }).distinct()
        }
    }
}
