package com.utaumarket.mobile.download

import android.content.Context
import android.widget.Toast
import com.utaumarket.mobile.data.model.DownloadTask
import com.utaumarket.mobile.data.model.InstallRule
import com.utaumarket.mobile.data.model.ResourceItem
import com.utaumarket.mobile.data.model.ResourceKind
import com.utaumarket.mobile.data.prefs.SettingsStore
import java.util.UUID

/** 把资源条目转换成下载请求并入队；暂停/继续/取消也从这里转发。 */
object DownloadQueueFacade {

    fun start(
        context: Context,
        settings: SettingsStore,
        item: ResourceItem,
        overwrite: Boolean,
        useMirror: Boolean,
    ): String? {
        val target = settings.targetFor(item.kind) ?: return null
        val tree = target.treeUri
        if (tree.isBlank()) return null
        val url = if (useMirror && item.mirrorUrl.isNotBlank()) item.mirrorUrl else item.url
        if (url.isBlank()) return null
        val fileName = item.fileName.ifBlank { guessFileName(url, item.name) }
        val request = DownloadRequest(
            id = UUID.randomUUID().toString(),
            resourceId = item.id,
            name = item.name,
            kind = item.kind,
            url = url,
            fileName = fileName,
            installRule = item.installRule,
            targetSubDir = item.targetSubDir,
            treeUri = tree,
            targetDocId = target.docId,
            expectedSize = item.fileSize,
            overwrite = overwrite,
            fork = item.isFork,
        )
        DownloadService.enqueue(context, request)
        return request.id
    }

    fun fromTask(t: DownloadTask): DownloadRequest = DownloadRequest(
        id = t.id,
        resourceId = t.resourceId,
        name = t.name,
        kind = t.kind,
        url = t.url,
        fileName = t.fileName,
        installRule = t.installRule,
        targetSubDir = t.targetSubDir,
        treeUri = t.treeUri,
        targetDocId = t.targetDocId,
        expectedSize = if (t.totalBytes > 0) t.totalBytes else t.expectedSize,
        overwrite = t.overwrite,
        fork = t.fork,
    )

    fun pause(context: Context, task: DownloadTask) = DownloadService.pause(context, fromTask(task))
    fun resume(context: Context, task: DownloadTask) = DownloadService.resume(context, fromTask(task))
    fun cancel(context: Context, task: DownloadTask) = DownloadService.cancel(context, fromTask(task))
    fun cancelAll(context: Context) = DownloadService.cancelAll(context)

    private fun guessFileName(url: String, fallback: String): String {
        val clean = url.substringBefore('?').substringBefore('#')
        val last = clean.substringAfterLast('/', "")
        val name = if (last.contains('.')) last else fallback
        val ext = when {
            url.lowercase().contains(".uar") -> "uar"
            url.lowercase().contains(".rar") -> "rar"
            url.lowercase().contains(".7z") -> "7z"
            url.lowercase().endsWith(".tar.gz") -> "tar.gz"
            else -> "zip"
        }
        return if (name.contains('.')) name else "$name.$ext"
    }
}
