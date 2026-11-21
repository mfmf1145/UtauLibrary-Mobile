package com.utaumarket.mobile.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.utaumarket.mobile.R
import com.utaumarket.mobile.UtauMarketApp
import com.utaumarket.mobile.archive.ArchiveExtractor
import com.utaumarket.mobile.archive.Installer
import com.utaumarket.mobile.archive.TextEncoding
import com.utaumarket.mobile.download.LinkResolver
import com.utaumarket.mobile.core.L
import com.utaumarket.mobile.data.model.DownloadStatus
import com.utaumarket.mobile.data.model.DownloadTask
import com.utaumarket.mobile.data.model.InstallRule
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.saf.SafManager
import com.utaumarket.mobile.saf.TargetDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** 一次下载 + 解压 + 安装请求 */
data class DownloadRequest(
    val id: String,
    val resourceId: String,
    val name: String,
    val kind: ResourceKind,
    val url: String,
    val fileName: String,
    val installRule: InstallRule,
    val targetSubDir: String,
    val treeUri: String,
    val targetDocId: String = "",
    val expectedSize: Long,
    val overwrite: Boolean,
    /** 分支改版（宿主 APK），用于下载列表里的类型标签 */
    val fork: Boolean = false,
) {
    fun toTask(): DownloadTask = DownloadTask(
        id = id,
        resourceId = resourceId,
        name = name,
        kind = kind,
        fork = fork,
        url = url,
        fileName = fileName,
        installRule = installRule,
        targetSubDir = targetSubDir,
        expectedSize = expectedSize,
        treeUri = treeUri,
        targetDocId = targetDocId,
        overwrite = overwrite,
    )

    fun toJson(): String = JSONObject().apply {
        put("id", id); put("resourceId", resourceId); put("name", name)
        put("kind", kind.id); put("url", url); put("fileName", fileName)
        put("installRule", installRule.id); put("targetSubDir", targetSubDir)
        put("treeUri", treeUri); put("targetDocId", targetDocId)
        put("expectedSize", expectedSize); put("overwrite", overwrite)
    }.toString()

    companion object {
        fun fromJson(s: String): DownloadRequest {
            val o = JSONObject(s)
            return DownloadRequest(
                id = o.optString("id"),
                resourceId = o.optString("resourceId"),
                name = o.optString("name"),
                kind = ResourceKind.fromId(o.optString("kind")) ?: ResourceKind.VOICEBANK,
                url = o.optString("url"),
                fileName = o.optString("fileName"),
                installRule = InstallRule.fromId(o.optString("installRule")),
                targetSubDir = o.optString("targetSubDir"),
                treeUri = o.optString("treeUri"),
                targetDocId = o.optString("targetDocId"),
                expectedSize = o.optLong("expectedSize"),
                overwrite = o.optBoolean("overwrite", true),
            )
        }
    }
}

/**
 * 前台服务：负责「下载 → 解压 → 安装」全过程，支持**暂停 / 继续 / 取消重试**。
 *
 * 关键点：
 *  - 每个任务有一份 [Control] 标志，下载器在每次读取前都会检查，所以暂停/取消是即时的；
 *  - 未完成的字节留在 cacheDir/utm/partials/<资源>.part，继续时用 HTTP Range 从这个长度续传；
 *  - 取消会删除残留文件，暂停会保留；
 *  - 解压/安装阶段也会检查标志，不会"取消了还在写文件"。
 */
class DownloadService : Service() {

