package com.utaumarket.mobile.data

import android.content.Context

/**
 * 检测本机是否装了 OpenUtau Mobile。
 *
 * 【为什么要做这个检测】
 * 本 App 只是 OpenUtau Mobile 的**资源管理器**，自己不负责合成。用户装完本 App
 * 却发现自己没有 OpenUtau，是很常见的困惑点（尤其是被朋友安利过来的）。
 * 所以启动时探一下，没装就引导去 GitHub 下载。
 *
 * 注意：这个 App 里出现了三个不同的宿主包名 ——
 *   · pers.vocoder712.openutaumobile      官方版 & styisme 的 DLL 支持分支（同一包名，不能共存）
 *   · com.mysticild.openutaumobileclassic  MysticILD 的 Classic 改版（不同包名，可以共存）
 * 只要装了其中**任意一个**，就算"已安装"。
 */
object HostAppDetector {

    /** 已知的 OpenUtau Mobile 包名（官方 + 各改版） */
    /**
     * 需要检测的宿主包名。
     *
     * 官方版与「DLL 支持版」共用 pers.vocoder712.openutaumobile；
     * Classic 改版是独立包名，可以和官方版共存。
     */
    val FORK_PACKAGES: List<String> = listOf(
        "pers.vocoder712.openutaumobile",
        "com.mysticild.openutaumobileclassic",
    )

    private val PACKAGES = listOf(
        "pers.vocoder712.openutaumobile",
        "com.mysticild.openutaumobileclassic",
    )

    /** 官方下载页（GitHub Releases） */
    const val DOWNLOAD_URL = "https://github.com/vocoder712/OpenUtauMobile/releases/latest"

    /** 返回已安装的宿主包名列表；空列表表示一个都没装 */
    fun installedPackages(context: Context): List<String> {
        val pm = context.packageManager ?: return emptyList()
        return PACKAGES.filter { pkg ->
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            }.isSuccess
        }
    }

    fun isInstalled(context: Context): Boolean = installedPackages(context).isNotEmpty()
}
