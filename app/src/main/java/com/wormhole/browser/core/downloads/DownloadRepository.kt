package com.wormhole.browser.core.downloads

import android.content.Context
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.Flow
import java.util.Base64

object DownloadRepository {

    private fun dao(context: Context) = DownloadsDatabase.get(context).dao()

    fun observeAll(context: Context): Flow<List<DownloadRecord>> = dao(context).observeAll()

    fun guessFileName(url: String, contentDisposition: String, mimeType: String): String =
        URLUtil.guessFileName(url, contentDisposition, mimeType)

    fun guessMimeType(url: String, fallback: String): String {
        val extension = MimeTypeMap.getFileExtensionFromUrl(url)
        return extension?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) } ?: fallback
    }

    fun needsStoragePermission(): Boolean =
        android.os.Build.VERSION.SDK_INT in android.os.Build.VERSION_CODES.O..android.os.Build.VERSION_CODES.P

    suspend fun start(
        context: Context,
        url: String,
        userAgent: String?,
        contentDisposition: String,
        mimeType: String,
    ): DownloadStartResult? {
        return try {
            if (url.isBlank()) return null
            val resolvedMime = mimeType.ifBlank { guessMimeType(url, "application/octet-stream") }
            val fileName = guessFileName(url, contentDisposition, resolvedMime)
            val destination = WormHoleDownloadEngine.createDestination(context, fileName, resolvedMime) ?: return null
            val category = DownloadCategory.from(resolvedMime, fileName)
            val now = System.currentTimeMillis()

            val record = DownloadRecord(
                url = url,
                fileName = fileName,
                mimeType = resolvedMime,
                category = category.name,
                destinationUri = destination.uriString,
                status = WormHoleDownloadStatus.PENDING.name,
                createdAt = now,
                updatedAt = now,
            )
            val id = dao(context).upsert(record)

            val cookie = try {
                CookieManager.getInstance().getCookie(url)
            } catch (_: Throwable) {
                null
            }
            try {
                ContextCompat.startForegroundService(
                    context,
                    WormHoleDownloadService.newIntent(context, id, url, userAgent, cookie, destination.uriString),
                )
            } catch (e: Throwable) {
                // Foreground-service restrictions / OEM quirks must not crash the app.
                // Fall back to a plain startService so the download can still run.
                try {
                    context.startService(
                        WormHoleDownloadService.newIntent(context, id, url, userAgent, cookie, destination.uriString),
                    )
                } catch (e2: Throwable) {
                    dao(context).updateStatus(id, WormHoleDownloadStatus.FAILED.name, e2.message ?: "Could not start download", System.currentTimeMillis())
                    return null
                }
            }

            DownloadStartResult(downloadId = id, fileName = fileName, mimeType = resolvedMime)
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun saveBase64(
        context: Context,
        fileName: String,
        mimeType: String,
        base64Data: String,
    ): DownloadStartResult? {
        val resolvedMime = mimeType.ifBlank { "application/octet-stream" }
        return try {
            val bytes = Base64.getDecoder().decode(base64Data)
            val destination = WormHoleDownloadEngine.createDestination(context, fileName, resolvedMime) ?: return null

            val written = when (destination) {
                is DownloadDestination.MediaStoreDestination -> {
                    context.contentResolver.openOutputStream(destination.uri)?.use { it.write(bytes) } != null
                }
                is DownloadDestination.FileDestination -> {
                    destination.file.outputStream().use { it.write(bytes) }
                    true
                }
            }
            if (!written) return null

            if (destination is DownloadDestination.MediaStoreDestination) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                }
                context.contentResolver.update(destination.uri, values, null, null)
            }

            val category = DownloadCategory.from(resolvedMime, fileName)
            val now = System.currentTimeMillis()
            val record = DownloadRecord(
                url = "blob:",
                fileName = fileName,
                mimeType = resolvedMime,
                category = category.name,
                destinationUri = destination.uriString,
                bytesDownloaded = bytes.size.toLong(),
                bytesTotal = bytes.size.toLong(),
                status = WormHoleDownloadStatus.SUCCESSFUL.name,
                createdAt = now,
                updatedAt = now,
            )
            val id = dao(context).upsert(record)
            DownloadStartResult(downloadId = id, fileName = fileName, mimeType = resolvedMime)
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: java.io.IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    suspend fun cancel(context: Context, downloadId: Long) {
        WormHoleDownloadEngine.requestCancel(downloadId)
        context.startService(WormHoleDownloadService.cancelIntent(context, downloadId))
    }

    suspend fun clear(context: Context, downloadId: Long) {
        val record = dao(context).get(downloadId) ?: return
        if (record.pinned) return
        dao(context).delete(downloadId)
    }

    suspend fun deleteMany(context: Context, downloadIds: List<Long>) {
        val d = dao(context)
        val deletable = downloadIds.filter { id -> d.get(id)?.pinned == false }
        if (deletable.isNotEmpty()) d.deleteMany(deletable)
    }

    suspend fun setPinned(context: Context, downloadId: Long, pinned: Boolean) {
        dao(context).setPinned(downloadId, pinned, System.currentTimeMillis())
    }

    suspend fun rename(context: Context, downloadId: Long, newFileName: String) {
        if (newFileName.isBlank()) return
        dao(context).rename(downloadId, newFileName, System.currentTimeMillis())
    }

    fun shareFile(context: Context, record: DownloadRecord) {
        val uri = android.net.Uri.parse(record.destinationUri)
        val resolvedUri = if (uri.scheme == "file") {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                java.io.File(uri.path ?: return),
            )
        } else {
            uri
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = record.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, resolvedUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(
                Intent.createChooser(intent, "Share ${record.fileName}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "No app found to share this file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun retry(context: Context, record: DownloadRecord, userAgent: String?) {

        WormHoleDownloadEngine.deletePartialFile(context, record.destinationUri)
        val destination = WormHoleDownloadEngine.createDestination(context, record.fileName, record.mimeType)
            ?: return
        val now = System.currentTimeMillis()
        dao(context).update(
            record.copy(
                destinationUri = destination.uriString,
                bytesDownloaded = 0,
                bytesTotal = 0,
                status = WormHoleDownloadStatus.PENDING.name,
                errorMessage = null,
                updatedAt = now,
            ),
        )
        val cookie = CookieManager.getInstance().getCookie(record.url)
        ContextCompat.startForegroundService(
            context,
            WormHoleDownloadService.newIntent(context, record.id, record.url, userAgent, cookie, destination.uriString),
        )
    }

    suspend fun resumeIncomplete(context: Context) {
        val stuck = dao(context).activeDownloads()
        for (record in stuck) {
            retry(context, record, userAgent = null)
        }
    }

    fun openFile(context: Context, record: DownloadRecord) {
        val uri = android.net.Uri.parse(record.destinationUri)
        val resolvedUri = if (uri.scheme == "file") {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                java.io.File(uri.path ?: return),
            )
        } else {
            uri
        }
        val mime = record.mimeType.ifBlank { "*/*" }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(resolvedUri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            val chooser = Intent.createChooser(intent, "Open ${record.fileName}").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(chooser)
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "No app found to open this file", android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: IllegalArgumentException) {
            android.widget.Toast.makeText(context, "This download cannot be opened", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

data class DownloadStartResult(
    val downloadId: Long,
    val fileName: String,
    val mimeType: String?,
)
