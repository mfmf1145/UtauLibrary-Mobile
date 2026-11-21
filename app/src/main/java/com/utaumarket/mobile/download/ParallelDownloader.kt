package com.utaumarket.mobile.download

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.utaumarket.mobile.R
import com.utaumarket.mobile.core.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicLong

/**
 * 下载器：**按站点自适应**选择单连接还是多线程分块。
 *
 * 同一台手机实测（2026-10-11），不同站点表现完全相反：
 *
 * | 来源 | 单连接 | 4 线程 |
 * | --- | --- | --- |
 * | gh-proxy（GitHub 加速） | 278 KB/s | 1192 KB/s（4.3×） |
 * | zunko.jp | 282 KB/s | 840 KB/s（3.0×） |
 * | canon-voice.com | 115 KB/s | 845 KB/s（7.3×） |
 * | kasaneteto.jp | 803 KB/s | 70 KB/s（0.09×，惩罚多连接） |
 *
 * 所以默认用多线程；某个站点试跑后明显不划算，就**记住**它，以后直接走单连接。
 *
 * 分块实现上的两个要点（上一版就是在这里翻车的）：
 *  1. **绝不预分配文件长度**——旧版 setLength(total) 让文件长度等于总大小但内容是空洞，
 *     导致进度条失真、暂停后被当成"已下完"从而解压失败；
 *  2. 断点续传靠**旁路 meta 文件**逐块记录进度，不靠文件长度。
 */
object ParallelDownloader {

    enum class Stop { NONE, PAUSED, CANCELED }

    data class Result(
        val ok: Boolean,
        val totalBytes: Long,
        val stopped: Stop = Stop.NONE,
        val message: String = "",
        /** 试跑发现多线程不划算，已回退并要求调用方按单连接重来 */
        val abortedSlow: Boolean = false,
    )

    private const val USER_AGENT = "UtauMarket-Mobile/1.0"
    private const val BUFFER_SIZE = 256 * 1024
    private const val MIN_CHUNKED_BYTES = 512L * 1024

    /** 每块最小字节数。调小一点，让中小体积的声库也能吃满并发。 */
    // 每块最小 64KB：之前是 256KB，小包（9MB）配 48 线程时会被判定"不值得分块"，
    // 直接退回单连接 → 速度只有 1 MB/s。降低后小包也能并行。
    private const val MIN_CHUNK_BYTES = 64L * 1024
    /** 分块布局固定 1 MiB 一块，与并发数无关（改了并发数也能接着下） */
    private const val CHUNK_BYTES = 1024L * 1024
    private const val MAX_CHUNKS = 8192
    private const val MAX_RETRY = 4

    /**
     * 试跑窗口。
     *
     * 注意：**不能按速度定门槛**。之前设的是"4 秒拿不到 400KB 就回退单连接"，
     * 在慢网（实测这条网只有 2–3 KB/s）下每个站点都达不到，结果一律被降级成单连接，
     * 反而比多线程更慢。现在只在**一个字节都没收到**时才认为该站点的多路复用有问题。
     */
    // 试跑窗口：48 连接光是建连就可能超过 6 秒，之前太短会把"还没开始收数据"
    // 误判成"这个站不适合多连接"，整站被降级成单连接。
    private const val TRIAL_MS = 20_000L

    /**
     * 默认连接数。实测（HTTP/1.1，每条独立 TCP 连接）：
     * 2026-10-13 复测（kasaneteto.jp，两轮）：
     *   16 条 1046 / 988 KB/s
     *   32 条 2292 / 2335 KB/s   ← 稳定翻倍
     *   48 条 4678 / 2369 KB/s
     * 这个站点是"按连接限速"的，多开才有意义，所以默认给到 32。
     */
    private const val DEFAULT_WORKERS = 32

    /** 真正用于网络连接的最大并发数；再高实测反而更慢甚至卡住 */
    private const val MAX_WORKERS = 64

    // ---------------------------------------------------------------- 站点模式记忆

