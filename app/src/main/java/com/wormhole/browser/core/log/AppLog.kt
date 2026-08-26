package com.wormhole.browser.core.log

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object AppLog {
    private const val MAX_MEMORY_LINES = 400
    private const val MAX_FILE_BYTES = 256 * 1024

    private val memory = CopyOnWriteArrayList<String>()
    private val lock = Any()
    private var logFile: File? = null
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        val dir = File(context.filesDir, "logs").apply { mkdirs() }
        logFile = File(dir, "wormhole.log")
        i("AppLog", "logger ready")
    }

    fun d(tag: String, message: String) = write("D", tag, message, null)

    fun i(tag: String, message: String) = write("I", tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)

    fun snapshot(): String = buildString {
        appendLine("WormHole app log")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine()
        val file = logFile
        if (file != null && file.exists()) {
            appendLine("--- file ---")
            appendLine(file.readText())
        }
        appendLine("--- memory ---")
        memory.forEach { appendLine(it) }
    }

    fun clear() {
        synchronized(lock) {
            memory.clear()
            logFile?.writeText("")
        }
    }

    private fun write(level: String, tag: String, message: String, error: Throwable?) {
        val line = buildString {
            append(timeFormat.format(Date()))
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(": ")
            append(message)
            if (error != null) {
                append('\n')
                append(error.stackTraceToString())
            }
        }
        memory.add(line)
        while (memory.size > MAX_MEMORY_LINES) {
            runCatching { memory.removeAt(0) }
        }
        when (level) {
            "E" -> Log.e(tag, message, error)
            "W" -> Log.w(tag, message, error)
            "I" -> Log.i(tag, message)
            else -> Log.d(tag, message)
        }
        val file = logFile ?: return
        synchronized(lock) {
            runCatching {
                if (file.length() > MAX_FILE_BYTES) {
                    val keep = file.readText().takeLast(MAX_FILE_BYTES / 2)
                    file.writeText(keep)
                }
                file.appendText(line + "\n")
            }
        }
    }
}
