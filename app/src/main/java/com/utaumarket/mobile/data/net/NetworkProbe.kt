package com.utaumarket.mobile.data.net

import com.utaumarket.mobile.core.L
import com.utaumarket.mobile.data.model.NetworkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * 实测每条直链的连通性，徽章不再靠清单里写死的标记。
 *
 * 几条重要经验（都是踩过坑的）：
 * 1. 必须探**真实资源 URL**，不能只探根域名 —— github.com 根域能通，但 release 资产经常下不动；
 * 2. **按站点只探一条**就够了。之前对 120 条链接同时探测把连接打满，大量请求超时被误判成"无法访问"；
 * 3. **超时 ≠ 无法访问**。超时只说明慢，应该显示"直连缓慢"；只有 DNS 失败 / 连接被拒 /
 *    SSL 错误 / HTTP 4xx-5xx 这些明确失败才算"国内无法访问"。
 */
object NetworkProbe {

    data class Result(val status: NetworkStatus, val latencyMs: Long, val at: Long)

    private val _statuses = MutableStateFlow<Map<String, NetworkStatus>>(emptyMap())
    /** url(小写) -> 状态；界面订阅它实时更新徽章 */
    val statuses: StateFlow<Map<String, NetworkStatus>> = _statuses.asStateFlow()

    private val details = java.util.concurrent.ConcurrentHashMap<String, Result>()

    /**
     * 探测时顺手拿到的文件大小（Content-Range / Content-Length）。
     * 清单里有些插件/音素器没有 file_size，界面就用这里补上 ——
     * 用户反馈"插件和音素器不显示大小"，根因是清单缺数据。
     */
    private val _sizes = MutableStateFlow<Map<String, Long>>(emptyMap())
    val sizes: StateFlow<Map<String, Long>> = _sizes.asStateFlow()

    /**
     * 只按 URL 精确查。
     *
     * 【踩坑】之前还按 host 兜底，结果 GitHub 上所有插件共用一个 host，
     * 全都显示成第一条被探测到的资源大小（用户看到"每个插件都是 94.1 MB"）。
     * 大小是"每个文件"的属性，不能按站点复用。
     */
    fun sizeFor(url: String): Long = _sizes.value[url.lowercase()] ?: 0L

