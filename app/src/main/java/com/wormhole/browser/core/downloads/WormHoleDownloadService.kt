package com.wormhole.browser.core.downloads

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class WormHoleDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val notificationManager by lazy {
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
                if (id != -1L) {
                    WormHoleDownloadEngine.requestCancel(id)
                }
                return START_NOT_STICKY
            }
            else -> {

                val safeIntent = intent ?: run {
                    stopIfIdle()
                    return START_NOT_STICKY
                }
                val id = safeIntent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
                if (id == -1L) {
                    stopIfIdle()
                    return START_NOT_STICKY
                }
                val url = safeIntent.getStringExtra(EXTRA_URL) ?: run {
                    stopIfIdle()
                    return START_NOT_STICKY
                }
                val userAgent = safeIntent.getStringExtra(EXTRA_USER_AGENT)
                val cookie = safeIntent.getStringExtra(EXTRA_COOKIE)
                val destinationUri = safeIntent.getStringExtra(EXTRA_DESTINATION_URI) ?: run {
                    stopIfIdle()
                    return START_NOT_STICKY
                }
                startDownload(id, url, userAgent, cookie, destinationUri)
            }
        }
        return START_NOT_STICKY
    }

    private fun notificationId(downloadId: Long): Int {
        val hashed = (downloadId.hashCode() and 0x7FFFFFFF).let { if (it == 0) 1 else it }
        // Never let a per-download notification collide with the reserved foreground
        // summary ID -- otherwise updating one would clobber the other.
        return if (hashed == FOREGROUND_NOTIFICATION_ID) hashed + 1 else hashed
    }

    private fun startDownload(
        downloadId: Long,
        url: String,
        userAgent: String?,
        cookie: String?,
        destinationUri: String,
    ) {

        activeJobs[downloadId]?.cancel()

        // Only the very first active download actually calls startForeground(); later
        // concurrent downloads just post regular notifications with their own IDs.
        // Android only recognizes one "foreground" notification per service, so calling
        // startForeground() again per download would silently orphan the earlier ones.
        if (activeJobs.isEmpty()) {
            try {
                startForeground(FOREGROUND_NOTIFICATION_ID, buildSummaryNotification())
            } catch (_: Throwable) {
                // Android 12+ / OEM may reject startForeground; continue without FG.
            }
        }
        try {
            notificationManager.notify(notificationId(downloadId), buildProgressNotification(downloadId, fileNameHint(url), 0, 0))
        } catch (_: Throwable) { }

        val job = serviceScope.launch {
            try {
                // Bound how many downloads are actively transferring bytes at once.
                WormHoleDownloadEngine.withDownloadSlot {
                    val dao = DownloadsDatabase.get(applicationContext).dao()
                    val record = dao.get(downloadId)
                    val displayName = record?.fileName ?: fileNameHint(url)

                    try {
                        dao.updateStatus(downloadId, WormHoleDownloadStatus.RUNNING.name, null, System.currentTimeMillis())
                    } catch (_: Throwable) { }

                    val destination = parseDestination(destinationUri)
                    if (destination == null) {
                        try {
                            dao.updateStatus(downloadId, WormHoleDownloadStatus.FAILED.name, "Invalid destination", System.currentTimeMillis())
                        } catch (_: Throwable) { }
                        notifyFailed(downloadId, displayName)
                        finishJob(downloadId)
                        return@withDownloadSlot
                    }

                    var lastNotifyMillis = 0L
                    var lastDbWriteMillis = 0L
                    val result = try {
                        WormHoleDownloadEngine.fetch(
                            context = applicationContext,
                            downloadId = downloadId,
                            url = url,
                            userAgent = userAgent,
                            cookie = cookie,
                            destination = destination,
                        ) { downloaded, total ->
                            val now = System.currentTimeMillis()
                            val isDone = total > 0 && downloaded >= total
                            if (now - lastDbWriteMillis > 250 || isDone) {
                                lastDbWriteMillis = now
                                serviceScope.launch {
                                    try {
                                        dao.updateProgress(downloadId, downloaded, total, WormHoleDownloadStatus.RUNNING.name, System.currentTimeMillis())
                                    } catch (_: Throwable) { }
                                }
                            }
                            if (now - lastNotifyMillis > 250) {
                                lastNotifyMillis = now
                                try {
                                    notificationManager.notify(
                                        notificationId(downloadId),
                                        buildProgressNotification(downloadId, displayName, downloaded, total),
                                    )
                                } catch (_: Throwable) { }
                            }
                        }
                    } catch (e: Throwable) {
                        FetchResult.Failure(e.message ?: "Download crashed")
                    }

                    try {
                        when (result) {
                            is FetchResult.Success -> {
                                dao.updateProgress(
                                    downloadId,
                                    result.bytesDownloaded,
                                    result.bytesTotal,
                                    WormHoleDownloadStatus.SUCCESSFUL.name,
                                    System.currentTimeMillis(),
                                )
                                notifyCompleted(downloadId, displayName)
                            }
                            is FetchResult.Failure -> {
                                dao.updateStatus(downloadId, WormHoleDownloadStatus.FAILED.name, result.reason, System.currentTimeMillis())
                                WormHoleDownloadEngine.deletePartialFile(applicationContext, destinationUri)
                                notifyFailed(downloadId, displayName)
                            }
                            FetchResult.Cancelled -> {
                                dao.updateStatus(downloadId, WormHoleDownloadStatus.CANCELLED.name, null, System.currentTimeMillis())
                                WormHoleDownloadEngine.deletePartialFile(applicationContext, destinationUri)
                                notificationManager.cancel(notificationId(downloadId))
                            }
                        }
                    } catch (_: Throwable) { }

                    finishJob(downloadId)
                }
            } catch (e: Throwable) {
                try {
                    val dao = DownloadsDatabase.get(applicationContext).dao()
                    dao.updateStatus(downloadId, WormHoleDownloadStatus.FAILED.name, e.message ?: "Download failed", System.currentTimeMillis())
                } catch (_: Throwable) { }
                try { notifyFailed(downloadId, fileNameHint(url)) } catch (_: Throwable) { }
                finishJob(downloadId)
            }
        }
        activeJobs[downloadId] = job
    }

    private fun finishJob(downloadId: Long) {
        activeJobs.remove(downloadId)
        if (activeJobs.isEmpty()) {
            stopIfIdle()
        } else {
            // Other downloads are still running; keep the foreground anchor notification
            // alive and just refresh its "N downloads active" summary text.
            notificationManager.notify(FOREGROUND_NOTIFICATION_ID, buildSummaryNotification())
        }
    }

    private fun buildSummaryNotification(): Notification {
        val activeCount = activeJobs.size.coerceAtLeast(1)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading")
            .setContentText(if (activeCount == 1) "1 download in progress" else "$activeCount downloads in progress")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(DOWNLOAD_NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .build()
    }

    private fun stopIfIdle() {
        if (activeJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private fun buildProgressNotification(downloadId: Long, fileName: String, downloaded: Long, total: Long): Notification {
        val progressPercent = if (total > 0) ((downloaded * 100) / total).toInt() else 0
        val cancelIntent = PendingIntent.getService(
            this,
            notificationId(downloadId),
            cancelIntent(this, downloadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val statusText = when {
            downloaded <= 0L -> "Connecting…"
            total > 0 -> "$progressPercent% • ${formatBytes(downloaded)} of ${formatBytes(total)}"
            else -> formatBytes(downloaded)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(fileName)
            .setContentText(statusText)
            .setProgress(100, progressPercent, downloaded <= 0L || total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(DOWNLOAD_NOTIFICATION_GROUP)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
    }

    private fun notifyCompleted(downloadId: Long, fileName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(fileName)
            .setContentText("Download complete")
            .setOngoing(false)
            .setAutoCancel(true)
            .setGroup(DOWNLOAD_NOTIFICATION_GROUP)
            .build()
        notificationManager.notify(notificationId(downloadId), notification)
    }

    private fun notifyFailed(downloadId: Long, fileName: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(fileName)
            .setContentText("Download failed")
            .setOngoing(false)
            .setAutoCancel(true)
            .setGroup(DOWNLOAD_NOTIFICATION_GROUP)
            .build()
        notificationManager.notify(notificationId(downloadId), notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows progress for files WormHole is downloading"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun parseDestination(destinationUri: String): DownloadDestination? {
        val uri = android.net.Uri.parse(destinationUri)
        return if (uri.scheme == "file") {
            uri.path?.let { DownloadDestination.FileDestination(java.io.File(it)) }
        } else {
            DownloadDestination.MediaStoreDestination(uri)
        }
    }

    private fun fileNameHint(url: String): String =
        android.net.Uri.parse(url).lastPathSegment ?: "Download"

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 KB"
        val kb = bytes / 1024.0
        if (kb < 1024) return "${kb.toInt()} KB"
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "wormhole_downloads"
        private const val DOWNLOAD_NOTIFICATION_GROUP = "com.wormhole.browser.downloads.GROUP"
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val ACTION_CANCEL = "com.wormhole.browser.action.CANCEL_DOWNLOAD"
        private const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        private const val EXTRA_URL = "extra_url"
        private const val EXTRA_USER_AGENT = "extra_user_agent"
        private const val EXTRA_COOKIE = "extra_cookie"
        private const val EXTRA_DESTINATION_URI = "extra_destination_uri"

        fun newIntent(
            context: Context,
            downloadId: Long,
            url: String,
            userAgent: String?,
            cookie: String?,
            destinationUri: String,
        ): Intent = Intent(context, WormHoleDownloadService::class.java).apply {
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            putExtra(EXTRA_URL, url)
            putExtra(EXTRA_USER_AGENT, userAgent)
            putExtra(EXTRA_COOKIE, cookie)
            putExtra(EXTRA_DESTINATION_URI, destinationUri)
        }

        fun cancelIntent(context: Context, downloadId: Long): Intent =
            Intent(context, WormHoleDownloadService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
    }
}
