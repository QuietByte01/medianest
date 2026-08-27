package com.medianest.util

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LogEntry(
    val timestamp: String,
    val dateGroup: String,
    val tag: String,
    val message: String,
    val level: LogLevel
)

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

object Logger {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private val groupDateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.US)
    private const val MAX_LOGS = 500

    private fun addLog(tag: String, msg: String, level: LogLevel) {
        val now = Date()
        val entry = LogEntry(
            timestamp = dateFormat.format(now),
            dateGroup = groupDateFormat.format(now).uppercase(Locale.US),
            tag = tag,
            message = msg,
            level = level
        )
        val currentList = _logs.value.toMutableList()
        currentList.add(0, entry) // Newest first
        if (currentList.size > MAX_LOGS) {
            currentList.removeAt(currentList.size - 1)
        }
        _logs.value = currentList
    }

    fun v(tag: String, msg: String, throwable: Throwable? = null) {
        Log.v("MediaNest_$tag", msg, throwable)
        addLog(tag, "$msg ${throwable?.message ?: ""}", LogLevel.DEBUG)
    }

    fun d(tag: String, msg: String, throwable: Throwable? = null) {
        Log.d("MediaNest_$tag", msg, throwable)
        addLog(tag, "$msg ${throwable?.message ?: ""}", LogLevel.DEBUG)
    }

    fun i(tag: String, msg: String, throwable: Throwable? = null) {
        Log.i("MediaNest_$tag", msg, throwable)
        addLog(tag, "$msg ${throwable?.message ?: ""}", LogLevel.INFO)
    }

    fun w(tag: String, msg: String, throwable: Throwable? = null) {
        Log.w("MediaNest_$tag", msg, throwable)
        addLog(tag, "$msg ${throwable?.message ?: ""}", LogLevel.WARN)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        Log.e("MediaNest_$tag", msg, throwable)
        addLog(tag, "$msg ${throwable?.message ?: ""}", LogLevel.ERROR)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    /**
     * Installs a global uncaught exception handler to capture app crashes in our internal logs.
     */
    fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("CRASH", "FATAL EXCEPTION in thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