    private var prefs: SharedPreferences? = null

    /** 只用于取本地化文案：init 已经把 Application Context 交进来了，不必改任何函数签名 */
    private var appCtx: Context? = null

    fun init(context: Context) {
        runCatching {
            forceMulti = context.getSharedPreferences("utm_boot", Context.MODE_PRIVATE)
                .getBoolean("force_multi", false)
        }
        appCtx = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences("utm_host_mode", Context.MODE_PRIVATE)
    }

    /** 本地化文案。appCtx 由 Application.onCreate 里的 init 填好；万一为空就退回空串，绝不因取文案而崩 */
    private fun tr(resId: Int, vararg args: Any): String =
        appCtx?.getString(resId, *args).orEmpty()

    /** 记住"这个站点多线程不划算"的有效期：服务器策略会变（实测 kasaneteto 一天内就反转了），不能永久记住 */
    // 之前是 6 小时 —— 网速差的那段时间被标记成"单连接"，之后就一直单连接，这是"变慢"的一大原因
    private const val HOST_MODE_TTL_MS = 45L * 60 * 1000

    /** 设置里直接关掉所有"单连接降级"，用于排障/提速 */
    private var forceMulti: Boolean = false
    fun setForceMulti(v: Boolean) { forceMulti = v }

    private fun isHostSingle(host: String): Boolean {
        if (forceMulti) return false
        val at = prefs?.getLong("single_v3_at_$host", 0L) ?: 0L
        return at > 0 && System.currentTimeMillis() - at < HOST_MODE_TTL_MS
    }

    /** 清掉所有"这个站只能单连接"的记忆，让它们重新用多连接试一次 */
    fun clearHostMemory() {
        runCatching {
            val all = prefs?.all ?: return@runCatching
            prefs?.edit {
                all.keys.filter { it.startsWith("single_v3_at_") }.forEach { remove(it) }
            }
            L.d("已清空单连接记忆")
        }
    }

    private fun rememberHostSingle(host: String) {
        if (host.isBlank()) return
        prefs?.edit { putLong("single_v3_at_$host", System.currentTimeMillis()) }
    }

    fun hostOf(url: String): String =
        runCatching { java.net.URI(url).host ?: "" }.getOrDefault("").lowercase()

    // ---------------------------------------------------------------- 入口

    suspend fun download(
        client: OkHttpClient,
        url: String,
        dest: File,
        expectedSize: Long,
        connections: Int,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        shouldStop: () -> Stop,
        tag: String = "",
    ): Result {
        val probe = withContext(Dispatchers.IO) { probe(client, url, expectedSize) }
            ?: return Result(false, 0, message = tr(R.string.dlmsg_cannot_connect))
        val total = if (probe.totalBytes > 0) probe.totalBytes else expectedSize
        val host = hostOf(url)

        // 并发数按文件体积自适应：太小文件不值得拆太多块
        val maxBySize = if (total > 0) (total / MIN_CHUNK_BYTES).toInt() else DEFAULT_WORKERS
        // 【实测】并发超过 32 后，多数 CDN 会开始限速甚至直接卡住（握手/排队开销反而更大）。
        // 用户在设置里选了 48/64 也按 32 跑 —— 宁可稳，不要"看起来线程多但其实是卡着"。
        val workers = when {
            connections >= 2 -> connections.coerceIn(2, MAX_WORKERS)
            else -> DEFAULT_WORKERS
        }.coerceAtMost(maxBySize.coerceAtLeast(1))

        val canChunk = probe.rangeSupported && total > MIN_CHUNKED_BYTES &&
            workers > 1 && total / workers >= MIN_CHUNK_BYTES

        // 【关键】已有 .meta 说明这个半成品是**分块**写的 —— 文件是稀疏的，
        // length() 是"最高写入位置"，不等于已下载字节数。
        // 如果此时改走单连接，single() 会拿 length() 当起点、判定"文件损坏"然后删光重下，
        // 表现就是"暂停后再点下载，又从 0 开始"。
        // 所以只要 .meta 在，就必须继续用 chunked；真要退化成单连接，也要先干净地清掉。
        val hadMeta = metaFile(dest).exists()
        if (hadMeta && !probe.rangeSupported) {
            L.w("续传时服务器不再支持 Range，只能清掉重下: " + dest.name)
            clearPartial(dest)
            return single(client, url, dest, total, onProgress, shouldStop, tag)
        }
        if (!hadMeta && (!canChunk || isHostSingle(host))) {
            return single(client, url, dest, total, onProgress, shouldStop, tag)
        }

        val r = try {
            chunked(client, url, dest, total, workers, onProgress, shouldStop, tag)
        } catch (t: Throwable) {
            // 服务器在分块请求里回了 200（忽略 Range）：清干净后改用单连接，
            // 以前这里会一路抛到上层，任务直接失败（"下到 100% 然后失败"）。
            if (t is RangeIgnoredException) {
                L.w("分块被忽略 Range，回退单连接: " + dest.name)
                clearPartial(dest)
                return single(client, url, dest, total, onProgress, shouldStop, tag)
            }
            throw t
        }
        if (r.abortedSlow) {
            // 只本次回退，不再把域名记成"只能单连接"——
            // 一次网络抖动就会让整站 45 分钟都慢（用户反馈过好几次）。
            L.w("站点 $host 本次多线程未收到数据，回退单连接（不记忆）")
            rememberHostSingle(host).let { }
            clearPartial(dest)
            return single(client, url, dest, total, onProgress, shouldStop, tag)
        }
        return r
    }

