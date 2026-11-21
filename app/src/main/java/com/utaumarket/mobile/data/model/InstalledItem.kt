package com.utaumarket.mobile.data.model

/** 已安装资源（通过 SAF 扫描目标目录得到） */
data class InstalledItem(
    val uri: String,
    val parentUri: String,
    /** 显示名：优先 character.txt / character.yaml 里的 name=，没有才用目录名 */
    val name: String,
    /** 真实目录名（删除、打开文件夹要用它） */
    val folderName: String = name,
    val kind: ResourceKind,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val childCount: Int,
    /** 从 character.txt / character.yaml / dsconfig.yaml / plugin.txt 读到的信息 */
    val author: String = "",
    val version: String = "",
    val engine: String = "",
    val language: String = "",
    val recordingType: String = "",
    val detailLines: List<Pair<String, String>> = emptyList(),
    /**
     * 歌手头像的 SAF 文档 URI（空串 = 没有）。
     *
     * 原理和 UTAU / OpenUtau 完全一样：声库目录里本来就有一个图标文件
     * （icon.png / icon.bmp / character.txt 里的 image= ），直接读本地文件，
     * 不需要联网。
     */
    val iconUri: String = "",
)

/** 一个下载/安装任务 */
/**
 * 「已安装」页里「分支改版」这一栏用的数据。
 *
 * 分支改版是第三方修改的 OpenUtau 本体，安装后是**一个 APK 应用**，
 * 不在声库目录里，所以不能走 InstalledScanner，得问系统。
 */
data class InstalledFork(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isFork: Boolean,
    val lastUpdateTime: Long,
)

data class DownloadTask(
    val id: String,
    val resourceId: String,
    val name: String,
    val kind: ResourceKind,
    val url: String,
    val fileName: String,
    val installRule: InstallRule,
    val targetSubDir: String,
    /** true = 这是「分支改版」（第三方魔改宿主 APK），下载列表里不要显示成「插件」 */
    val fork: Boolean = false,
    val expectedSize: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBps: Long = 0L,
    /** 预计剩余秒数；0 表示还无法估算 */
    val etaSeconds: Long = 0L,
    val message: String = "",
    val error: String = "",
    val tempPath: String = "",
    val installedTo: String = "",
    /** 用于「继续下载」时重建请求 */
    val treeUri: String = "",
    val targetDocId: String = "",
    val overwrite: Boolean = true,
    /** 解压后发现的候选根目录，用于 manual 规则的确认弹窗 */
    val pendingRoots: List<String> = emptyList(),
    /** 最近一次状态变更的时间戳（毫秒），用于超时看门狗 */
    val statusSince: Long = 0L,
)

/** 0f..1f；总长度未知时返回 0 */
val DownloadTask.progress: Float
    get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

/** 「正在进行中」——**不包括已暂停**，否则会显示「进行中 1 · 已暂停 1」这种自相矛盾的计数 */
val DownloadTask.isActive: Boolean
    get() = status == DownloadStatus.QUEUED || status == DownloadStatus.DOWNLOADING ||
        status == DownloadStatus.EXTRACTING || status == DownloadStatus.INSTALLING

/** 「还在清单里、需要用户操心」——包含已暂停和等待确认 */
val DownloadTask.isPending: Boolean
    get() = isActive || status == DownloadStatus.PAUSED ||
        status == DownloadStatus.AWAITING_CONFIRM

enum class DownloadStatus(val id: String) {
    QUEUED("queued"),
    DOWNLOADING("downloading"),
    EXTRACTING("extracting"),
    INSTALLING("installing"),
    PAUSED("paused"),
    AWAITING_CONFIRM("awaiting_confirm"),
    DONE("done"),
    FAILED("failed"),
    CANCELED("canceled");
}
