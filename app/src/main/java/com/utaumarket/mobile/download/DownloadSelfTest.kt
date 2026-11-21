package com.utaumarket.mobile.download

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.utaumarket.mobile.core.CrashReporter
import com.utaumarket.mobile.core.L
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 下载自检：用**和正式下载完全相同的代码路径**跑 7 秒，分别测单连接与 4 连接，
 * 把每秒速率写进 Download/UtauMarketMobile-download.log，方便定位"为什么比浏览器慢"。
 */
object DownloadSelfTest {

    const val LOG_FILE = "UtauMarketMobile-download.log"
    private const val TEST_URL =
        "https://kasaneteto.jp/assets/download/utau/TETO-jakuongen210725.zip"
    private const val RUN_MS = 7_000L

    fun runOnce(context: Context, force: Boolean = false) {
        val prefs = context.getSharedPreferences("utaumarket_settings", Context.MODE_PRIVATE)
        if (!force && prefs.getBoolean("selftest_done", false)) return
        prefs.edit { putBoolean("selftest_done", true) }
        Thread { runCatching { run(context) }.onFailure { L.w("自检失败", it) } }
            .apply { isDaemon = true; name = "utm-selftest" }
            .start()
    }

    private fun run(context: Context) {
        val sb = StringBuilder()
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        sb.appendLine("=== 下载自检 $ts ===")
        sb.appendLine("设备: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("被测 URL: $TEST_URL")

        for (conns in listOf(1, 4)) {
            val dir = File(context.cacheDir, "utm/selftest").apply { mkdirs() }
            val f = File(dir, "c$conns.bin")
            f.delete()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            var lastB = 0L
            var lastT = System.currentTimeMillis()
            val samples = StringBuilder()
            val t0 = System.currentTimeMillis()
            val res = kotlinx.coroutines.runBlocking { ParallelDownloader.download(
                client = client,
                url = TEST_URL,
                dest = f,
                expectedSize = 0,
                connections = conns,
                onProgress = { read, _ ->
                    val now = System.currentTimeMillis()
                    if (now - lastT >= 1000) {
                        val inst = if (now > lastT) (read - lastB) * 1000 / 1024 / (now - lastT) else 0
                        samples.appendLine("     ${inst} KB/s   累计 ${read / 1048576} MB")
                        lastB = read
                        lastT = now
                    }
                },
                shouldStop = {
                    if (System.currentTimeMillis() - t0 > RUN_MS) ParallelDownloader.Stop.CANCELED
                    else ParallelDownloader.Stop.NONE
                },
                tag = "selftest$conns",
            ) }
            val dt = System.currentTimeMillis() - t0
            val bytes = f.length()
            val avg = if (dt > 0) bytes / 1024 * 1000 / dt else 0
            sb.appendLine("[并发=$conns] 用时 ${dt}ms  下载 ${bytes / 1048576.0} MB  平均 ${avg} KB/s  stopped=${res.stopped}")
            sb.append(samples)
            runCatching { f.delete() }
        }
        sb.appendLine("=== 自检结束 ===")
        CrashReporter.appendShared(context, LOG_FILE, sb.toString())
        L.d(sb.toString())
    }
}