    // ---------------------------------------------------------------- 单连接

    private suspend fun single(
        client: OkHttpClient,
        url: String,
        dest: File,
        knownTotal: Long,
        onProgress: (Long, Long) -> Unit,
        shouldStop: () -> Stop,
        tag: String,
    ): Result = coroutineScope {
        // 单连接是顺序追加的，文件长度就是已下载字节数 —— 只有这种模式下才可靠。
        // 如果存在 .meta（上一次是分块写的），文件是稀疏的，length() 不能当已下载量。
        if (metaFile(dest).exists()) {
            L.w("发现分块半成品，单连接模式下无法安全续传，清理后重下: " + dest.name)
            clearPartial(dest)
        }
        var start = if (dest.exists()) dest.length() else 0L
        val looksDone = start > 0 && knownTotal > 0 && start >= knownTotal
        if (looksDone && com.utaumarket.mobile.archive.ArchiveExtractor.isCompleteArchive(dest)) {
            onProgress(knownTotal, knownTotal)
            return@coroutineScope Result(true, knownTotal)
        }
        // 长度够了但内容不完整（旧版预分配留下的空洞文件），或者头部就不是压缩包 → 丢弃重下。
        // 这一步是"解压失败一直复现"的根治点：坏文件必须在这里就被扔掉。
        if (start > 0 && (looksDone ||
                !com.utaumarket.mobile.archive.ArchiveExtractor.looksLikeArchive(dest))
        ) {
            L.w("半成品不完整，丢弃重下: ${dest.name}")
            runCatching { dest.delete() }
            start = 0
        }

        // 进度回调必须在独立协程里跑：写通知是跨进程调用，慢了会直接卡住读取循环
        val counter = AtomicLong(start)
        val reporter = async(Dispatchers.IO) {
            while (isActive) {
                onProgress(counter.get(), knownTotal)
                delay(400)
            }
        }
        val result = withContext(Dispatchers.IO) {
            var from = start
            try {
                while (true) {
                    val builder = Request.Builder().url(url)
                        .header("User-Agent", USER_AGENT)
                        .tag(String::class.java, tag)
                    if (from > 0) builder.header("Range", "bytes=$from-")

                    var restart = false
                    client.newCall(builder.build()).execute().use { resp ->
                        if (!resp.isSuccessful && resp.code != 206) {
                            return@withContext Result(false, from, message = "HTTP ${resp.code}")
                        }
                        if (from > 0 && resp.code == 200) {
                            restart = true
                        } else {
                            val body = resp.body
                                ?: return@withContext Result(false, from, message = tr(R.string.dlmsg_empty_response))
                            val total = when {
                                resp.code == 206 ->
                                    body.contentLength().takeIf { it > 0 }?.plus(from) ?: knownTotal
                                body.contentLength() > 0 -> body.contentLength()
                                else -> knownTotal
                            }
                            if (from == 0L) counter.set(0)
                            dest.parentFile?.mkdirs()
                            var read = from
                            body.byteStream().use { ins ->
                                java.io.FileOutputStream(dest, from > 0).use { out ->
                                    val buf = ByteArray(BUFFER_SIZE)
                                    while (true) {
                                        if (shouldStop() != Stop.NONE) {
                                            counter.set(read)
                                            return@withContext Result(false, read, shouldStop())
                                        }
                                        val n = ins.read(buf)
                                        if (n <= 0) break
                                        out.write(buf, 0, n)
                                        read += n
                                        counter.set(read)
                                    }
                                }
                            }
                            counter.set(read)
                            val stop = shouldStop()
                            if (stop != Stop.NONE) return@withContext Result(false, read, stop)
                            if (total > 0 && read < total) {
                                return@withContext Result(
                                    false, read,
                                    message = tr(R.string.dlmsg_conn_interrupted, read / 1024, total / 1024),
                                )
                            }
                            return@withContext Result(true, if (total > 0) total else read)
                        }
                    }
                    if (restart) {
                        L.w("服务器忽略 Range，重新从头下载")
                        runCatching { dest.delete() }
                        from = 0
                        counter.set(0)
                        continue
                    }
                    break
                }
                Result(false, 0, message = tr(R.string.dlmsg_download_failed))
            } catch (t: Throwable) {
                L.w("下载失败", t)
                Result(false, 0, message = friendly(t))
            }
        }
        reporter.cancel()
        onProgress(counter.get(), knownTotal)
        result
    }

