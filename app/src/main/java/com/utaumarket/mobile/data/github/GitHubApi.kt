package com.utaumarket.mobile.data.github

import android.content.Context
import androidx.annotation.StringRes
import com.utaumarket.mobile.R
import com.utaumarket.mobile.core.L
import com.utaumarket.mobile.data.model.NetworkStatus
import com.utaumarket.mobile.data.model.PhonemizerLanguage
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.data.model.InstallRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 音素器来源：实时调用 GitHub Search API + Releases API。
 * 搜索关键词：openutau phonemizer / openutau topic。
 * 过滤掉 Source code.zip，只保留真正的 .dll 附件。
 *
 * 注意：未认证请求频率很低（search 10 req/min，core 60 req/h），支持用户填入自己的 Token。
 */
class GitHubApi(
    private val context: Context,
    private val client: OkHttpClient = defaultClient(),
) {

    /** 提示文案走资源文件（限流信息会直接显示在音素器页顶部） */
    private fun s(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    class RateLimitException(
        val remaining: Int,
        val resetAt: Long,
        message: String,
    ) : Exception(message)

    suspend fun searchPhonemizers(token: String, onProgress: (String) -> Unit = {}): List<ResourceItem> =
        withContext(Dispatchers.IO) {
            val repos = LinkedHashMap<String, JSONObject>()
            for (q in QUERIES) {
                onProgress(s(R.string.gh_progress_search, q))
                try {
                    val arr = searchRepos(q, token)
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val full = o.optString("full_name")
                        if (full.isNotBlank()) repos[full] = o
                    }
                } catch (t: Throwable) {
                    L.w("GitHub 搜索失败: $q", t)
                    if (t is RateLimitException) throw t
                }
            }

            val out = ArrayList<ResourceItem>()
            for ((full, repo) in repos) {
                onProgress(s(R.string.gh_progress_releases, full))
                val assets = try {
                    releaseAssets(full, token)
                } catch (t: Throwable) {
                    if (t is RateLimitException) throw t
                    emptyList()
                }
                val dllAssets = assets.filter { it.name.endsWith(".dll", true) }
                if (dllAssets.isEmpty()) continue
                val description = repo.optString("description")
                // 【分类修正】以前只要求"有 .dll 附件"，任何带 dll 的插件都被塞进音素器页。
                // 现在要求仓库名/描述/主题里确实出现音素器相关词，否则跳过。
                val haystack = (full + " " + description + " " + (repo.optJSONArray("topics")?.let { a ->
                    (0 until a.length()).joinToString(" ") { a.optString(it) }
                } ?: "")).lowercase()
                if (PHONEMIZER_HINTS.none { haystack.contains(it) }) {
                    L.d("跳过非音素器仓库: " + full)
                    continue
                }
                val stars = repo.optInt("stargazers_count")
                val topics = repo.optJSONArray("topics")?.let { a ->
                    (0 until a.length()).joinToString(",") { a.optString(it) }
                } ?: ""
                val language = guessLanguage(full, description, topics)
                for ((idx, asset) in dllAssets.withIndex()) {
                    val (assetName, size, downloadUrl, publishedAt) = asset
                    out += ResourceItem(
                        id = "gh:$full:$assetName",
                        kind = ResourceKind.PHONEMIZER,
                        name = if (dllAssets.size > 1) "$full / $assetName" else full,
                        nameTranslated = "",
                        author = full.substringBefore("/"),
                        engine = com.utaumarket.mobile.data.model.EngineType.OTHER,
                        language = language.id,
                        phonemizerLanguage = language,
                        url = downloadUrl,
                        network = NetworkStatus.UNKNOWN,
                        format = "dll",
                        fileSize = size,
                        version = "",
                        releaseDate = publishedAt,
                        sourcePlatform = "GitHub",
                        license = "",
                        description = description,
                        remark = if (idx == 0) s(R.string.gh_remark_release, full) else "",
                        installRule = InstallRule.PHONEMIZER_DLL,
                        fileName = assetName,
                        mobileWarning = s(R.string.gh_mobile_dll_warning),
                        detailUrl = "https://github.com/$full",
                        stars = stars,
                    )
                }
            }
            out.sortedWith(compareByDescending<ResourceItem> { it.stars }.thenBy { it.name })
        }

    private fun searchRepos(q: String, token: String): org.json.JSONArray {
        val url = "https://api.github.com/search/repositories?q=${enc(q)}&sort=stars&order=desc&per_page=40"
        val obj = getJsonObject(url, token)
        return obj.optJSONArray("items") ?: org.json.JSONArray()
    }

    /** 返回 (assetName, size, downloadUrl, publishedAt) */
    private fun releaseAssets(fullName: String, token: String): List<Quad> {
        val url = "https://api.github.com/repos/$fullName/releases?per_page=10"
        val arr = getJsonArray(url, token)
        val out = ArrayList<Quad>()
        for (i in 0 until arr.length()) {
            val rel = arr.optJSONObject(i) ?: continue
            val published = rel.optString("published_at")
            val assets = rel.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val a = assets.optJSONObject(j) ?: continue
                val name = a.optString("name")
                if (name.isBlank()) continue
                out += Quad(name, a.optLong("size"), a.optString("browser_download_url"), published)
            }
        }
        return out
    }

    private fun getJsonObject(url: String, token: String): JSONObject =
        JSONObject(requestBody(url, token))

    private fun getJsonArray(url: String, token: String): org.json.JSONArray =
        org.json.JSONArray(requestBody(url, token))

    private fun requestBody(url: String, token: String): String {
        val b = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "UtauMarket-Mobile")
        if (token.isNotBlank()) b.header("Authorization", "Bearer $token")
        client.newCall(b.build()).execute().use { resp ->
            if (resp.code == 403 || resp.code == 429) {
                val remaining = resp.header("X-RateLimit-Remaining")?.toIntOrNull() ?: 0
                val reset = resp.header("X-RateLimit-Reset")?.toLongOrNull()?.times(1000) ?: 0L
                // 重置时间转成本地可读时间，直接显示给用户
                val resetText = if (reset > 0L) {
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(reset))
                } else {
                    "-"
                }
                throw RateLimitException(remaining, reset, s(R.string.gh_rate_limited, remaining, resetText))
            }
            return resp.body?.string() ?: "null"
        }
    }

    private fun guessLanguage(full: String, description: String, topics: String): PhonemizerLanguage {
        val s = "$full $description $topics".lowercase()
        return when {
            s.contains("japanese") || s.contains("ja-") || s.contains("ja_") || s.contains(" kana") -> PhonemizerLanguage.JA
            s.contains("chinese") || s.contains("mandarin") || s.contains("zh-") || s.contains("pinyin") -> PhonemizerLanguage.ZH
            s.contains("english") || s.contains("en-") || s.contains("arpabet") -> PhonemizerLanguage.EN
            else -> PhonemizerLanguage.OTHER
        }
    }

    private data class Quad(val name: String, val size: Long, val url: String, val publishedAt: String)

    companion object {

        /** 仓库里出现这些词才算音素器，避免把普通插件也算进来 */
        private val PHONEMIZER_HINTS = listOf(
            "phonemizer", "phoneme", "g2p", "grapheme", "romaniz", "romaji",
            "phonetic", "音素", "字典", "发音", "発音",
        )

        // 搜索词数量要克制：GitHub 未认证的搜索接口每个 IP 每分钟只有 10 次，
        // 而且后面还要为每个仓库查 release。之前写了 11 条，直接把配额打满、
        // 整个音素器列表变成空的。收敛到 5 条，覆盖面仍然够。
        val QUERIES = listOf(
            "openutau phonemizer",
            "openutau plugin",
            "topic:openutau",
            "openutau phoneme in:readme",
            "diffsinger phonemizer",
        )

        fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
