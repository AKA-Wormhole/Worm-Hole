package com.knot.browser.core.browser

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a download off to Android's system DownloadManager, which shows
 * its own notification/progress UI and survives the app being killed --
 * far more robust than trying to stream the download ourselves. This is
 * the standard approach used by every WebView-based Android browser.
 *
 * On top of enqueueing, this also reads DownloadManager's own content
 * provider back out ([queryAll]) so Knot's in-app Downloads screen shows
 * live, real status/progress rather than a decorative list -- there is
 * no separate Knot-side download table to keep in sync.
 */
object DownloadHandler {

    fun enqueue(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String,
        mimeType: String,
    ): Long {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mimeType)
            addRequestHeader("cookie", CookieManager.getInstance().getCookie(url))
            userAgent?.let { addRequestHeader("User-Agent", it) }
            setDescription("Downloading via Knot")
            setTitle(fileName)
            allowScanningByMediaScanner()
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }

    fun guessMimeType(url: String, fallback: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) } ?: fallback
    }

    fun guessFileName(url: String, contentDisposition: String, mimeType: String): String =
        URLUtil.guessFileName(url, contentDisposition, mimeType)

    /**
     * Reads every download DownloadManager knows about for this app
     * (this session's and any from before an app restart -- the OS
     * keeps its own record independent of Knot's process) and maps each
     * row into a [DownloadEntry]. Called on a polling interval from the
     * Downloads screen so progress bars move in real time; cheap enough
     * for that since it's a single content-provider query with no args.
     */
    fun queryAll(context: Context): List<DownloadEntry> {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = downloadManager.query(DownloadManager.Query())
        val results = mutableListOf<DownloadEntry>()
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val titleCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
            val uriCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
            val statusCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val reasonCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
            val bytesDownloadedCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val bytesTotalCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val mimeCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)
            val localUriCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            val lastModifiedCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
            val localFilenameCol = it.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_FILENAME)

            while (it.moveToNext()) {
                val rawStatus = it.getInt(statusCol)
                val title = it.getString(titleCol) ?: "Download"
                val localUri = it.getString(localUriCol)
                val localFileName = it.getString(localFilenameCol)
                val fileName = localFileName?.let { path -> File(path).name }
                    ?: localUri?.let { u -> Uri.parse(u).lastPathSegment }
                    ?: title

                results += DownloadEntry(
                    downloadManagerId = it.getLong(idCol),
                    title = title,
                    fileName = fileName,
                    url = it.getString(uriCol).orEmpty(),
                    status = rawStatus.toDownloadStatus(),
                    bytesDownloaded = it.getLong(bytesDownloadedCol),
                    bytesTotal = it.getLong(bytesTotalCol),
                    mimeType = it.getString(mimeCol),
                    localUri = localUri,
                    reason = it.getInt(reasonCol),
                    lastModifiedMillis = it.getLong(lastModifiedCol),
                )
            }
        }
        return results.sortedByDescending { it.lastModifiedMillis }
    }

    private fun Int.toDownloadStatus(): DownloadStatus = when (this) {
        DownloadManager.STATUS_PENDING -> DownloadStatus.PENDING
        DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
        DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
        DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESSFUL
        DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
        else -> DownloadStatus.FAILED
    }

    /** Cancels a still-in-progress download and removes its DownloadManager
     * row (matches what swiping it away in the system Downloads UI does). */
    fun cancel(context: Context, downloadManagerId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadManagerId)
    }

    /** Removes a completed or failed entry from the list without touching
     * the file on disk (DownloadManager.remove also deletes the file for
     * completed downloads, which is the expected "clear from list" UX
     * here, matching Chrome's downloads page). */
    fun clear(context: Context, downloadManagerId: Long) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.remove(downloadManagerId)
    }

    /**
     * Opens a completed download with whatever app the system resolves
     * for its MIME type -- the same "tap to open" behavior as the stock
     * Downloads app and every other browser's downloads screen.
     */
    fun openFile(context: Context, entry: DownloadEntry) {
        val uri = entry.localUri?.let(Uri::parse) ?: return
        val resolvedUri = if (uri.scheme == "file") {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(uri.path ?: return))
        } else {
            uri
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(resolvedUri, entry.mimeType ?: "*/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens Android's own Downloads UI (the same list system DownloadManager
     * shows in its notification / the stock Downloads app). Kept as a
     * fallback entry point (e.g. if the person wants the OS-level view),
     * but Knot's own Downloads screen (DownloadsScreen.kt) is now the
     * primary in-app surface reached from the page-tools menu.
     */
    fun openSystemDownloadsList(context: Context) {
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "No downloads app found", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