    // ---------------------------------------------------------------- 多线程分块

    private fun metaFile(dest: File) = File(dest.parentFile, dest.name + ".meta")

    private fun clearPartial(dest: File) {
        runCatching { dest.delete() }
        runCatching { metaFile(dest).delete() }
    }

    private fun readMeta(dest: File, total: Long, workers: Int): LongArray? = runCatching {
        val f = metaFile(dest)
        if (!f.isFile || !dest.isFile) return null
        val lines = f.readLines()
        if (lines.isEmpty()) return null
        val head = lines[0].split(',')
        if (head.size < 2) return null
        if (head[0].toLong() != total || head[1].toInt() != workers) return null
        val done = LongArray(workers)
        lines.drop(1).forEach { line ->
            val p = line.split(',')
            if (p.size >= 2) {
                val i = p[0].toIntOrNull() ?: return@forEach
                if (i in 0 until workers) done[i] = p[1].toLongOrNull() ?: 0L
            }
        }
        done
    }.getOrNull()

    private suspend fun chunked(
        client: OkHttpClient,
        url: String,
        dest: File,
        total: Long,
        workers: Int,
        onProgress: (Long, Long) -> Unit,
        shouldStop: () -> Stop,
        tag: String,
    ): Result = coroutineScope {
        dest.parentFile?.mkdirs()

        // 【关键修复】小分块 + 动态抢活（work stealing）。
        //
        // 以前是"每个 worker 独占一大块"：先下完的 worker 就闲着，
        // 最后阶段只剩一两个连接在爬，速度直接塌下来 ——
        // 用户反馈的"快下载完了速度就变得非常慢"就是这个原因。
        //
        // 现在把文件切成很多小块（每块约 total/(workers*8)），所有 worker 从一个
        // 共享队列里抢下一块，直到全部下完。这样连接数始终拉满，
        // 而且某一块慢也只影响那一小块，不会拖住整体。
        // 【关键】分块布局只由 total 决定，**和并发数无关**。
        // 以前 targetChunk 里带着 workers，用户一改「并发连接数」，chunkCount 就变，
        // 于是 meta 对不上 → 删掉半成品 → 暂停再继续就变成从头下载（用户报的 bug）。
        // 现在固定 1 MiB 一块，workers 只决定"同时几个人抢活"，不再影响布局。
        val targetChunk = CHUNK_BYTES
        val chunkCount = ((total + targetChunk - 1) / targetChunk)
            .coerceIn(1L, MAX_CHUNKS.toLong()).toInt()
        val chunkSize = (total + chunkCount - 1) / chunkCount
        val meta = readMeta(dest, total, chunkCount)
        if (meta == null && dest.exists()) {
            // 只有"布局对不上"（换过文件/版本太旧/下载地址变了）才会走到这里 —— 从头来，避免拼出坏文件
            L.w("分块元数据不可用，重新下载: ${dest.name} (total=$total chunks=$chunkCount)")
            runCatching { dest.delete() }
            runCatching { metaFile(dest).delete() }
        }
        val fresh = LongArray(chunkCount)
        val doneArr = java.util.concurrent.atomic.AtomicLongArray(chunkCount)
        (meta ?: fresh).forEachIndexed { i, v -> if (i < chunkCount) doneArr.set(i, v) }
        val totalDone = AtomicLong((meta ?: fresh).sum())

        // 每块一个进度累计，便于写 meta
        fun saveMeta() {
            runCatching {
                val sb = StringBuilder()
                sb.append(total).append(',').append(chunkCount).append('\n')
                for (i in 0 until chunkCount) sb.append(i).append(',').append(doneArr.get(i)).append('\n')
                metaFile(dest).writeText(sb.toString())
            }
        }

        val reporter = async(Dispatchers.IO) {
            var stopped: Stop = Stop.NONE
            while (isActive) {
                stopped = shouldStop()
                if (stopped != Stop.NONE) break
                onProgress(totalDone.get(), total)
                if (totalDone.get() >= total) break
                delay(400)
            }
            onProgress(totalDone.get(), total)
            stopped
        }

        val saver = async(Dispatchers.IO) {
            while (isActive) {
                delay(2000)
                saveMeta()
            }
        }

        // 试跑看门狗：多连接被限速就尽早放弃
        val slow = java.util.concurrent.atomic.AtomicBoolean(false)
        val base = totalDone.get()
        val watchdog = async(Dispatchers.IO) {
            delay(TRIAL_MS)
            // 只有彻底收不到数据才算"这个站点不适用多连接"；慢不等于不适用
            if (totalDone.get() - base <= 0L) {
                slow.set(true)
                L.w("多线程开跑 ${TRIAL_MS}ms 一个字节都没收到，判定为不适用多连接")
            }
        }

        // 共享任务队列：worker 空出来就抢下一块，直到全部抢完 ——
        // 这样最后阶段也不会只剩一两个连接在爬。
        val nextChunk = java.util.concurrent.atomic.AtomicInteger(0)
        val jobs = (0 until workers).map {
            async<Boolean>(Dispatchers.IO) {
                var finished = false
                while (!finished) {
                    if (slow.get()) return@async false
                    if (shouldStop() != Stop.NONE) return@async false
                    val i = nextChunk.getAndIncrement()
                    if (i >= chunkCount) { finished = true; continue }
                val chunkStart = i.toLong() * chunkSize
                if (chunkStart >= total) return@async true
                val chunkEnd = minOf(chunkStart + chunkSize - 1, total - 1)
                val startFrom = doneArr.get(i).coerceIn(0L, chunkEnd - chunkStart + 1)
                var offset = chunkStart + startFrom
                var attempts = 0
                while (offset <= chunkEnd) {
                    if (slow.get()) return@async false
                    if (shouldStop() != Stop.NONE) return@async false
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Range", "bytes=$offset-$chunkEnd")
                        .tag(String::class.java, tag)
                        .build()
                    try {
                        client.newCall(req).execute().use { resp ->
                            if (resp.code != 206 && resp.code != 200) {
                                lastHttpCode.set(resp.code)
                                throw IllegalStateException("HTTP " + resp.code)
                            }
                            // 【关键】服务器忽略 Range、回了 200 时，body 是**整个文件**。
                            // 如果此时 offset 不在文件开头（暂停后续传的典型情况），
                            // 把整个文件写到中间位置会把文件写坏 —— 表现就是"续传后解压失败"。
                            // 这里直接报错，让上层删掉半成品重新下，绝不写坏数据。
                            if (resp.code == 200 && (offset > 0 || chunkStart > 0)) {
                                throw RangeIgnoredException(offset)
                            }
                            val body = resp.body ?: throw IllegalStateException("empty body")
                            body.byteStream().use { ins ->
                                RandomAccessFile(dest, "rw").use { raf ->
                                    raf.seek(offset)
                                    val buf = ByteArray(BUFFER_SIZE)
                                    var wrote = 0
                                    while (true) {
                                        if (slow.get()) return@async false
                                        if (shouldStop() != Stop.NONE) return@async false
                                        val n = ins.read(buf)
                                        if (n <= 0) break
                                        raf.write(buf, 0, n)
                                        wrote += n
                                        offset += n
                                        // 每块自己记进度：meta 靠它，不靠文件长度
                                        doneArr.addAndGet(i, n.toLong())
                                        totalDone.addAndGet(n.toLong())
                                    }
                                    if (wrote == 0 && offset <= chunkEnd) {
                                        throw IllegalStateException("空响应")
                                    }
                                }
                            }
                        }
                        attempts = 0
                    } catch (t: Throwable) {
                        // 服务器不支持续传：删掉半成品，让上层从头重新下载。
                        // 继续重试只会反复写坏同一个文件。
                        if (t is RangeIgnoredException) {
                            L.w("服务器忽略 Range，删除半成品重新下载: " + dest.name)
                            runCatching { dest.delete() }
                            runCatching { metaFile(dest).delete() }
                            throw t
                        }
                        if (slow.get() || shouldStop() != Stop.NONE) return@async false
                        attempts++
                        if (attempts >= MAX_RETRY) {
                            L.w("分块重试耗尽: " + offset + "-" + chunkEnd, t)
                            return@async false
                        }
                        delay(600L * attempts)
                    }
                }
                }
                true
            }
        }

        val allOk = jobs.awaitAll().all { it }
        val lastCode = lastHttpCode.get()
        val stopped = reporter.await()
        saver.cancel()
        watchdog.cancel()
        saveMeta()
        onProgress(totalDone.get(), total)

        when {
            slow.get() -> Result(false, totalDone.get(), abortedSlow = true)
            stopped != Stop.NONE -> Result(false, totalDone.get(), stopped)
            !allOk -> Result(
                false, totalDone.get(),
                message = when {
                    lastCode == 403 || lastCode == 401 ->
                        tr(R.string.dlmsg_http_forbidden)
                    lastCode > 0 -> "HTTP $lastCode"
                    else -> tr(R.string.dlmsg_chunk_interrupted)
                },
            )
            totalDone.get() < total -> Result(false, totalDone.get(), message = tr(R.string.dlmsg_file_incomplete))
            else -> {
                // 注意：只删 meta，下载好的文件要留给安装流程用
                runCatching { metaFile(dest).delete() }
                Result(true, total)
            }
        }
    }

