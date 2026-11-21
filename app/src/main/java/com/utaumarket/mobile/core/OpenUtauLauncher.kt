package com.utaumarket.mobile.core

import android.content.Context
import android.content.Intent

/** 启动 OpenUtau Mobile 的唯一入口。失败原因写进 a11y.log，方便排查。 */
object OpenUtauLauncher {
    private const val PKG = "pers.vocoder712.openutaumobile"
    /** MAUI 生成的 Activity 名；包可见性拿不到 launchIntent 时用它显式启动 */
    private const val ACTIVITY = "crc6436b81bded55779df.MainActivity"

    fun log(context: Context, msg: String) {
        runCatching {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            java.io.File(dir, "a11y.log").appendText(
                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT).format(java.util.Date()) +
                    "  LAUNCH " + msg + "\n",
            )
        }
    }

    fun launch(context: Context): Boolean {
        val pm = context.packageManager
        val li = runCatching { pm.getLaunchIntentForPackage(PKG) }.getOrNull()
        log(context, "getLaunchIntentForPackage=" + (li != null))
        if (li != null) {
            li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { context.startActivity(li); true }.getOrElse {
                log(context, "startActivity(intent) 失败: " + it.javaClass.simpleName + " " + it.message)
                false
            }
            if (ok) return true
        }
        // 退路：显式 Component（不依赖包可见性）
        val explicit = Intent().setClassName(PKG, PKG + "." + ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        log(context, "fallback component=" + explicit.component?.flattenToShortString())
        return runCatching { context.startActivity(explicit); true }.getOrElse {
            log(context, "startActivity(component) 失败: " + it.javaClass.simpleName + " " + it.message)
            false
        }
    }
}
