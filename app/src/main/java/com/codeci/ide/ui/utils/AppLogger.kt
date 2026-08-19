package com.codeci.ide.ui.utils

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()
    
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun d(tag: String, message: String) {
        log("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        log("INFO", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log("ERROR", tag, message + (throwable?.let { " - ${it.message}" } ?: ""))
    }

    private fun log(level: String, tag: String, message: String) {
        val time = dateFormat.format(Date())
        val logLine = "[$time] $level/$tag: $message"
        Log.d("AppLogger_$tag", logLine)
        
        val current = _logs.value.toMutableList()
        current.add(logLine)
        if (current.size > 1000) {
            current.removeAt(0)
        }
        _logs.value = current
    }
    
    fun clear() {
        _logs.value = emptyList()
    }
    
    fun getLogsString(): String = _logs.value.joinToString("\n")
}