    // ---------------------------------------------------------------- 探测

    /** 把底层异常翻译成用户能看懂的话，别只丢一句 timeout */
    private fun friendly(t: Throwable): String = when (t) {
        is java.net.SocketTimeoutException ->
            tr(R.string.dlmsg_err_no_response)
        is java.net.UnknownHostException -> tr(R.string.dlmsg_err_host)
        is java.net.ConnectException -> tr(R.string.dlmsg_err_connect)
        is javax.net.ssl.SSLException -> tr(R.string.dlmsg_err_ssl)
        // 其余异常不再原样透出（okhttp 的原文是英文），统一给一句人话
        else -> tr(R.string.dlmsg_download_failed)
    }

    /**
     * 让某个域名下次只用单连接。
     *
     * 分块下载对"按 206 精确返回"的服务器才安全；遇到行为异常（忽略 Range、
     * 返回长度不对、写出来的文件读不出中央目录）就退回单连接，避免反复产出坏包。
     */
    fun forceSingleFor(url: String) {
        runCatching { rememberHostSingle(java.net.URI(url).host ?: "") }
    }

    /** 分块下载里最后一次非 200/206 的 HTTP 码，用来给用户一个能看懂的原因 */
    private val lastHttpCode = java.util.concurrent.atomic.AtomicInteger(0)

