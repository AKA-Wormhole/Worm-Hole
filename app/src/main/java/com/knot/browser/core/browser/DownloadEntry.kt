package com.knot.browser.core.browser

/**
 * A snapshot of one row from Android's DownloadManager, re-shaped for
 * the UI layer. Nothing here is synthetic -- every field is read
 * straight out of DownloadManager's own content provider via
 * [DownloadHandler.queryAll], so this reflects exactly what the OS is
 * doing with the download (including ones started in a previous app
 * session, since DownloadManager tracks them independently of Knot).
 */
data class DownloadEntry(
    val downloadManagerId: Long,
    val title: String,
    val fileName: String,
    val url: String,
    val status: DownloadStatus,
    val bytesDownloaded: Long,
    val bytesTotal: Long,
    val mimeType: String?,
    val localUri: String?,
    val reason: Int,
    val lastModifiedMillis: Long,
) {
    /** 0f..1f, or null when the server didn't report a content length so
     * progress can't be computed (shown as an indeterminate spinner). */
    val progress: Float?
        get() = if (bytesTotal > 0) (bytesDownloaded.toFloat() / bytesTotal.toFloat()).coerceIn(0f, 1f) else null
}

enum class DownloadStatus {
    PENDING,
    RUNNING,
    PAUSED,
    SUCCESSFUL,
    FAILED,
}