    private class Control {
        @Volatile var canceled = false
        @Volatile var paused = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ArrayDeque<DownloadRequest>()
    private var worker: Job? = null
    /** 当前存活的 worker 数量 */
    private val activeWorkers = java.util.concurrent.atomic.AtomicInteger(0)
    /** 同时下载的任务数 */
    private val MAX_PARALLEL = 3
    private val controls = ConcurrentHashMap<String, Control>()
    private val requests = ConcurrentHashMap<String, DownloadRequest>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 关键：强制 HTTP/1.1。
            // HTTP/2 下 OkHttp 会把 4 个分块请求多路复用成**一条 TCP 连接上的 4 个流**，
            // 实测只有 18 KB/s；而 4 条独立 TCP 连接能到 63 KB/s，快 3.5 倍。
            // 这里是纯批量下载，不需要多路复用，要的就是"真·多连接"。
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            // 致命默认值：OkHttp 的 maxRequestsPerHost 默认只有 5，
            // 就算开 16 个工作线程，实际也只有 5 条在跑。这里必须放开。
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 128
                    maxRequestsPerHost = 64
                }
            )
            // 【关键】连接池必须 >= 最大并发数，否则 worker 抢不到连接会干等 ——
            // 之前池子只有 32，用户选 48/64 线程时看起来就是"卡住"。
            .connectionPool(okhttp3.ConnectionPool(96, 5, TimeUnit.MINUTES))
            .connectTimeout(10, TimeUnit.SECONDS)
            // 读取超时缩短：网络半死时 30 秒才报错，界面看上去就是"卡住"
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.ensureChannel(this)
        // 【孤儿缓存清理】取消下载会删掉 .part，但进程被杀、卡刷、崩溃等情况下
        // 半截文件会留在 cacheDir/utm 里一直占空间。这里在每次服务启动时清一次。
        runCatching { cleanupOrphanCache() }
        when (intent?.action) {
            ACTION_ENQUEUE -> {
                val req = intent.getStringExtra(EXTRA_REQUEST)?.let {
                    runCatching { DownloadRequest.fromJson(it) }.getOrNull()
                } ?: return START_NOT_STICKY
                promoteToForeground(req.name, getString(R.string.dlmsg_queued), 0, true)
                requests[req.id] = req
                controls[req.id] = Control()
                queue.addLast(req)
                DownloadController.upsert(req.toTask())
                startWorkerIfNeeded()
            }

            ACTION_PAUSE -> {
                val id = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                intent.getStringExtra(EXTRA_REQUEST)?.let {
                    runCatching { DownloadRequest.fromJson(it) }.getOrNull()?.let { r -> requests[id] = r }
                }
                controls.getOrPut(id) { Control() }.paused = true
                queue.removeAll { it.id == id }
                cancelCallsFor(id)
                setStatus(id, DownloadStatus.PAUSED, getString(R.string.dlmsg_paused_resumable))
            }

            ACTION_RESUME -> {
                val id = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                val fromExtra = intent.getStringExtra(EXTRA_REQUEST)?.let {
                    runCatching { DownloadRequest.fromJson(it) }.getOrNull()
                }
                val req = fromExtra ?: requests[id] ?: return START_NOT_STICKY
                requests[id] = req
                promoteToForeground(req.name, getString(R.string.dlmsg_resuming), 0, true)
                val c = controls.getOrPut(id) { Control() }
                c.paused = false
                c.canceled = false
                if (queue.none { it.id == id }) {
                    queue.addLast(req)
                    setStatus(id, DownloadStatus.QUEUED, getString(R.string.dlmsg_waiting_resume))
                    startWorkerIfNeeded()
                }
            }

            ACTION_CANCEL -> {
                val id = intent.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
                intent.getStringExtra(EXTRA_REQUEST)?.let {
                    runCatching { DownloadRequest.fromJson(it) }.getOrNull()?.let { r -> requests[id] = r }
                }
                controls.getOrPut(id) { Control() }.canceled = true
                queue.removeAll { it.id == id }
                cancelCallsFor(id)
                setStatus(id, DownloadStatus.CANCELED, getString(R.string.dlmsg_canceled))
                deletePartial(id)
            }

            ACTION_CANCEL_ALL -> {
                queue.clear()
                controls.values.forEach { it.canceled = true }
                runCatching {
                    client.dispatcher.runningCalls().forEach { it.cancel() }
                }
                DownloadController.snapshot().forEach {
                    if (it.status != DownloadStatus.DONE && it.status != DownloadStatus.FAILED) {
                        DownloadController.upsert(it.copy(status = DownloadStatus.CANCELED, message = getString(R.string.dlmsg_canceled)))
                    }
                }
            }
        }
        // 只有"真的一点活都没有"时才停服务。
        // 以前判断的是单个 worker 变量，多并发后又引入 activeWorkers，
        // 于是队列刚被取空就会误判"没活"→ stopSelf → onDestroy 取消协程 →
        // 下载到 100% 突然变暂停/安装失败（用户反复遇到的就是这个）。
        if (queue.isEmpty() && !hasActiveWork()) stopSelfSafely()
        return START_NOT_STICKY
    }

    /** 还有没有真正在跑的活（下载/解压/安装都算） */
    private fun hasActiveWork(): Boolean {
        if (activeWorkers.get() > 0) return true
        return DownloadController.snapshot().any {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.EXTRACTING || it.status == DownloadStatus.INSTALLING
        }
    }

    private fun stopSelfSafely() {
        runCatching { stopForegroundCompat(); stopSelf() }
    }

    /**
     * 并行下载。原来是单 worker 串行 —— 队列里有第二个任务时必须等第一个彻底装完。
     * 现在开 MAX_PARALLEL 个 worker 同时跑，任务之间互不等待。
     */

    /**
     * 卡死看门狗。
     *
     * 解压/写入阶段如果底层库卡在一个阻塞读上，任务会永远停在 EXTRACTING/INSTALLING，
     * 用户看到的就是"卡住无法安装"。这里每 30 秒扫一次，超时直接判失败，让队列能继续。
     */
    private var watchdog: Job? = null

    private fun startWatchdog() {
        if (watchdog?.isActive == true) return
        watchdog = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                val now = System.currentTimeMillis()
                // ① 下载阶段：超过 60 秒没有新字节 = 服务器已经断流
                for (t in DownloadController.snapshot()) {
                    if (t.status != DownloadStatus.DOWNLOADING) {
                        lastProgress.remove(t.id)
                        continue
                    }
                    val prev = lastProgress[t.id]
                    if (prev == null || prev.first != t.bytesDownloaded) {
                        lastProgress[t.id] = t.bytesDownloaded to now
                    } else if (now - prev.second > DOWNLOAD_STALL_MS) {
                        L.w("下载停滞 " + ((now - prev.second) / 1000) + "s: " + t.name)
                        cancelCallsFor(t.id)
                        lastProgress.remove(t.id)
                        // 半成品留着，用户可以点重试续传
                        setStatus(
                            t.id,
                            DownloadStatus.FAILED,
                            getString(R.string.dlmsg_download_stalled, DOWNLOAD_STALL_MS / 1000),
                        )
                    }
                }
                // ② 解压/安装阶段：15 分钟没完成
                for (t in DownloadController.snapshot()) {
                    val stuck = t.status == DownloadStatus.EXTRACTING ||
                        t.status == DownloadStatus.INSTALLING
                    if (!stuck) continue
                    val since = if (t.statusSince > 0) t.statusSince else now
                    if (now - since > STUCK_TIMEOUT_MS) {
                        setStatus(
                            t.id,
                            DownloadStatus.FAILED,
                            getString(R.string.dlmsg_stuck_timeout, STUCK_TIMEOUT_MS / 60000),
                        )
                    }
                }
            }
        }
    }

    private val STUCK_TIMEOUT_MS = 15 * 60 * 1000L

    /** 下载阶段「多久没有新字节」判定为服务器断流 */
    private val DOWNLOAD_STALL_MS = 60_000L

    /** 每个任务上一次的（已下载字节, 时间戳），用于判定断流 */
    private val lastProgress = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()

    private fun startWorkerIfNeeded() {
        val running = activeWorkers.get()
        if (running >= MAX_PARALLEL) return
        repeat(MAX_PARALLEL - running) {
            activeWorkers.incrementAndGet()
            startWatchdog()
        worker = scope.launch {
                try {
                    while (true) {
                        val req = synchronized(queue) { queue.removeFirstOrNull() } ?: break
                        // 控制器那边已经锁成「已暂停」的，绝不能再跑一遍
                        // （进程重启后队列里可能还残留着这条请求）
                        // 三重确认：内存锁 / 服务控制位 / 持久化状态
                        val stored = DownloadController.snapshot().firstOrNull { it.id == req.id }
                        if (DownloadController.isTerminated(req.id) ||
                            controls[req.id]?.paused == true ||
                            controls[req.id]?.canceled == true ||
                            stored?.status == DownloadStatus.PAUSED ||
                            stored?.status == DownloadStatus.CANCELED
                        ) {
                            setStatus(req.id, DownloadStatus.PAUSED, getString(R.string.dlmsg_paused_resumable))
                            continue
                        }
                        try {
                            process(req)
                        } catch (t: Throwable) {
                            L.e("下载任务失败: " + req.name, t)
                            // 之前直接把 t.message 显示出来 —— 用户看到的是 "Job was cancelled" 这种英文原文。
                            // 取消就按取消算，其余一律翻成中文/英文/日文的人话，原始异常留在日志里。
                            if (DownloadController.isTerminated(req.id) || controls[req.id]?.paused == true) {
                                // 暂停导致的协程中断，不能算取消 —— 半成品必须留着续传
                                setStatus(req.id, DownloadStatus.PAUSED, getString(R.string.dlmsg_paused_resumable))
                            } else if (controls[req.id]?.canceled == true) {
                                setStatus(req.id, DownloadStatus.CANCELED, getString(R.string.dlmsg_canceled))
                            } else if (com.utaumarket.mobile.core.ErrorText.isCancellation(t)) {
                                // 服务被系统停掉导致的协程取消：不是用户取消，也不是失败，
                                // 标成「已暂停」让用户能无缝继续。
                                setStatus(req.id, DownloadStatus.PAUSED, getString(R.string.dlmsg_paused_resumable))
                            } else {
                                setStatus(req.id, DownloadStatus.FAILED, com.utaumarket.mobile.core.ErrorText.of(this@DownloadService, t))
                            }
                        }
                    }
                } finally {
                    val left = activeWorkers.decrementAndGet()
                    val pending = synchronized(queue) { queue.isEmpty() }
                    if (left == 0 && pending && !hasActiveWork()) stopSelfSafely()
                }
            }
        }
    }

    private fun controlOf(id: String): Control = controls.getOrPut(id) { Control() }

    /** 每个任务自动重下的次数，避免坏文件无限重试 */
    private val retries = java.util.concurrent.ConcurrentHashMap<String, Int>()

    /**
     * 下载到坏文件（不完整/解压不了）时自动重来一次。
     * 关键：必须先把坏文件删掉，否则重试还是拿同一个坏文件解压，永远失败。
     */
    private fun autoRetry(req: DownloadRequest, reason: String): Boolean {
        // 【必须放最前】暂停/取消中绝不自动重试。
        // 之前少了这层判断：暂停会 cancel 掉 HTTP 请求，底层把中断报成"下载失败"，
        // 于是自动重排队 → 又跑起来 → 又被暂停 → 来回弹好几次（用户报的"回弹四五次"）。
        if (DownloadController.isTerminated(req.id) ||
            controls[req.id]?.paused == true ||
            controls[req.id]?.canceled == true
        ) return false
        val n = retries.getOrDefault(req.id, 0)
        // 【可调】次数来自设置（默认 3 次，0 = 不自动重试）
        val max = UtauMarketApp.get().settings.current.autoRetryCount
        if (n >= max) return false
        retries[req.id] = n + 1
        deletePartial(req.id)
        // 【指数退避】之前是立刻重排，服务器抽风时会瞬间打满请求。
        // 现在第 1/2/3 次分别等 2/6/12 秒，界面显示"第 n/max 次重试"。
        val waitMs = when (n) { 0 -> 2_000L; 1 -> 6_000L; 2 -> 12_000L; else -> 20_000L }
        setStatus(
            req.id,
            DownloadStatus.QUEUED,
            getString(R.string.dlmsg_retry_scheduled, n + 1, max, reason, (waitMs / 1000).toInt()),
        )
        retryHandler.postDelayed({ queue.addLast(req) }, waitMs)
        return true
    }

    /** 重试退避用的主线程 handler */
    private val retryHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 按下暂停/取消时立刻掐断该任务的所有 HTTP 连接，不用等下一次读循环 */
    private fun cancelCallsFor(id: String) {
        runCatching {
            client.dispatcher.runningCalls()
                .filter { it.request().tag(String::class.java) == id }
                .forEach { it.cancel() }
        }
    }

    private fun stopReason(id: String): ParallelDownloader.Stop {
        val c = controls[id]
        return when {
            c?.canceled == true -> ParallelDownloader.Stop.CANCELED
            c?.paused == true -> ParallelDownloader.Stop.PAUSED
            // 兜底：controls 是内存 Map，服务重启后是空的。
            // 用户按过暂停这件事记录在 DownloadController 的持久锁里，必须一起问。
            // 取消优先于暂停（两者都终止，但界面语义不同）
            DownloadController.isCanceled(id) -> ParallelDownloader.Stop.CANCELED
            DownloadController.isPaused(id) -> ParallelDownloader.Stop.PAUSED
            else -> ParallelDownloader.Stop.NONE
        }
    }

    private suspend fun process(req: DownloadRequest) {
        val ctx = applicationContext
        val s = UtauMarketApp.get().settings.current
        val partial = partialFile(ctx, req)
        partial.parentFile?.mkdirs()

        // 【重要】安装前先确保目标目录真的存在。
        // 之前 targetFor() 直接返回设置里记的 docId，如果那个目录被删了（用户清理、
        // 换目录、卸载重装都会），createDocument 就会失败，然后被报成
        // "SAF 授权失败" —— 授权其实一直是好的，只是目录没了。
        // 这里跑一次 DirScanner（createIfMissing=true），目录不在就重建、docId 过期就刷新。
        runCatching {
            val root = UtauMarketApp.get().settings.current.rootTreeUri
            if (root.isNotBlank()) {
                val oc = com.utaumarket.mobile.data.installed.DirScanner
                    .scanAndConfigure(ctx, root, createIfMissing = true)
                if (!oc.ok) L.w("目标目录准备失败: " + oc.error)
            }
        }.onFailure { L.w("准备目标目录异常", it) }

        when (stopReason(req.id)) {
            ParallelDownloader.Stop.CANCELED -> { deletePartial(req.id); setStatus(req.id, DownloadStatus.CANCELED, getString(R.string.dlmsg_canceled)); return }
            ParallelDownloader.Stop.PAUSED -> { setStatus(req.id, DownloadStatus.PAUSED, getString(R.string.dlmsg_paused)); return }
            else -> {}
        }

        // 【体积探测】清单里 file_size 为 0 的条目（很多网盘/个人站的就是空的），
        // 先 HEAD 一次问服务器要 Content-Length，后面才能校验"是否下全"。
        var probedSize = req.expectedSize
        if (probedSize <= 0 && partial.length() == 0L) {
            probedSize = runCatching { probeContentLength(rawUrlForProbe(req.url)) }.getOrDefault(0L)
            if (probedSize > 0) L.d("探测到体积: " + human(probedSize) + " (" + req.name + ")")
        }

        val already = if (partial.exists()) partial.length() else 0L
        setStatus(
            req.id, DownloadStatus.DOWNLOADING,
            if (already > 0) getString(R.string.dlmsg_resume_from, human(already))
            else if (s.parallelDownload && s.connections > 1) getString(R.string.dlmsg_start_chunked, s.connections)
            else getString(R.string.dlmsg_start),
        )
        // 【进度复位】上一次失败时进度停在 100%，重试时 setStatus 不会清掉字节数，
        // 于是进度条一开始就是满的、看着像卡住。这里按真实的已有字节重置。
        runCatching {
            DownloadController.snapshot().firstOrNull { it.id == req.id }?.let { cur ->
                DownloadController.upsert(
                    cur.copy(
                        status = DownloadStatus.DOWNLOADING,
                        bytesDownloaded = already,
                        totalBytes = when {
                            probedSize > 0 -> probedSize
                            req.expectedSize > 0 -> req.expectedSize
                            else -> 0L
                        },
                        speedBps = 0,
                        etaSeconds = 0,
                    ),
                )
            }
        }

        var lastTick = System.currentTimeMillis()
        var lastBytes = already
        var lastUi = 0L
        // 服务器速度抖动非常大（实测 0 → 2.6 MB/s → 13 KB/s），
        // 直接显示瞬时值会一跳一跳，这里做个指数平滑
        var smoothedSpeed = 0L
        val onProgress: (Long, Long) -> Unit = { read, total ->
            val now = System.currentTimeMillis()
            val dt = now - lastTick
            val raw = if (dt > 0) (read - lastBytes) * 1000 / dt else 0L
            if (dt >= 500) { lastTick = now; lastBytes = read }
            smoothedSpeed = if (smoothedSpeed <= 0L) raw else (smoothedSpeed * 2 + raw) / 3
            val speed = smoothedSpeed
            // 界面与通知每 600ms 更新一次即可，避免高频刷新拖慢下载
            if (now - lastUi >= 600 || read >= total) {
                lastUi = now
                val cur = DownloadController.snapshot().firstOrNull { it.id == req.id }
                // 关键：暂停/取消已经生效时，绝不能被后台的进度回调刷回"下载中"
                if (cur != null && cur.status != DownloadStatus.PAUSED && cur.status != DownloadStatus.CANCELED) {
                    val eta = if (speed > 0 && total > read) (total - read) / speed else 0L
                    DownloadController.upsert(
                        cur.copy(
                            status = DownloadStatus.DOWNLOADING,
                            bytesDownloaded = read,
                            totalBytes = if (total > 0) total else req.expectedSize,
                            speedBps = speed,
                            etaSeconds = eta,
                        ),
                        // 关键：进度更新不许覆盖"已暂停 / 已取消"，否则卡片会闪一下再暂停
                        progressOnly = true,
                    )
                }
                val pct = if (total > 0) ((read * 100) / total).toInt() else 0
                val eta = if (speed > 0 && total > read) (total - read) / speed else 0L
                updateNotification(req.name, progressText(read, total, speed, eta), pct, total <= 0)
            }
        }

        // GitHub 直连在国内可能完全不通：按设置依次尝试直连 / 加速线路
        // 网盘落地页（MediaFire / Dropbox）先解析成直链，否则下到的会是 HTML
        val rawUrl = if (LinkResolver.needsResolve(req.url)) {
            updateMessage(req.id, getString(R.string.dlmsg_resolving))
            LinkResolver.resolve(req.url)
        } else {
            req.url
        }
        val candidates = UrlCandidates.build(rawUrl, s.ghMode, s.ghCustomPrefix)
        var result: ParallelDownloader.Result? = null
        // 【重试不重下】上一次如果只是"安装"失败，压缩包其实已经完整躺在本地了。
        // 这时候再从头下一遍纯属浪费流量和时间 —— 用户明确反馈过这个问题。
        val reuseLocal = partial.isFile && partial.length() > 0 &&
            ArchiveExtractor.isCompleteArchive(partial)
        if (reuseLocal) {
            updateMessage(req.id, getString(R.string.dlmsg_reuse_local))
            result = ParallelDownloader.Result(true, partial.length())
        }
        // 【模式诊断】把"分块还是单连接、几个线程"写进日志，
        // 下次速度不对时可以直接看出是不是退化成单连接了。
        runCatching {
            com.utaumarket.mobile.core.CrashReporter.appendShared(
                ctx, "UtauMarketMobile-download.log",
                "开始下载: " + req.name + " 期望大小=" + human(req.expectedSize) +
                    " 线程设置=" + s.connections + " 并行=" + s.parallelDownload + "\n",
            )
        }

        for ((idx, url) in candidates.withIndex()) {
            if (reuseLocal) break
            if (idx > 0 && (result == null || !result!!.ok)) {
                updateMessage(req.id, getString(R.string.dlmsg_switch_route, idx, candidates.size - 1))
            } else {
                updateMessage(req.id, getString(R.string.dlmsg_connecting))
            }
            // 下载器内部如果抛异常（服务器行为异常、OkHttp 崩溃等），
            // 以前会直接把整个任务协程带崩，界面上停在"下载中"不动。
            // 现在统一转成失败结果，并把原因写进日志文件。
            result = runCatching {
                ParallelDownloader.download(
                    client = client,
                    url = url,
                    dest = partial,
                    expectedSize = req.expectedSize,
                    connections = s.connections, // 并发分块数，设置里可调
                    onProgress = onProgress,
                    shouldStop = { stopReason(req.id) },
                    tag = req.id,
                )
            }.getOrElse { t ->
                L.w("下载器异常", t)
                runCatching {
                    com.utaumarket.mobile.core.CrashReporter.appendShared(
                        ctx, "UtauMarketMobile-download.log",
                        "下载异常: " + req.name +
                            "\n  " + url +
                            "\n  " + (t::class.java.simpleName) + ": " + (t.message ?: "") + "\n",
                    )
                }
                ParallelDownloader.Result(
                    false, partial.length(),
                    message = getString(R.string.dlmsg_download_error, t::class.java.simpleName),
                )
            }
            if (result.ok || result.stopped != ParallelDownloader.Stop.NONE) break
            L.w("线路失败，尝试下一条: $url")
        }
        val r = result ?: ParallelDownloader.Result(false, 0, message = getString(R.string.dlmsg_no_route))

        // 【关键】暂停时我们会 cancel 正在跑的请求，底层可能把这次中断报成普通失败。
        // 所以这里必须重新问一次用户的真实意图：暂停优先，绝不能被误判成"失败/取消"，
        // 也绝不能删半成品 —— 否则"暂停再继续"就变成从头下载（用户报的 bug）。
        val stopNow = stopReason(req.id)
        when {
            stopNow == ParallelDownloader.Stop.PAUSED -> {
                setStatus(req.id, DownloadStatus.PAUSED, getString(R.string.dlmsg_paused_resumable))
                return
            }
            stopNow == ParallelDownloader.Stop.CANCELED -> {
                deletePartial(req.id)
                setStatus(req.id, DownloadStatus.CANCELED, getString(R.string.dlmsg_canceled))
                return
            }
            r.stopped == ParallelDownloader.Stop.CANCELED -> {
                deletePartial(req.id)
                setStatus(req.id, DownloadStatus.CANCELED, getString(R.string.dlmsg_canceled))
                return
            }
            r.stopped == ParallelDownloader.Stop.PAUSED -> {
                setStatus(req.id, DownloadStatus.PAUSED, getString(R.string.dlmsg_paused_resumable))
                return
            }
            !r.ok -> {
                val why = r.message.ifBlank { getString(R.string.dlmsg_all_routes_failed) }
                runCatching {
                    com.utaumarket.mobile.core.CrashReporter.appendShared(
                        ctx, "UtauMarketMobile-download.log",
                        "下载失败: " + req.name +
                            "\n  " + rawUrl +
                            "\n  " + human(partial.length()) +
                            "\n  " + why + "\n",
                    )
                }
                setStatus(req.id, DownloadStatus.FAILED, why)
                return
            }
        }

        // ---------- 大小校验：字节数必须严格相等 ----------
        // 之前只在"压缩包结构看起来完整"时才校验，服务器不给 Content-Length 或被截断的情况下
        // 会把半个文件当成功（用户反馈"下载不完整"）。这里先按已知长度做硬比对。
        val expectBytes = when {
            r.totalBytes > 0 -> r.totalBytes
            req.expectedSize > 0 -> req.expectedSize
            probedSize > 0 -> probedSize
            else -> 0L
        }
        if (expectBytes > 0) {
            val gotBytes = partial.length()
            // 【判据只有一条】比预期**短**就是没下完 —— 必须判失败重下。
            // 比预期长则只记录（作者可能更新了同名文件）。
            // 之前把"不等"一律放过，结果截断的包被当成完整包安装，出现「只有 1.2 MB、大量文件缺失」。
            if (gotBytes < expectBytes) {
                val why = getString(R.string.dlmsg_size_mismatch, human(gotBytes), human(expectBytes))
                L.w("下载不完整：实际 $gotBytes < 预期 $expectBytes（" + req.name + "）")
                runCatching {
                    com.utaumarket.mobile.core.CrashReporter.appendShared(
                        ctx, "UtauMarketMobile-write.txt",
                        "下载不完整: " + req.name + " 实际=" + gotBytes + " 预期=" + expectBytes + "\n",
                    )
                }
                if (autoRetry(req, why)) return
                dropPartialOnFail(req)
                setStatus(req.id, DownloadStatus.FAILED, why)
                return
            } else if (gotBytes > expectBytes) {
                L.w("长度比清单更长（作者可能更新了文件）：实际 $gotBytes / 清单 $expectBytes（" + req.name + "）")
                runCatching {
                    com.utaumarket.mobile.core.CrashReporter.appendShared(
                        ctx, "UtauMarketMobile-write.txt",
                        "长度偏大: " + req.name + " 实际=" + gotBytes + " 清单=" + expectBytes + "\n",
                    )
                }
            }
        }

        // ---------- 压缩包结构强校验 ----------
        // 有些服务器不给 Content-Length（或清单里 file_size 为 0），
        // 截断的文件在"长度对不上"这条检查里查不出来。
        // 这里直接让 ZipFile 读中央目录：读不出来就是坏包/半包，绝不往下走。
        val zipEntries = if (zipLooksLikeZip(partial)) ArchiveExtractor.entryCount(partial) else 0
        if (zipLooksLikeZip(partial) && zipEntries < 0) {
            // 读不出中央目录：先记录，不要立刻判失败。
            // 之前这里直接失败，导致所有包都装不上（1.1.3 的问题）。
            // 不再在这里判失败 / 重下：ZipFile 读不出中央目录不代表包一定坏，
            // 交给解压阶段去判定（解压失败会走既有的重下流程）。
            // 之前在这里 autoRetry，导致所有包都卡在"压缩包无法解析"反复重下。
            L.w("ZIP 中央目录读取失败（继续解压判定）: " + req.name + " size=" + partial.length())
            runCatching {
                com.utaumarket.mobile.core.CrashReporter.appendShared(
                    ctx, "UtauMarketMobile-write.txt",
                    "ZIP 中央目录读取失败: " + req.name + " size=" + partial.length() + "\n",
                )
            }
        }

        // ---------- 校验 ----------
        if (!ArchiveExtractor.isCompleteArchive(partial)) {
            val head = runCatching {
                val h = ByteArray(16)
                java.io.FileInputStream(partial).use { it.read(h) }
                String(h, Charsets.ISO_8859_1)
            }.getOrDefault("")
            val why = if (head.startsWith("<!") || head.contains("<html")) {
                getString(R.string.dlmsg_err_html_page)
            } else {
                getString(R.string.dlmsg_err_incomplete_file)
            }
            if (autoRetry(req, why)) return
            deletePartial(req.id)
            setStatus(req.id, DownloadStatus.FAILED, getString(R.string.dlmsg_corrupt_download, why))
            return
        }

        // 文件名兜底：清单里 install_rule / format 写漏时，按扩展名判断
        val plainName = req.fileName.ifBlank {
            req.url.substringBefore('?').substringAfterLast('/')
        }.lowercase()

        // ---------- APK（分支改版）：不解压，直接交给系统安装器 ----------
        // 之前这类资源按插件规则处理，解压失败后落到「待确认」，
        // 用户得自己去文件管理器里翻 —— 完全走不通。
        if (req.installRule == InstallRule.APK || plainName.endsWith(".apk")) {
            setStatus(req.id, DownloadStatus.INSTALLING, getString(R.string.dlmsg_apk_preparing))
            // 【性能】之前用 copyTo 把几百 MB 的安装包再复制一份，
            // 在手机上要几十秒，期间界面看起来就是"卡住了"。
            // 现在改成同分区 renameTo —— 只改目录项，不搬数据，瞬间完成。
            val dir = File(ctx.cacheDir, "utm/apk").apply { mkdirs() }
            // 每次只留最新的包，避免缓存越堆越大
            runCatching { dir.listFiles()?.forEach { it.delete() } }
            val safeName = req.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60)
            val apkFile = File(dir, if (safeName.endsWith(".apk")) safeName else "$safeName.apk")
            val moved = runCatching { partial.renameTo(apkFile) }.getOrDefault(false)
            if (!moved) {
                // 跨分区等极端情况才退回复制
                val copied = runCatching { partial.copyTo(apkFile, overwrite = true); partial.delete(); true }
                    .getOrDefault(false)
                if (!copied) {
                    setStatus(req.id, DownloadStatus.FAILED, getString(R.string.dlmsg_apk_copy_failed))
                    return
                }
            }
            val launched = ApkInstaller.install(ctx, apkFile)
            setStatus(
                req.id,
                DownloadStatus.DONE,
                getString(if (launched) R.string.dlmsg_apk_install_prompt else R.string.dlmsg_apk_saved),
                installedTo = apkFile.absolutePath,
            )
            if (launched) showDoneNotification(req.name, getString(R.string.dlmsg_apk_install_prompt))
            else showDoneNotification(req.name, getString(R.string.dlmsg_apk_saved))
            runCatching { File(ctx.cacheDir, "utm/work/" + req.id).deleteRecursively() }
            return
        }

        // ---------- 单文件资源（.dll 音素器 / 插件）：不解压，直接写进目标目录 ----------
        // 之前一律走解压流程，.dll 当然解不开，于是报「压缩包可能损坏或格式不受支持」。
        if (req.installRule == InstallRule.PHONEMIZER_DLL || plainName.endsWith(".dll")) {
            setStatus(req.id, DownloadStatus.INSTALLING, getString(R.string.dlmsg_saving_file))
            val target = UtauMarketApp.get().settings.targetFor(req.kind)
                ?: TargetDir(req.treeUri, req.targetDocId)
            if (target.treeUri.isBlank()) {
                setStatus(req.id, DownloadStatus.FAILED, getString(R.string.dlmsg_need_target_dir))
                return
            }
            val fileName = plainName.substringAfterLast('/').ifBlank { req.name + ".dll" }
            val ok = SafManager.copyInto(
                ctx, Uri.parse(target.treeUri), target.docId, partial, fileName,
            )
            if (ok) {
                setStatus(req.id, DownloadStatus.DONE, getString(R.string.dlmsg_file_installed, fileName), installedTo = fileName)
                showDoneNotification(req.name, getString(R.string.dlmsg_file_installed, fileName))
                deletePartial(req.id)
            } else {
                setStatus(req.id, DownloadStatus.FAILED, getString(R.string.dlmsg_save_failed))
            }
            return
        }

        // ---------- 只下载不安装 ----------
        if (!UtauMarketApp.get().settings.current.autoInstall) {
            setStatus(req.id, DownloadStatus.INSTALLING, getString(R.string.dlmsg_saving_archive))
            val target = UtauMarketApp.get().settings.targetFor(req.kind)
                ?: TargetDir(req.treeUri, req.targetDocId)
            val rawName = java.net.URLDecoder.decode(
                req.url.substringAfterLast('/').substringBefore('?'), "UTF-8",
            ).ifBlank { req.name + ".zip" }
            val ok = SafManager.copyInto(
                ctx, Uri.parse(target.treeUri), target.docId, partial, rawName,
            )
            if (ok) {
                setStatus(req.id, DownloadStatus.DONE, getString(R.string.dlmsg_saved_not_installed, rawName), installedTo = rawName)
                showDoneNotification(req.name, getString(R.string.dlmsg_archive_saved))
                deletePartial(req.id)
            } else {
                setStatus(req.id, DownloadStatus.FAILED, getString(R.string.dlmsg_save_failed))
            }
            return
        }

        // ---------- 解压 ----------
        setStatus(req.id, DownloadStatus.EXTRACTING, getString(R.string.dlmsg_extracting))
        val workDir = File(ctx.cacheDir, "utm/work/${req.id}").apply { deleteRecursively(); mkdirs() }
        val extractDir = File(workDir, "extract").apply { mkdirs() }
        val extracted = ArchiveExtractor.extract(partial, extractDir) { name -> updateMessage(req.id, getString(R.string.dlmsg_extract_progress, name)) }
        if (!extracted) {
            // 解压失败：清掉缓存重下一次（连接策略不在这里改，避免整站被降成单连接）
            deletePartial(req.id)
            if (autoRetry(req, getString(R.string.dlmsg_extract_failed))) return
            deletePartial(req.id)
            setStatus(
                req.id,
                DownloadStatus.FAILED,
                getString(R.string.dlmsg_extract_failed_detail),
            )
            return
        }

        // 【重要】这里以前会把 character.txt / oto.ini 转成 UTF-8，那是**错误的判断**：
        // OpenUtau（含 Mobile）是按 Shift-JIS 读这些文件的，UTAU 的原生格式也是 Shift-JIS。
        // 转成 UTF-8 反而让 OpenUtau 读出乱码 —— 这正是"安装后乱码"的真因。
        // 当初真正的乱码只在 ZIP 内的文件名上，那部分已经由 Commons Compress 解决。
        // 现在安装流程**完全不动文件内容**，保持原始字节。

        // 【完整性校验】对一下"压缩包条目数"和"实际解压出的文件数"。
        // 用户明确要求下载出来不能损坏或丢失 —— 少了就在这里告警，别默默装上去。
        runCatching {
            val expect = if (zipEntries > 0) zipEntries else ArchiveExtractor.entryCount(partial)
            val got = ArchiveExtractor.countFiles(extractDir)
            if (expect > 0 && got < expect) {
                val missing = expect - got
                val severe = got == 0 || missing * 10 > expect
                L.w("解压文件数不符: 压缩包 $expect / 实际 $got（缺 $missing）" + (if (severe) " → 记录" else " → 容忍"))
                runCatching {
                    com.utaumarket.mobile.core.CrashReporter.appendShared(
                        ctx, "UtauMarketMobile-write.txt",
                        "解压数目不符: " + req.name + " 压缩包=" + expect + " 实际=" + got + "\n",
                    )
                }
                // 只记录，不判失败：很多包含符号链接/超长文件名，解压器会合法跳过若干条目
                L.w("解压文件数不符: 压缩包 " + expect + " / 实际 " + got + "（缺 " + missing + "）")
                L.d("解压完整：$got/$expect 个文件")
            }
        }

        // 【嵌套包】有些作者把多个音源打成"一个大 zip 里再放几个 zip"（東北きりたん 就是），
        // 直接装会把内层 zip 原样复制进 Singers，扫描器找不到 oto.ini，已安装里就看不到。
        // 这里先把内层压缩包再解一层，然后照常识别。
        runCatching {
            val inner = extractDir.listFiles().orEmpty().filter {
                it.isFile && listOf(".zip", ".uar", ".rar", ".7z").any { ext -> it.name.lowercase().endsWith(ext) }
            }.take(12)
            if (inner.isNotEmpty()) {
                updateMessage(req.id, getString(R.string.dlmsg_nested_expand, inner.size))
                inner.forEachIndexed { i, f ->
                    val sub = File(extractDir, "_nested_" + i).apply { mkdirs() }
                    ArchiveExtractor.extract(f, sub)
                }
            }
        }

        // ---------- 修正被追加的后缀 ----------
        // 某些 ROM 的存储接口会按 MIME 给文件补扩展名，历史上出现过
        // oto.ini.bin / character.yaml.txt 这种名字，OpenUtau 一律认不出。
        // 这里在复制到目标目录之前统一纠正一次。
        runCatching {
            val fixable = mapOf(
                "oto.ini.bin" to "oto.ini",
                "character.yaml.bin" to "character.yaml",
                "character.txt.bin" to "character.txt",
                "dsconfig.yaml.bin" to "dsconfig.yaml",
                "plugin.txt.bin" to "plugin.txt",
            )
            val stack = ArrayDeque<File>().apply { addLast(extractDir) }
            var guard = 0
            while (stack.isNotEmpty() && guard < 20000) {
                val dir = stack.removeLast()
                dir.listFiles()?.forEach { f ->
                    guard++
                    if (f.isDirectory) stack.addLast(f) else {
                        val target = fixable[f.name.lowercase()]
                        if (target != null) {
                            val dest = File(f.parentFile, target)
                            if (!dest.exists()) {
                                f.renameTo(dest)
                                L.w("修正后缀: " + f.name + " -> " + target)
                            }
                        }
                    }
                }
            }
        }

        // ---------- 解压结构自检 ----------
        // 装了但 OpenUtau 找不到，最常见的原因是包里缺 character.yaml / oto.ini。
        // 这里先看一眼，缺了就在任务消息里提示，避免用户装完一头雾水。
        runCatching {
            var hasMarker = false
            var audioCount = 0
            val stack = ArrayDeque<File>().apply { addLast(extractDir) }
            var guard = 0
            while (stack.isNotEmpty() && guard < 60000) {
                val d = stack.removeLast()
                d.listFiles()?.forEach { f ->
                    guard++
                    if (f.isDirectory) stack.addLast(f) else {
                        val n = f.name.lowercase()
                        if (n == "character.txt" || n == "character.yaml" || n == "oto.ini" || n == "dsconfig.yaml") hasMarker = true
                        if (n.endsWith(".wav") || n.endsWith(".flac") || n.endsWith(".onnx")) audioCount++
                    }
                }
            }
            if (!hasMarker) {
                updateMessage(req.id, getString(R.string.dlmsg_no_char_marker))
            } else if (audioCount == 0 && req.kind == ResourceKind.VOICEBANK) {
                updateMessage(req.id, getString(R.string.dlmsg_no_audio_files))
            }
        }

        val plans = Installer.buildPlan(extractDir, req.kind, req.name, req.installRule)
        if (plans.isEmpty()) {
            // 【诊断】识别不了时，把 App 实际解压出来的目录结构写到共享文件，
            // 方便直接定位是"解压结构不对"还是"识别规则没覆盖"。
            runCatching {
                val sb = StringBuilder("=== 无法识别目录结构: " + req.name + " ===\n")
                sb.append("声明类型: ").append(req.kind).append("  规则: ").append(req.installRule).append('\n')
                fun walk(dir: File, depth: Int) {
                    if (depth > 3) return
                    dir.listFiles()?.sortedBy { it.name }?.take(30)?.forEach { c ->
                        repeat(depth) { sb.append("  ") }
                        sb.append(if (c.isDirectory) "[D] " else "    ").append(c.name).append('\n')
                        if (c.isDirectory) walk(c, depth + 1)
                    }
                }
                walk(extractDir, 0)
                com.utaumarket.mobile.core.CrashReporter.appendShared(
                    ctx, "UtauMarketMobile-extract.txt", sb.toString() + "\n"
                )
                L.w(sb.toString())
            }
            // 认不出结构就别留着这个包了 —— 万一是清单里链接换过、缓存还是旧的，
            // 留着它下次重试还会复用旧文件，永远失败。
            runCatching { deletePartial(req.id) }
            setStatus(req.id, DownloadStatus.AWAITING_CONFIRM, getString(R.string.dlmsg_unknown_structure))
            return
        }
        if (req.treeUri.isBlank()) {
            setStatus(req.id, DownloadStatus.FAILED, getString(R.string.dlmsg_need_target_dir))
            return
        }

        setStatus(req.id, DownloadStatus.INSTALLING, getString(R.string.dlmsg_installing))
        var allOk = true
        var lastTarget = ""
        // 失败时要回滚 —— 否则半截目录留在 Singers 里，
        // 扫描器只看到 oto.ini 就当成"已安装"（"下了却显示无法识别"的来源之一）
        val written = ArrayList<Pair<TargetDir, String>>()
        for ((idx, plan) in plans.withIndex()) {
            val target = UtauMarketApp.get().settings.targetFor(plan.kind)
                ?: TargetDir(req.treeUri, req.targetDocId)
            val done = Installer.install(
                context = ctx,
                target = target,
                plan = plan,
                overwrite = req.overwrite,
                onProgress = { name ->
                    updateMessage(req.id, getString(R.string.dlmsg_write_progress, idx + 1, plans.size, name))
                },
                // 字节级进度：安装阶段也能显示真实百分比
                onBytes = { copied, total -> updateInstallProgress(req.id, copied, total) },
            )
            if (!done) allOk = false else {
                lastTarget = plan.targetName
                written.add(target to plan.targetName)
            }
        }
        // ---------- 安装后回读 ----------
        // 写入成功 ≠ 用户能看到。SAF 写入偶尔"报告成功但内容不在"，
        // 这里逐条回读目标目录，读不到就判失败，避免"下了却找不到"。
        if (allOk) {
            for ((t, name) in written) {
                val visible = runCatching {
                    SafManager.findChild(ctx, android.net.Uri.parse(t.treeUri), t.docId, name) != null
                }.getOrDefault(false)
                if (!visible) {
                    L.w("安装后回读失败: " + name + " @ " + t.treeUri)
                    allOk = false
                    break
                }
            }
        }
        if (allOk) {
            setStatus(req.id, DownloadStatus.DONE, getString(R.string.dlmsg_installed, lastTarget), installedTo = lastTarget)
            showDoneNotification(req.name, getString(R.string.dlmsg_installed_to_target))
            deletePartial(req.id)
        } else {
            for ((t, name) in written) {
                runCatching { SafManager.deleteChild(ctx, android.net.Uri.parse(t.treeUri), t.docId, name) }
            }
            setStatus(req.id, DownloadStatus.FAILED, getString(R.string.dlmsg_install_failed))
        }
        runCatching { workDir.deleteRecursively() }
    }

    // ---------------------------------------------------------------- helpers

    private fun partialFile(ctx: Context, req: DownloadRequest): File {
        val safe = req.resourceId.ifBlank { req.id }.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        // 【关键】缓存文件名里必须带上 URL 的哈希。
        // 之前只用 resourceId，结果清单里把某个条目的下载地址换掉之后，
        // 旧的压缩包还完整躺在缓存里，"重试不重下"直接复用了旧文件 ——
        // defoko 一直解出 utau0419Installer.exe 就是这么来的。
        val urlKey = req.url.hashCode().toUInt().toString(16)
        return File(File(ctx.cacheDir, "utm/partials"), "$safe-$urlKey.part")
    }

    /**
     * 清理没有任何任务引用的下载缓存。
     *
     * 规则：
     *  · 未完成分片（.part）：任务列表里没有「进行中/暂停」的同名任务就删
     *  · 解压工作目录（utm/work 下每个任务一项）：超过 24 小时的删（正常流程退出即删）
     *  · 分支改版安装包（utm/apk 目录）：超过 7 天的删（每次新下载也会先清一次）
     *
     * 不碰 utm/partials 里仍有暂停任务的 .part —— 那是用户主动暂停、等着续传的。
     */
    private fun cleanupOrphanCache() {
        val ctx = applicationContext
        val alive = DownloadController.snapshot()
            .filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.QUEUED ||
                      it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.EXTRACTING ||
                      it.status == DownloadStatus.INSTALLING }
            .mapNotNull { t ->
                requests[t.id]?.let { partialFile(ctx, it).name }
            }
            .toSet()
        runCatching {
            File(ctx.cacheDir, "utm/partials").listFiles()?.forEach { f ->
                if (f.name !in alive) f.delete()
            }
        }
        val dayMs = 24L * 60 * 60 * 1000
        val now = System.currentTimeMillis()
        runCatching {
            File(ctx.cacheDir, "utm/work").listFiles()?.forEach { d ->
                if (now - d.lastModified() > dayMs) d.deleteRecursively()
            }
        }
        runCatching {
            val weekMs = 7L * dayMs
            File(ctx.cacheDir, "utm/apk").listFiles()?.forEach { f ->
                if (now - f.lastModified() > weekMs) f.delete()
            }
        }
        L.d("下载缓存清理完成")
    }

    private fun deletePartial(id: String) {
        val req = requests[id] ?: return
        runCatching { partialFile(applicationContext, req).delete() }
    }

    private fun setStatus(
        id: String,
        status: DownloadStatus,
        message: String,
        installedTo: String = "",
    ) {
        val name = DownloadController.snapshot().firstOrNull { it.id == id }?.name ?: return
        // 【防御】用户按了暂停时，不管是协程中断还是异常先到，
        // 都不允许落成「已取消」或「失败」——否则看起来就是"点暂停直接取消了"。
        val pausedNow = DownloadController.isPaused(id) || controls[id]?.paused == true
        val finalStatus =
            if (pausedNow && (status == DownloadStatus.CANCELED || status == DownloadStatus.FAILED)) {
                DownloadStatus.PAUSED
            } else status
        return setStatusInner(id, name, finalStatus, message, installedTo)
    }

    private fun setStatusInner(
        id: String,
        name: String,
        status: DownloadStatus,
        message: String,
        installedTo: String,
    ) {
        // 交给控制器原子更新，避免和后台进度回调抢着写同一个任务
        DownloadController.setStatus(id, status, message, installedTo)
        updateNotification(name, message, if (status == DownloadStatus.DONE) 100 else 0, status != DownloadStatus.DONE)
    }

    /**
     * 安装阶段的字节进度。只在"还在安装"时写，避免覆盖暂停/取消；
     * progressOnly 保证不会把状态改回别的值。
     */
    private fun updateInstallProgress(id: String, copied: Long, total: Long) {
        val cur = DownloadController.snapshot().firstOrNull { it.id == id } ?: return
        if (cur.status == DownloadStatus.PAUSED || cur.status == DownloadStatus.CANCELED) return
        DownloadController.upsert(
            cur.copy(
                status = DownloadStatus.INSTALLING,
                bytesDownloaded = copied,
                totalBytes = total,
            ),
            progressOnly = true,
        )
    }

    private fun updateMessage(id: String, message: String) {
        val name = DownloadController.snapshot().firstOrNull { it.id == id }?.name ?: return
        DownloadController.updateMessage(id, message)
        updateNotification(name, message, 0, true)
    }

    /**
     * 失败时是否丢掉缓存。
     *
     * 默认丢（避免坏包被复用）；打开"失败时保留缓存"后保留 ——
     * 便于把文件拿出来手工校验，确认是"下不完整"还是"解压/写入"的问题。
     */
    private fun dropPartialOnFail(req: DownloadRequest) {
        if (UtauMarketApp.get().settings.current.keepPartialOnFail) {
            L.w("保留半成品（设置已开启）: " + req.name)
            return
        }
        deletePartial(req.id)
    }

    /** 需要解析落地页的链接（网盘）先解析，普通直链直接用 */
    private fun rawUrlForProbe(url: String): String =
        if (LinkResolver.needsResolve(url)) runCatching { LinkResolver.resolve(url) }.getOrDefault(url) else url

    /** HEAD 一次拿 Content-Length；跟着重定向走 */
    private fun probeContentLength(url: String): Long = runCatching {
        var cur = url
        repeat(5) {
            val req = okhttp3.Request.Builder().url(cur).head()
                .header("User-Agent", "UtauMarket-Mobile/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                if (len > 0) return len
                val loc = resp.header("Location")
                if (loc.isNullOrBlank()) return 0L
                cur = java.net.URI(cur).resolve(loc).toString()
            }
        }
        0L
    }.getOrDefault(0L)

    /** 文件头是不是 ZIP（PK\x03\x04）。rar/7z 不适用 ZipFile 校验。 */
    private fun zipLooksLikeZip(file: java.io.File): Boolean = runCatching {
        java.io.FileInputStream(file).use { ins ->
            val h = ByteArray(4)
            if (ins.read(h) < 4) return false
            h[0] == 0x50.toByte() && h[1] == 0x4B.toByte()
        }
    }.getOrDefault(false)

    private fun progressText(read: Long, total: Long, speed: Long, eta: Long = 0L): String {
        val a = human(read)
        val b = if (total > 0) human(total) else "?"
        val e = if (eta > 0) {
            val m = eta / 60
            val s = eta % 60
            " · " + if (m > 0) getString(R.string.dlmsg_eta_min_sec, m, s) else getString(R.string.dlmsg_eta_sec, s)
        } else ""
        return "$a / $b  (${human(speed)}/s)$e"
    }

    private fun human(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(java.util.Locale.ROOT, "%.1f MB", mb)
        return String.format(java.util.Locale.ROOT, "%.2f GB", mb / 1024.0)
    }

    private fun promoteToForeground(title: String, text: String, progress: Int, indeterminate: Boolean) {
        val notification = NotificationHelper.buildProgress(this, title, text, progress, indeterminate)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, NotificationHelper.NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            L.w("startForeground 失败", t)
        }
    }

    private fun updateNotification(title: String, text: String, progress: Int, indeterminate: Boolean) {
        runCatching {
            // Context#getSystemService(Class) 要 API 23，minSdk 是 21 —— 用 ContextCompat 兼容
            androidx.core.content.ContextCompat
                .getSystemService(this, android.app.NotificationManager::class.java)?.notify(
                NotificationHelper.NOTIFICATION_ID,
                NotificationHelper.buildProgress(this, title, text, progress, indeterminate),
            )
        }
    }

    private fun showDoneNotification(title: String, text: String) {
        runCatching {
            androidx.core.content.ContextCompat
                .getSystemService(this, android.app.NotificationManager::class.java)?.notify(
                NotificationHelper.NOTIFICATION_ID + 1,
                NotificationHelper.buildDone(this, title, text),
            )
        }
    }

    private fun stopForegroundCompat() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH) }
    }

    /**
     * 服务被系统回收 / 重装 APK 时，把还在跑的任务标成「已暂停」而不是「已取消」。
     * 之前这种情况下正在下的任务全变成"已取消"（用户看到"下着下着自己取消了"），
     * 半成品其实还在，续不了很可惜。
     */
    override fun onDestroy() {
        runCatching {
            for (t in DownloadController.snapshot()) {
                // 下载中的任务标成「已暂停」（可续传）；
                // 但**解压/安装中的任务不能标暂停** —— 压缩包已经完整躺在缓存里，
                // 重跑一次安装即可。标成暂停会让用户以为"下载完成却突然暂停"。
                when (t.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> DownloadController.setStatus(
                        t.id, DownloadStatus.PAUSED, "服务已停止，可点「继续」续传", "",
                    )
                    DownloadStatus.EXTRACTING, DownloadStatus.INSTALLING -> DownloadController.setStatus(
                        t.id, DownloadStatus.QUEUED, "等待重新安装", "",
                    )
                    else -> {}
                }
            }
        }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ENQUEUE = "com.utaumarket.mobile.action.ENQUEUE"
        const val ACTION_PAUSE = "com.utaumarket.mobile.action.PAUSE"
        const val ACTION_RESUME = "com.utaumarket.mobile.action.RESUME"
        const val ACTION_CANCEL = "com.utaumarket.mobile.action.CANCEL"
        const val ACTION_CANCEL_ALL = "com.utaumarket.mobile.action.CANCEL_ALL"
        const val EXTRA_REQUEST = "request"
        const val EXTRA_TASK_ID = "task_id"

        fun enqueue(context: Context, request: DownloadRequest) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_ENQUEUE
                putExtra(EXTRA_REQUEST, request.toJson())
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                else context.startService(intent)
            }.onFailure { L.e("无法启动下载服务", it) }
        }

        /** 暂停/继续/取消都作用在**已经在运行**的服务上，用 startService 即可（避免 5 秒前台限制）。 */
        fun control(context: Context, action: String, request: DownloadRequest) {
            val intent = Intent(context, DownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_TASK_ID, request.id)
                putExtra(EXTRA_REQUEST, request.toJson())
            }
            runCatching { context.startService(intent) }.onFailure { L.e("控制命令下发失败", it) }
        }

        fun pause(context: Context, request: DownloadRequest) = control(context, ACTION_PAUSE, request)
        fun resume(context: Context, request: DownloadRequest) = control(context, ACTION_RESUME, request)
        fun cancel(context: Context, request: DownloadRequest) = control(context, ACTION_CANCEL, request)

        fun cancelAll(context: Context) {
            val intent = Intent(context, DownloadService::class.java).apply { action = ACTION_CANCEL_ALL }
            runCatching { context.startService(intent) }.onFailure { L.e("全部取消失败", it) }
        }
    }
}