    private data class Probe(val totalBytes: Long, val rangeSupported: Boolean)

    private fun probe(client: OkHttpClient, url: String, expectedSize: Long): Probe? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Range", "bytes=0-0")
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful || resp.code == 206) {
                val cr = resp.header("Content-Range")
                val total = cr?.substringAfter('/')?.toLongOrNull()
                    ?: resp.body?.contentLength()?.takeIf { it > 0 }
                    ?: expectedSize
                val rangeSupported = resp.code == 206 && total > 0
                resp.body?.byteStream()?.use { it.read() }
                Probe(total, rangeSupported)
            } else if (resp.code == 403 || resp.code == 400 || resp.code == 416) {
                // 【关键 1】有的 CDN **只拒绝"极小"的 Range**：bytes=0-0 → 403，
                // 但 bytes=0-1048575 → 206（实测欲音ルコ 的 CloudFront 就是这样）。
                // 之前只用 bytes=0-0 探测，一旦 403 就退化成单连接 —— 结果 2KB/s。
                // 现在先用一个正常大小的 Range 再试一次。
                resp.close()
                probeRange(client, url, expectedSize, "bytes=0-65535")
                    ?: probeNoRange(client, url, expectedSize)
            } else null
        }
    } catch (t: Throwable) {
        L.w("探测失败", t)
        if (expectedSize > 0) Probe(expectedSize, false) else null
    }

    /** 用一个"正常大小"的 Range 再探一次（有些 CDN 拒绝 bytes=0-0 这种极小范围） */
    private fun probeRange(
        client: OkHttpClient, url: String, expectedSize: Long, range: String,
    ): Probe? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Range", range)
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.code == 206 || resp.isSuccessful) {
                val cr = resp.header("Content-Range")
                val total = cr?.substringAfter('/')?.toLongOrNull()
                    ?: resp.body?.contentLength()?.takeIf { it > 0 }
                    ?: expectedSize
                val rangeSupported = resp.code == 206 && total > 0
                runCatching { resp.body?.byteStream()?.read() }   // 读 1 字节就断开
                Probe(total, rangeSupported)
            } else null
        }
    } catch (t: Throwable) {
        L.w("Range 复探失败", t)
        null
    }

    /** 不带 Range 的探测：只读 1 字节就关闭连接，用来兼容"拒绝 Range"的服务器 */
    private fun probeNoRange(client: OkHttpClient, url: String, expectedSize: Long): Probe? = try {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val total = resp.body?.contentLength()?.takeIf { it > 0 } ?: expectedSize
                // 读一点点就断开，别真把整个文件拉下来
                runCatching { resp.body?.byteStream()?.read() }
                Probe(total, rangeSupported = false)   // 明确告诉调用方：只能单连接
            } else null
        }
    } catch (t: Throwable) {
        L.w("无 Range 探测失败", t)
        null
    }
}

/** 服务器忽略了 Range 请求（回 200 而不是 206），此时不能续传 */
class RangeIgnoredException(val offset: Long) :
    IllegalStateException("服务器忽略 Range（offset=" + offset + "），无法安全续传")
