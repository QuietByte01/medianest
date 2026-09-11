package com.medianest.studio.util

import android.util.Log
import java.util.Locale

object Logger {
    fun d(tag: String, msg: String) { Log.d(tag, msg) }
    fun i(tag: String, msg: String) { Log.i(tag, msg) }
    fun w(tag: String, msg: String) { Log.w(tag, msg) }
    fun e(tag: String, msg: String, t: Throwable? = null) { Log.e(tag, msg, t) }
}

fun formatBytesReport(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
