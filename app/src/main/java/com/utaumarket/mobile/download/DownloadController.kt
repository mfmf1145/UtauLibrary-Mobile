package com.utaumarket.mobile.download

import android.content.Context
import com.utaumarket.mobile.data.model.DownloadStatus
import com.utaumarket.mobile.data.model.DownloadTask
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 下载队列的内存状态（进程内单例）。
 * 真正的网络与解压工作由前台服务 [DownloadService] 执行。
 */
object DownloadController {

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    /** 终点态：用户已暂停/取消，或已完成/失败 */
    private val TERMINAL = setOf(
        DownloadStatus.PAUSED, DownloadStatus.CANCELED,
        DownloadStatus.DONE, DownloadStatus.FAILED,
    )
    private val IN_FLIGHT = setOf(
        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING,
        DownloadStatus.EXTRACTING, DownloadStatus.INSTALLING,
    )

    /** 任务记录文件（JSON 数组）。放 filesDir 下，卸载即清。 */
    private fun storeFile(context: Context) =
        java.io.File(context.filesDir, "download_tasks.json")

    /**
     * 应用启动时恢复任务记录。
     *
     * 之前这些记录只存在内存里，退出程序就全没了 —— 用户看到「下载记录被清空」。
     * 现在落盘，并把上次没跑完的「下载中 / 排队 / 解压」统一改成**已暂停**，
     * 这样重进后可以点「继续」接着下，而不是显示成"正在下载"却毫无动静。
     */
    fun init(context: Context) {
        appCtx = context.applicationContext
        runCatching {
            val f = storeFile(context)
            if (!f.isFile) return@runCatching
            val arr = org.json.JSONArray(f.readText())
            val restored = ArrayList<DownloadTask>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val t = taskFromJson(o) ?: continue
                // 解压/安装中被打断的任务：压缩包已经完整在缓存里，重跑安装即可，
                // 恢复成「排队中」而不是「已暂停」—— 后者会让用户看到"下载完成却突然暂停"。
                val st = when {
                    t.status == DownloadStatus.EXTRACTING || t.status == DownloadStatus.INSTALLING ->
                        DownloadStatus.QUEUED
                    t.status in IN_FLIGHT -> DownloadStatus.PAUSED
                    else -> t.status
                }
                // 【关键】恢复成「已暂停」的任务必须同时把锁补上。
                // 否则进程一重启锁就没了，服务会把队列里残留的请求接着跑，
                // 表现就是「暂停 → 几秒后又自己变回下载中 → 卡住」（用户录屏复现）。
                if (st == DownloadStatus.PAUSED) pausedIds.add(t.id)
                if (st == DownloadStatus.CANCELED) canceledIds.add(t.id)
                restored.add(
                    if (st != t.status) t.copy(status = st, speedBps = 0, message = "")
                    else t.copy(speedBps = 0)
                )
            }
            // 只在内存为空时恢复；否则以内存为准（内存才是活的），避免旧文件覆盖新状态
            if (restored.isNotEmpty() && _tasks.value.isEmpty()) {
                _tasks.value = restored
                // 恢复后立刻把当前状态写回去，保证文件与内存一致
                persist()
            }
        }
    }

    /**
     * 落盘用的单线程执行器。
     *
     * 【为什么不能用 GlobalScope.launch 一把梭】每次状态变化都起一个协程写同一个文件，
     * 多个写入并发 → **后发起的可能先完成** → 文件里留下的是旧状态。
     * 进程重启后 init() 把旧状态恢复回来，界面就"回弹"了（自检时实测到
     * 已取消 → 排队中 → 已暂停 乱跳）。
     *
     * 这里保证：串行写、且永远写**最新**快照。
     */
    private val persistExec = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "utm-persist").apply { isDaemon = true }
    }
    @Volatile private var persistPending = false

    private fun persist() {
        val ctx = appCtx ?: return
        // 已经在排队写入了就不再叠加 —— 单线程执行器会在轮到时读取最新值
        if (persistPending) return
        persistPending = true
        runCatching {
            persistExec.execute {
                try {
                    val data = _tasks.value          // ← 执行时再取，保证是最新的
                    val arr = org.json.JSONArray()
                    for (t in data) arr.put(taskToJson(t))
                    storeFile(ctx).writeText(arr.toString())
                } catch (_: Throwable) {
                } finally {
                    persistPending = false
                }
            }
        }.onFailure { persistPending = false }
    }

    private var appCtx: Context? = null

    private fun taskToJson(t: DownloadTask): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", t.id); put("resourceId", t.resourceId); put("name", t.name)
        put("kind", t.kind.name); put("url", t.url); put("fileName", t.fileName)
        put("installRule", t.installRule.name); put("targetSubDir", t.targetSubDir)
        put("expectedSize", t.expectedSize); put("status", t.status.name)
        put("bytesDownloaded", t.bytesDownloaded); put("totalBytes", t.totalBytes)
        put("message", t.message); put("error", t.error)
        put("tempPath", t.tempPath); put("installedTo", t.installedTo)
        put("treeUri", t.treeUri); put("targetDocId", t.targetDocId)
        put("fork", t.fork)
    }

    private fun taskFromJson(o: org.json.JSONObject): DownloadTask? = runCatching {
        DownloadTask(
            id = o.getString("id"),
            resourceId = o.optString("resourceId"),
            name = o.optString("name"),
            kind = com.utaumarket.mobile.data.model.ResourceKind.valueOf(o.optString("kind", "VOICEBANK")),
            url = o.optString("url"),
            fileName = o.optString("fileName"),
            installRule = com.utaumarket.mobile.data.model.InstallRule.valueOf(o.optString("installRule", "MANUAL")),
            targetSubDir = o.optString("targetSubDir"),
            expectedSize = o.optLong("expectedSize"),
            status = com.utaumarket.mobile.data.model.DownloadStatus.valueOf(o.optString("status", "PAUSED")),
            bytesDownloaded = o.optLong("bytesDownloaded"),
            totalBytes = o.optLong("totalBytes"),
            message = o.optString("message"),
            error = o.optString("error"),
            tempPath = o.optString("tempPath"),
            installedTo = o.optString("installedTo"),
            treeUri = o.optString("treeUri"),
            targetDocId = o.optString("targetDocId"),
            fork = o.optBoolean("fork", false),
        )
    }.getOrNull()

    fun snapshot(): List<DownloadTask> = _tasks.value

    /**
     * 被用户显式暂停的任务。暂停后**任何**非终点状态的写入都会被拦下，
     * 直到用户点「继续」才解除。
     *
     * 之前只靠 progressOnly 防御，但后台还有别的 setStatus(DOWNLOADING) 路径，
     * 会导致卡片"暂停 → 又跳回下载中 → 再暂停"，看起来像卡了几下又自己下起来。
     */
    private val pausedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun markPaused(id: String) { pausedIds.add(id) }
    /**
     * 解除终止锁（用户点「继续」时调用）。
     *
     * 【必须同时清两个】之前只清 pausedIds，canceledIds 一直留着 ——
     * 于是"暂停→继续"会走进 cancel 分支，任务直接变成已取消（用户报的 bug）。
     * 继续 = 明确要跑，两把锁都要放下。
     */
    fun clearPaused(id: String) {
        pausedIds.remove(id)
        canceledIds.remove(id)
    }

    fun clearCanceled(id: String) { canceledIds.remove(id) }
    /** 界面用它判断「是否已被用户暂停」——不依赖服务回调是否及时 */
    fun isPaused(id: String): Boolean = id in pausedIds

    /**
     * 已被用户取消的任务。
     * 和暂停锁同理：取消后任何"还在动"的状态写回都必须被拦下，
     * 否则后台回调会把界面刷回"下载中"（用户报的"取消也会回弹"）。
     */
    private val canceledIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun markCanceled(id: String) { canceledIds.add(id); pausedIds.remove(id) }
    fun isCanceled(id: String): Boolean = id in canceledIds
    /** 已终止（暂停或取消）——后台任何"继续跑"的动作都要看这个 */
    fun isTerminated(id: String): Boolean = id in pausedIds || id in canceledIds

    /**
     * 写入任务状态。
     *
     * @param progressOnly 来自后台读取循环的进度更新。它和用户的暂停/取消是**并发**的：
     *   回调可能读到暂停前的旧状态，写回来就把界面又刷成"下载中"，
     *   表现就是卡片先闪一下再暂停。所以这类更新不允许覆盖终点态。
     */
    fun upsert(task: DownloadTask, progressOnly: Boolean = false) {
        // 已显式暂停/取消：拒绝任何"还在动"的状态写回（进度数字也不刷，避免视觉回弹）
        if (isTerminated(task.id) && task.status in IN_FLIGHT) return
        _tasks.update { list ->
            val i = list.indexOfFirst { it.id == task.id }
            if (i < 0) {
                listOf(task) + list
            } else {
                val old = list[i]
                // 【防回弹】字节数只能变大。暂停瞬间会先有一次乐观写入（较大值），
                // 随后后台回调带着暂停前的较小值写回来，界面就会"倒退一格"。
                // 这里直接取 max，视觉上永远向前。
                val bounced = task.copy(
                    bytesDownloaded = maxOf(old.bytesDownloaded, task.bytesDownloaded),
                )
                val merged = if (progressOnly && old.status in TERMINAL && bounced.status in IN_FLIGHT) {
                    // 保留终点状态和提示文字，但进度数字照常刷新
                    bounced.copy(
                        status = old.status,
                        message = old.message,
                        installedTo = old.installedTo,
                    )
                } else {
                    bounced
                }
                list.toMutableList().also { it[i] = merged }
            }
        }
        persist()
    }

    /** 原子地改状态，避免"读旧值 → 写回"之间被进度回调插队 */
    fun setStatus(id: String, status: DownloadStatus, message: String, installedTo: String = "") {
        if (status in IN_FLIGHT) {
            if (isTerminated(id)) return          // 用户已暂停/取消，后台不许再写"在动"
            pausedIds.remove(id); canceledIds.remove(id)
        } else if (status == DownloadStatus.PAUSED) {
            pausedIds.add(id); canceledIds.remove(id)
        } else if (status == DownloadStatus.CANCELED) {
            canceledIds.add(id); pausedIds.remove(id)
        } else if (status == DownloadStatus.DONE) {
            pausedIds.remove(id); canceledIds.remove(id)
        }
        _tasks.update { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i < 0) {
                list
            } else {
                list.toMutableList().also {
                    val cur = it[i]
                    it[i] = cur.copy(
                        status = status,
                        message = message,
                        installedTo = installedTo.ifBlank { cur.installedTo },
                        error = if (status == DownloadStatus.FAILED) message else "",
                        speedBps = if (status == DownloadStatus.DOWNLOADING) cur.speedBps else 0L,
                        statusSince = System.currentTimeMillis(),
                    )
                }
            }
        }
        persist()
    }

    fun updateMessage(id: String, message: String) {
        _tasks.update { list ->
            val i = list.indexOfFirst { it.id == id }
            if (i < 0) list
            else list.toMutableList().also { it[i] = it[i].copy(message = message) }
        }
        persist()
    }

    fun remove(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clearFinished() {
        _tasks.update { list ->
            list.filter {
                // 只保留"还在动"的任务。
                // 之前这里漏了 AWAITING_CONFIRM（"无法自动识别目录结构"就是这个状态），
                // 于是那条记录既清不掉、也没有别的删除入口 —— 用户怎么点都删不掉。
                it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.EXTRACTING ||
                    it.status == DownloadStatus.INSTALLING ||
                    it.status == DownloadStatus.PAUSED
            }
        }
        persist()
    }
}
