package com.utaumarket.mobile.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.utaumarket.mobile.core.L
import java.io.File

/**
 * 把下载好的 APK 交给系统安装器。
 *
 * 【为什么需要】「分支改版」下载的是第三方魔改的 OpenUtau 本体 APK。
 * 之前按插件规则处理，解压失败就落到「待确认」，用户只能自己去文件管理器翻 ——
 * 这个 App 的定位是"一站式"，不该把这一步丢给用户。
 *
 * 注意：
 *  · Android 8+ 需要 REQUEST_INSTALL_PACKAGES 权限（已在 Manifest 声明）
 *  · 部分系统还会要求用户为「UtauMarket」单独打开「安装未知应用」开关，
 *    此时安装器会自己弹设置页，属于系统行为，App 无法绕过
 *  · 用 FileProvider 把私有目录里的文件以 content:// 暴露出去，
 *    直接传 file:// 在 Android 7+ 会触发 FileUriExposedException
 */
object ApkInstaller {

    /** @return true = 已成功拉起系统安装器；false = 拉不起来（会记日志） */
    fun install(context: Context, apk: File): Boolean = runCatching {
        if (!apk.isFile || apk.length() <= 0L) return false
        val uri: Uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // 分发给所有能处理安装的应用（通常就是系统安装器）
        context.startActivity(intent)
        true
    }.onFailure { L.w("拉起 APK 安装器失败", it) }.getOrDefault(false)
}
