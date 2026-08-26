package com.wormhole.browser.core.crash

import android.content.Context
import com.wormhole.browser.core.log.AppLog
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CrashHandler private constructor(
    private val appContext: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            writeCrashReport(thread, throwable)
        } catch (_: Throwable) {

        }

        if (defaultHandler != null) {
            defaultHandler.uncaughtException(thread, throwable)
        } else {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        val dir = File(appContext.filesDir, "crash_logs").apply { mkdirs() }

        dir.listFiles()?.sortedBy { it.lastModified() }
            ?.dropLast(MAX_REPORTS - 1)
            ?.forEach { it.delete() }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val file = File(dir, "crash_$timestamp.txt")

        val stackTrace = StringWriter().also { sw ->
            throwable.printStackTrace(PrintWriter(sw))
        }.toString()

        AppLog.e("Crash", "Uncaught on ${thread.name}: ${throwable.message}", throwable)
        file.writeText(
            buildString {
                appendLine("WormHole crash report")
                appendLine("Time: $timestamp")
                appendLine("Thread: ${thread.name}")
                appendLine("App version: ${appVersionName()}")
                appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine()
                append(stackTrace)
            },
        )
    }

    private fun appVersionName(): String = try {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName ?: "unknown"
    } catch (_: Exception) {
        "unknown"
    }

    companion object {
        private const val MAX_REPORTS = 10

        /**
         * Records a caught (non-fatal) error using the same report format and
         * rotation as an uncaught crash, so failures that are deliberately
         * caught with runCatching (and would otherwise vanish silently) are
         * still visible via [latestReport]/[hasReports].
         */
        fun recordNonFatal(context: Context, label: String, throwable: Throwable) {
            val appContext = context.applicationContext
            try {
                val dir = File(appContext.filesDir, "crash_logs").apply { mkdirs() }
                dir.listFiles()?.sortedBy { it.lastModified() }
                    ?.dropLast(MAX_REPORTS - 1)
                    ?.forEach { it.delete() }

                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val file = File(dir, "crash_$timestamp.txt")
                val stackTrace = StringWriter().also { sw ->
                    throwable.printStackTrace(PrintWriter(sw))
                }.toString()

                file.writeText(
                    buildString {
                        appendLine("WormHole non-fatal report: $label")
                        appendLine("Time: $timestamp")
                        appendLine("Android: ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                        appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                        appendLine()
                        append(stackTrace)
                    },
                )
            } catch (_: Throwable) {
                // Best-effort logging only; never let this crash the app.
            }
        }

        fun install(context: Context) {
            val appContext = context.applicationContext
            val existing = Thread.getDefaultUncaughtExceptionHandler()

            if (existing is CrashHandler) return
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(appContext, existing))
        }

        fun latestReport(context: Context): String? {
            val dir = File(context.applicationContext.filesDir, "crash_logs")
            val latest = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return null
            return try {
                latest.readText()
            } catch (_: Exception) {
                null
            }
        }

        fun hasReports(context: Context): Boolean {
            val dir = File(context.applicationContext.filesDir, "crash_logs")
            return dir.listFiles()?.isNotEmpty() == true
        }

        fun clearReports(context: Context) {
            val dir = File(context.applicationContext.filesDir, "crash_logs")
            dir.listFiles()?.forEach { it.delete() }
        }
    }
}
