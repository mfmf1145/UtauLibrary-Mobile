package com.utaumarket.mobile.download

import com.utaumarket.mobile.core.L
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 把"网盘落地页"解析成可下载的直链。
 *
 * 为什么需要它：UTAU 社区的长尾声库绝大多数放在 MediaFire / Dropbox，
 * 而落地页是 HTML，不是压缩包。如果不解析，这些声库根本下不动，
 * 而事先批量解析会被限流（实测并发解析 100 多个链接后全部失败）。
 * 放到下载时按需解析就没有这个问题 —— 用户一次只下一个文件。
 */
object LinkResolver {

    private const val UA = "UtauMarket-Mobile/1.0"

    private val resolverClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** 这条链接是不是需要解析的网盘落地页 */
    fun needsResolve(url: String): Boolean = hostOf(url).let {
        it.contains("mediafire.com") || it.contains("dropbox.com") || it.contains("1drv.ms")
    }

    private fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host ?: "" }.getOrDefault("").lowercase()

    /**
     * 返回可直接下载的 URL；解析不出来就返回原地址（让上层照常报错）。
     */
    fun resolve(url: String, client: OkHttpClient = resolverClient): String {
        val host = hostOf(url)
        return try {
            when {
                host.contains("mediafire.com") -> resolveMediaFire(url, client)
                host.contains("dropbox.com") -> resolveDropbox(url)
                else -> url
            }
        } catch (t: Throwable) {
            L.w("解析直链失败: $url", t)
            url
        }
    }

    private fun resolveMediaFire(url: String, client: OkHttpClient): String {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        val html = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return url
            resp.body?.string() ?: return url
        }
        // 页面里形如 href="https://download1234.mediafire.com/.../xxx.zip"
        val m = Regex("""(https://download[0-9]*\.mediafire\.com/[^"\s]+)""").find(html)
        val direct = m?.groupValues?.getOrNull(1)
        return if (direct.isNullOrBlank()) {
            L.w("MediaFire 页面里没找到直链（可能被限流或文件已删除）")
            url
        } else {
            L.d("MediaFire 解析成功")
            direct
        }
    }

    private fun resolveDropbox(url: String): String {
        // dl=0 是预览页，dl=1 / raw=1 才是直接下载
        var u = url.replace("dl=0", "dl=1").replace("raw=0", "raw=1")
        if (!u.contains("dl=1") && !u.contains("raw=1")) {
            u = if (u.contains("?")) "$u&dl=1" else "$u?dl=1"
        }
        return u
    }
}