    /**
     * 按需补一个文件大小（清单里没有、探测也没覆盖到的条目）。
     *
     * GitHub 直连在国内不通，所以按加速线路依次试；只发 Range: bytes=0-0（1 字节），
     * 从 Content-Range 的末尾总长拿到文件大小，代价极小。
     */
    private val fetchScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )
    /** 补大小专用的短超时客户端：25 秒的探测超时会让卡片一直空着 */
    private val sizeClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    /** 失败过的 URL 别再反复重试（否则每次重组都会再发一轮请求） */
    private val sizeFailed = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )
    private val fetching = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    fun requestSize(url: String) {
        val key = url.lowercase()
        if (key.isBlank() || _sizes.value.containsKey(key) || sizeFailed.contains(key) || !fetching.add(key)) return
        fetchScope.launch {
            try {
                val candidates = com.utaumarket.mobile.download.UrlCandidates.build(url, "auto", "")
                for (u in candidates) {
                    val total = runCatching {
                        val req = okhttp3.Request.Builder()
                            .url(u)
                            .header("User-Agent", "UtauMarket-Mobile/1.0")
                            .header("Range", "bytes=0-0")
                            .build()
                        sizeClient.newCall(req).execute().use { resp ->
                            val cr = resp.header("Content-Range")
                            when {
                                cr != null && cr.contains('/') ->
                                    cr.substringAfterLast('/').trim().toLongOrNull() ?: 0L
                                resp.code == 200 ->
                                    resp.body?.contentLength()?.takeIf { it > 0 } ?: 0L
                                else -> 0L
                            }
                        }
                    }.getOrDefault(0L)
                    if (total > 0) {
                        _sizes.value = _sizes.value + (key to total)
                        return@launch
                    }
                }
                sizeFailed.add(key)   // 全部候选都没拿到大小，记下来别再来一遍
            } finally {
                fetching.remove(key)
            }
        }
    }

    // 超时给到 25 秒：kasaneteto.jp 实测要 13 秒才能回，之前 12 秒会把"慢但能用"误判成不通。
    // 反过来，25 秒都拿不到任何东西，那就是真的连不上 —— 判红。
    private val client: okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host ?: "" }.getOrDefault("").lowercase()

    /**
     * 🔴 国内基本连不上、也不做探测的站点（用户要求：Google Drive / Dropbox 标红）。
     * 这些站点的页面本身可能打得开，但下载基本不可用，标红符合用户预期。
     */
    private val BLOCKED_HOSTS = listOf(
        "drive.google.com", "drive.usercontent.google.com", "docs.google.com",
        "dropbox.com", "dropboxusercontent.com",
        "mega.nz", "1drv.ms", "onedrive.live.com",
        // 百度网盘 / 蓝奏云 / 夸克 等**国内网盘是能访问的**，只是需要登录或跳转，
        // 不能放进"国内无法访问"名单；它们由 platformOf() 显示成「到 XXX 下载」。
    )

    /**
     * 国外网盘：**国内直连通常不通，但开代理/VPN 就能下**。
     * 以前直接把 mediafire / dropbox / drive 一律判成「国内无法直连」（红），
     * 用户明明挂着 VPN 或者链接本来就能用，却看到红叉 —— 这是错的。
     * 现在改成：默认黄（需代理），并且**真的去探测**，通了就变绿。
     */
    /** 🟡 国外网盘但国内多数地区能连（只是慢）：MediaFire —— 单独标黄，不判红 */
    private val NEEDS_PROXY_HOSTS = listOf(
        "mediafire.com",
    )

    fun isBlockedHost(host: String): Boolean = BLOCKED_HOSTS.any { host.contains(it) }
    fun needsProxy(host: String): Boolean = NEEDS_PROXY_HOSTS.any { host.contains(it) }

    fun statusFor(url: String): NetworkStatus? {
        val key = url.lowercase()
        _statuses.value[key]?.let { return it }
        val h = hostOf(url)
        if (h.isEmpty()) return null
        _statuses.value["host:" + h]?.let { return it }
        // 需要登录/客户端的站：确实下不了，直接红
        if (isBlockedHost(h)) return NetworkStatus.RED
        // 国外网盘（MediaFire / Dropbox / Drive）：**不做探测**，直接标「需代理」。
        // 原因：① 逐个解析这些链接极慢（每个要 2-3 次请求），拖慢整个探测；
        //      ② 解析结果随 VPN 状态变化，显示"直连可用/无法访问"都会误导用户。
        // 实际能不能下取决于用户自己开不开代理，如实告诉他"需代理"最准确。
        if (needsProxy(h)) return NetworkStatus.YELLOW
        // GitHub 系：直连不通但**加速线路可用**，下载一直是好的。
        // 探测没跑到之前就显示"未探测"会让人以为不能用（用户反馈），
        // 所以这里直接给绿灯，并在 note 里说明走的是加速线路。
        if (h.contains("github") || h.contains("githubusercontent")) {
            return NetworkStatus.GREEN
        }
        // 其它还没测过的返回 null（界面退回清单里的标记）
        return null
    }

    /**
     * 手动刷新。
     *
     * 【关键改动】不再清空 _statuses —— 之前一刷新界面全部变回"未探测"，
     * 要等几十个站点逐个测完才恢复，用户看到的就是"点了刷新也没用"。
     * 现在保留旧结果，新结果边测边覆盖。
     */
    fun invalidate() {
        details.clear()
        _sizes.value = emptyMap()
    }

    fun lastProbeAt(): Long = details.values.maxOfOrNull { it.at } ?: 0L

    private fun rank(s: NetworkStatus): Int = when (s) {
        NetworkStatus.GREEN -> 3
        NetworkStatus.YELLOW -> 2
        NetworkStatus.PROXY -> 1
        NetworkStatus.UNKNOWN -> 1
        NetworkStatus.RED -> 0
    }

    /**
     * 并发实测。
     *
     * 【关键修正】以前"同一个 host 只探一条 URL"，而且是清单里**第一条**。
     * 于是同站点上只要有一条死链（404），整个 host 被判红，defoko 这种明明能下的
     * 也被标成"国内无法访问"。现在：
     * 1. 每个 host 取最多 3 条候选，第一条判红就换下一条；
     * 2. 拿到 HTTP 响应（哪怕 4xx）就算"站点可达"，不再等于"被墙"；
     * 3. host 状态取该 host 所有候选里**最好**的那个。
     */
    /** 是否正在探测（界面据此把"未探测"显示成"探测中"） */
    private val _probing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val probing: kotlinx.coroutines.flow.StateFlow<Boolean> = _probing

    suspend fun probeAll(urls: Collection<String>, concurrency: Int = 8): Map<String, NetworkStatus> =
        withContext(Dispatchers.IO) {
            _probing.value = true
            try {
            val perHost = LinkedHashMap<String, MutableList<String>>()
            for (u in urls) {
                if (u.isBlank()) continue
                val h = hostOf(u)
                if (h.isEmpty()) continue
                // 网盘主机不探测（状态固定为"需代理"），省下大量时间
                if (needsProxy(h)) continue
                // GitHub 也不探测：直连本来就不通，但**加速线路一直可用**，
                // 之前把它放进探测池，探出红叉后覆盖了 GREEN，导致插件/音素器明明是好的却显示无法访问。
                if (h.contains("github") || h.contains("githubusercontent")) continue
                val list = perHost.getOrPut(h) { ArrayList() }
                if (list.size < 5 && !list.contains(u.trim())) list.add(u.trim())
            }
            if (perHost.isEmpty()) return@withContext emptyMap()
            L.d("开始探测 ${perHost.size} 个站点")

            val hosts = perHost.keys.toList()
            for (chunk in hosts.chunked(concurrency.coerceAtLeast(1))) {
                val results = coroutineScope {
                    chunk.map { h -> async { h to probeHost(perHost[h].orEmpty()) } }.awaitAll()
                }
                val merged = _statuses.value.toMutableMap()
                results.forEach { (h, pr) ->
                    merged["host:" + h] = pr.hostStatus
                    pr.perUrl.forEach { (u, res) ->
                        details[u.lowercase()] = res
                        merged[u.lowercase()] = res.status
                    }
                }
                _statuses.value = merged
            }
            _statuses.value
            } finally {
                _probing.value = false
            }
        }

    private class HostProbe(
        val hostStatus: NetworkStatus,
        val perUrl: Map<String, Result>,
    )

    private fun probeHost(candidates: List<String>): HostProbe {
        if (candidates.isEmpty()) return HostProbe(NetworkStatus.UNKNOWN, emptyMap())
        // 需要登录/客户端的站：探测也没用，直接红，省下时间和流量
        val host = candidates.firstOrNull()?.let { hostOf(it) } ?: ""
        if (host.isNotEmpty() && isBlockedHost(host)) {
            return HostProbe(NetworkStatus.RED, emptyMap())
        }
        val perUrl = LinkedHashMap<String, Result>()
        var best: Result? = null
        for (u in candidates) {
            val res = probeOne(u)
            perUrl[u] = res
            if (best == null || rank(res.status) > rank(best.status)) best = res
            // 已经是绿的就没必要再试别的
            if (best.status == NetworkStatus.GREEN) break
        }
        val finalRes = best ?: Result(NetworkStatus.UNKNOWN, 0, System.currentTimeMillis())
        return HostProbe(finalRes.status, perUrl)
    }

    private fun probeOne(rawUrl: String): Result {
        // 网盘链接不会走到这里（probeAll 已经跳过），这里只测普通站点
        val url = rawUrl
        val t0 = System.currentTimeMillis()
        var status = NetworkStatus.UNKNOWN
        var knownSize = 0L
        var pageOnly = false   // 只拿到 HTML 落地页，拿不到真正的文件
        repeat(2) { attempt ->
            if (status == NetworkStatus.GREEN) return@repeat
            try {
                // 拉首尾各一小段，并校验 Content-Length。
                // 之前只发 Range 头、拿到 200 就判"可用" —— 但服务器回 200 不代表
                // 真的能下完（有的会先给页头、有的中途断流），于是出现"显示可下载却连不上"。
                var ok = false
                var rangeRefused = false
                // 先用 64KB：够大不会被"拒绝极小 Range"的 CDN 挡（bytes=0-0 会 403），
                // 又够小不会浪费流量。且下面只读前 16 字节，不会真下完。
                for ((start, end) in listOf(0L to 65535L, -65536L to -1L)) {
                    val r = okhttp3.Request.Builder()
                        .url(url)
                        .header("User-Agent", "UtauMarket-Mobile/1.0")
                        .header("Range", "bytes=" + (if (start < 0) "" else start.toString()) +
                            "-" + (if (end < 0) "" else end.toString()))
                        .build()
                    client.newCall(r).execute().use { resp ->
                        // 只要拿到任何 HTTP 响应，就说明这个站点是通的
                        // 顺手记下文件总大小：Content-Range 的 "/总长" 或 200 的 Content-Length
                        runCatching {
                            val cr = resp.header("Content-Range")
                            val total = when {
                                cr != null && cr.contains('/') ->
                                    cr.substringAfterLast('/').trim().toLongOrNull() ?: 0L
                                resp.code == 200 -> resp.body?.contentLength()?.takeIf { it > 0 } ?: 0L
                                else -> 0L
                            }
                            if (total > 0) knownSize = total
                        }
                        // 【兼容"拒绝 Range"的服务器】有的 CDN 对带 Range 的请求直接回 403，
                        // 但对普通 GET 正常（欲音ルコ官方 CloudFront 就是这样）。
                        // 这类站点不能被判死，否则用户挂着 VPN 也看到"无法连接"。
                        if (resp.code == 403 || resp.code == 400 || resp.code == 416) {
                            rangeRefused = true
                        }
                        when {
                            resp.code in 200..299 -> {
                                val ct = resp.header("Content-Type")?.lowercase() ?: ""
                                val body = resp.body
                                val len = body?.contentLength() ?: -1L
                                // 【关键】网盘落地页（MediaFire / Dropbox / Drive）会返回 200 + HTML，
                                // 旧逻辑只要"有内容"就判定可下载 —— 于是真正下不了的也显示「直连缓慢」。
                                // 现在：HTML 页面一律不算"直连可用"，交给 LinkResolver 解析后另说。
                                val isHtml = ct.contains("text/html") || ct.contains("application/xhtml")
                                when {
                                    isHtml -> {
                                        // 页面能打开 ≠ 文件能下，但**网盘落地页例外**：
                                        // MediaFire / Dropbox / Drive 返回的就是 HTML，
                                        // 下载时 LinkResolver 会把它解析成真直链。
                                        // 所以这类站点收到 HTML 只说明"连得上"，判黄而不是判红。
                                        // 走到这里说明 URL 已经是解析后的直链（网盘）
                                        // 或者普通站点。返回网页 = 拿不到文件 = 红。
                                        pageOnly = true
                                    }
                                    len == 0L -> {}
                                    // 【关键】只读前 16 字节就够判断"有内容"，绝不能 bytes() 读完 ——
                                    // Range 是 1MB 时读完等于每个站都下 1MB，700 多个站直接把网络占满。
                                    body != null && runCatching {
                                        val ins = body.byteStream()
                                        val buf = ByteArray(16)
                                        ins.read(buf) > 0
                                    }.getOrDefault(false) -> ok = true
                                }
                            }
                            // 服务器说"不认 Range"：文件本身可能是好的，只是没法校验
                            resp.code == 405 || resp.code == 416 -> rangeRefused = true
                            // 【403】可能是"拒绝 Range"而不是"文件没了"——
                            // 换一次不带 Range 的 GET 再确认（欲音ルコ官方 CDN 就是这种）。
                            // 通了就判黄（只能单连接下载），真 403 才判红。
                            resp.code == 403 -> {
                                val plainOk = runCatching {
                                    okhttp3.Request.Builder()
                                        .url(url)
                                        .header("User-Agent", "UtauMarket-Mobile/1.0")
                                        .build()
                                        .let { req -> client.newCall(req).execute() }
                                        .use { r2 ->
                                            val ok2 = r2.code in 200..299
                                            if (ok2) {
                                                runCatching { r2.body?.byteStream()?.read() }
                                            }
                                            ok2
                                        }
                                }.getOrDefault(false)
                                if (plainOk) rangeRefused = true
                            }
                            else -> {}
                        }
                    }
                    if (ok) break
                }
                val ms = System.currentTimeMillis() - t0
                status = when {
                    ok -> if (ms < 2500) NetworkStatus.GREEN else NetworkStatus.YELLOW
                    // 只有 HTML 落地页：直连拿不到文件，如实判红
                    pageOnly -> NetworkStatus.RED
                    rangeRefused -> NetworkStatus.YELLOW
                    // 只拿到 403/404/错误页 = 下不动，就是"无法访问"。
                    // （上一版把"有 HTTP 响应"也算可达，结果 403 的 CloudFront 链接显示成"缓慢"）
                    else -> NetworkStatus.RED
                }
            } catch (t: Throwable) {
                status = when (t) {
                    // 这些是"明确失败"：域名解析不了、连接被拒、SSL 被拦
                    is java.net.UnknownHostException,
                    is java.net.ConnectException,
                    is javax.net.ssl.SSLException -> NetworkStatus.RED
                    // 25 秒超时（而且已经重试过一次）说明是真的连不上，判红。
                    // 能"慢但通"的站点（例如 kasaneteto 的 13 秒）此时已经拿到响应了。
                    is java.net.SocketTimeoutException -> NetworkStatus.RED
                    else -> NetworkStatus.YELLOW
                }
                L.d("探测 $url 第 ${attempt + 1} 次：${t.javaClass.simpleName} -> $status")
            }
        }
        if (knownSize > 0) {
            _sizes.value = _sizes.value + (url.lowercase() to knownSize)
        }
        return Result(status, System.currentTimeMillis() - t0, System.currentTimeMillis())
    }
}
