package com.utaumarket.mobile.core

import android.util.Log

object L {
    private const val TAG = "UtauMarket"
    var verbose: Boolean = true
    fun d(msg: String) { if (verbose) Log.d(TAG, msg) }
    fun w(msg: String, t: Throwable? = null) { Log.w(TAG, msg, t) }
    fun e(msg: String, t: Throwable? = null) { Log.e(TAG, msg, t) }
}
