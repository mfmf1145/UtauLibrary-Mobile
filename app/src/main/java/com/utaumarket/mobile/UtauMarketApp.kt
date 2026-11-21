package com.utaumarket.mobile

import android.app.Application
import com.utaumarket.mobile.core.CrashReporter
import com.utaumarket.mobile.data.manifest.ManifestRepository
import com.utaumarket.mobile.data.prefs.SettingsStore
import com.utaumarket.mobile.download.DownloadController

class UtauMarketApp : Application() {

    val settings: SettingsStore by lazy { SettingsStore(this) }
    val manifest: ManifestRepository by lazy { ManifestRepository(this) }

    override fun onCreate() {
        // 崩溃自诊断必须最先安装，才能捕获启动阶段的任何异常
        CrashReporter.install(this)
        super.onCreate()
        instance = this
        // 语言标记：Activity 还没起来时（后台服务 / 自检）也能判断对界面语言
        runCatching {
            com.utaumarket.mobile.core.AppLocale.overrideTag =
                getSharedPreferences("utaumarket_settings", MODE_PRIVATE).getString("app_language", "") ?: ""
        }
        runCatching { DownloadController.init(this) }
        // 【首屏预热】清单是 1 MB 的 JSON，首次进入声库页才解析会卡 200~400ms。
        // 启动时丢到后台线程先解析一次，ManifestRepository 内部有 cache，
        // 用户真正点进去时通常已经就绪。
        runCatching {
            Thread({
                runCatching { manifest.load() }
            }, "manifest-warmup").apply { isDaemon = true }.start()
        }
        runCatching { com.utaumarket.mobile.download.ParallelDownloader.init(this) }
        // 1.1.3 曾经因为误判把整站标成"只能单连接"，导致下载掉到几十 KB/s。
        // 升级到这一版时清一次记忆，让所有站点重新用多连接。
        runCatching {
            val p = getSharedPreferences("utm_boot", MODE_PRIVATE)
            if (!p.getBoolean("host_memory_cleared_v2", false)) {
                com.utaumarket.mobile.download.ParallelDownloader.clearHostMemory()
                p.edit().putBoolean("host_memory_cleared_v2", true).apply()
            }
        }
        // 首次启动跑一次下载自检（结果写到 Download/UtauMarketMobile-download.log）
        runCatching { com.utaumarket.mobile.download.DownloadSelfTest.runOnce(this) }
        // 一次性清理：旧版本多线程分块留下的"预分配空洞文件"会让解压一直失败
        runCatching {
            val prefs = getSharedPreferences("utaumarket_settings", MODE_PRIVATE)
            if (!prefs.getBoolean("partial_purge_v3", false)) {
                // 旧版多线程预分配留下的"空洞文件"会让解压永远失败，这里彻底清一次
                java.io.File(cacheDir, "utm/partials").deleteRecursively()
                java.io.File(cacheDir, "utm/work").deleteRecursively()
                prefs.edit().putBoolean("partial_purge_v3", true).apply()
            }
        }
    }

    companion object {
        lateinit var instance: UtauMarketApp
            private set
        fun get(): UtauMarketApp = instance
    }
}
