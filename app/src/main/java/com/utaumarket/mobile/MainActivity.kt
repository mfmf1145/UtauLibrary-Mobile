package com.utaumarket.mobile

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utaumarket.mobile.core.CrashReporter
import com.utaumarket.mobile.ui.AppRoot
import com.utaumarket.mobile.ui.theme.OuTheme
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        // 这里就要把语言定下来 —— 晚于这里设置，界面已经用系统语言渲染过一轮了。
        val prefs = newBase.getSharedPreferences("utaumarket_settings", android.content.Context.MODE_PRIVATE)
        val tag = prefs.getString("app_language", "") ?: ""
        com.utaumarket.mobile.core.AppLocale.overrideTag = tag
        if (tag.isBlank()) {
            super.attachBaseContext(newBase)
            return
        }
        val locale = java.util.Locale.forLanguageTag(tag)
        // 只用 createConfigurationContext 换资源语言是不够的：进程默认 Locale 还是系统语言，
        // 「简体中文下显示中文译名」这类判断会错位。这里把进程 Locale 一起同步。
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 上次启动的崩溃日志：用纯 Android View 显示，保证一定看得到（不依赖 Compose 能否正常工作）
        val previous = CrashReporter.lastCrash(this)
        if (previous != null) {
            showPlainView(previous)
            return
        }

        try {
            enableEdgeToEdge()
            val settings = UtauMarketApp.get().settings
            setContent {
                val state by settings.state.collectAsStateWithLifecycle()
                OuTheme(
                    themeMode = state.themeMode,
                    scheme = com.utaumarket.mobile.ui.theme.ColorScheme.fromId(state.colorScheme),
                ) {
                    // 网络状态指示样式（圆点 / emoji）由设置决定，这里下发给所有卡片
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.utaumarket.mobile.ui.screens.LocalNetIndicatorStyle provides state.netIndicatorStyle,
                    ) {
                        Box(Modifier.fillMaxSize().background(OuTheme.colors.background)) {
                            AppRoot()
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            CrashReporter.write(this, t)
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            showPlainView(sw.toString())
        }
    }

    /** 纯 Android View 的错误页：直角、深色，底色与 OpenUtau 保持一致。 */
    private fun showPlainView(body: String) {
        val bodyView = TextView(this).apply {
            this.text = body
            setTextColor(Color.parseColor("#CDCAD4"))
            textSize = 11f
            setPadding(32, 48, 32, 48)
            setTextIsSelectable(true)
            typeface = Typeface.MONOSPACE
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#26232C"))
            addView(bodyView)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#26232C"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            gravity = Gravity.START
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(Button(this@MainActivity).apply {
                this.text = getString(R.string.crash_clear_restart)
                setOnClickListener {
                    CrashReporter.clear(this@MainActivity)
                    finish()
                    startActivity(Intent(this@MainActivity, MainActivity::class.java))
                }
            })
        }
        setContentView(root)
    }
}
