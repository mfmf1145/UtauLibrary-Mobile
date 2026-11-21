package com.utaumarket.mobile.core

import android.content.Context
import com.utaumarket.mobile.R

/**
 * 把底层异常翻译成人话。
 *
 * 以前直接把 t.message 丢到界面上，用户看到的是 Job was cancelled / timeout /
 * unexpected end of stream 这种英文原文，而且看不出该怎么办。
 * 现在只给几种最常见的原因 + 一个兜底，原始异常照旧进日志。
 */
object ErrorText {

    fun of(context: Context, t: Throwable): String = when (t) {
        is java.net.SocketTimeoutException -> context.getString(R.string.err_timeout)
        is java.net.UnknownHostException -> context.getString(R.string.err_host)
        is java.net.ConnectException -> context.getString(R.string.err_connect)
        is javax.net.ssl.SSLException -> context.getString(R.string.err_ssl)
        is kotlinx.coroutines.CancellationException -> context.getString(R.string.err_cancelled)
        is java.util.concurrent.CancellationException -> context.getString(R.string.err_cancelled)
        else -> context.getString(R.string.err_unknown)
    }

    fun isCancellation(t: Throwable): Boolean =
        t is kotlinx.coroutines.CancellationException || t is java.util.concurrent.CancellationException
}
