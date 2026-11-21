package com.utaumarket.mobile.core

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃自诊断。
 *
 * 目标：无论启动阶段哪里抛异常，都要把堆栈同时写到
 *   1) 应用内部 filesDir/crash-last.txt（下次启动可直接在界面上看到）
 *   2) 应用外部专属目录 getExternalFilesDir()/crash-last.txt
 *   3) 共享存储 Download/UtauMarketMobile-crash.txt（API 29+ 用 MediaStore，无需权限）
 * 这样即使程序闪退，也能拿到真正的异常堆栈。
 */
object CrashReporter {

    const val SHARED_FILE_NAME = "UtauMarketMobile-crash.txt"
    private const val LAST_FILE = "crash-last.txt"

    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(app, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun write(context: Context, throwable: Throwable) {
        val text = buildReport(context, throwable)
        runCatching { File(context.filesDir, LAST_FILE).writeText(text) }
        runCatching {
            context.getExternalFilesDir(null)?.let { File(it, LAST_FILE).writeText(text) }
        }
        runCatching { writeToDownloads(context, text) }
        L.e("UNCAUGHT EXCEPTION", throwable)
    }

    /** 追加写一份共享存储里的日志，方便开发者/用户回读（API 29+ 用 MediaStore，无需权限） */
    fun appendShared(context: Context, fileName: String, text: String) {
        runCatching {
            val old = readShared(context, fileName)
            writeToDownloadsNamed(context, fileName, old + text)
        }
    }

    private fun readShared(context: Context, fileName: String): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            resolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?", arrayOf(fileName), null,
            )?.use { c ->
                if (c.moveToNext()) {
                    val id = c.getLong(0)
                    val uri = android.content.ContentUris.withAppendedId(collection, id)
                    resolver.openInputStream(uri)?.use { String(it.readBytes()) }
                } else null
            } ?: ""
        } else ""
    }.getOrDefault("")

    /**
     * 读取上次崩溃记录。
     *
     * 只认**当前版本**写的记录：旧版本留下的崩溃会被丢弃。
     * 否则修好之后，用户每次启动都还会看到那张"崩溃"页。
     */
    fun lastCrash(context: Context): String? = runCatching {
        val f0 = File(context.filesDir, LAST_FILE)
        if (f0.isFile) {
            val head = runCatching { f0.readText().take(200) }.getOrDefault("")
            val ver = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            // 崩溃记录头部写的是 "1.0.2 (2)" 这种格式，直接看版本号是否出现即可
            if (ver.isNotBlank() && head.isNotBlank() && !head.contains(ver)) {
                f0.delete()
                return null
            }
        }
        _lastCrash(context)
    }.getOrNull()

    private fun _lastCrash(context: Context): String? = runCatching {
        val f = File(context.filesDir, LAST_FILE)
        if (f.exists()) f.readText().take(20_000) else null
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, LAST_FILE).delete() }
    }

    private fun buildReport(context: Context, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val version = runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pi.versionName} (${pi.versionCode})"
        }.getOrDefault("?")
        return buildString {
            appendLine("UtauLibrary Mobile 崩溃报告")
            appendLine("时间: $time")
            appendLine("包名: ${context.packageName}  版本: $version")
            appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine()
            append(sw.toString())
        }
    }

    private fun writeToDownloads(context: Context, text: String) =
        writeToDownloadsNamed(context, SHARED_FILE_NAME, text)

    private fun writeToDownloadsNamed(context: Context, fileName: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            runCatching {
                resolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                    arrayOf(fileName),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        resolver.delete(
                            collection,
                            "${MediaStore.MediaColumns._ID}=?",
                            arrayOf(id.toString()),
                        )
                    }
                }
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(collection, values) ?: return
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (dir != null && (dir.exists() || dir.mkdirs())) {
                File(dir, fileName).writeBytes(bytes)
            }
        }
    }
}
