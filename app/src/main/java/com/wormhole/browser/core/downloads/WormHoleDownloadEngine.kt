package com.wormhole.browser.core.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

object WormHoleDownloadEngine {

    private val dispatcher = okhttp3.Dispatcher().apply {
        maxRequests = 32
        maxRequestsPerHost = 16
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .followSslRedirects(true)
        .dispatcher(dispatcher)
        .connectionPool(okhttp3.ConnectionPool(24, 5, TimeUnit.MINUTES))
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    const val MAX_CONCURRENT_DOWNLOADS = 3
    @PublishedApi
    internal val downloadSemaphore = kotlinx.coroutines.sync.Semaphore(MAX_CONCURRENT_DOWNLOADS)

    suspend inline fun <T> withDownloadSlot(crossinline block: suspend () -> T): T =
        downloadSemaphore.withPermit { block() }

    private val cancelFlags = ConcurrentHashMap<Long, AtomicBoolean>()

    fun requestCancel(downloadId: Long) {
        cancelFlags.getOrPut(downloadId) { AtomicBoolean(false) }.set(true)
    }

    fun clearCancelFlag(downloadId: Long) {
        cancelFlags.remove(downloadId)
    }

    private fun cancelled(downloadId: Long): Boolean =
        cancelFlags[downloadId]?.get() == true

    fun createDestination(context: Context, fileName: String, mimeType: String): DownloadDestination? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                DownloadDestination.MediaStoreDestination(itemUri)
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val target = uniqueFile(downloadsDir, fileName)
                DownloadDestination.FileDestination(target)
            }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun fetch(
        context: Context,
        downloadId: Long,
        url: String,
        userAgent: String?,
        cookie: String?,
        destination: DownloadDestination,
        onProgress: (bytesDownloaded: Long, bytesTotal: Long) -> Unit,
    ): FetchResult {
        cancelFlags[downloadId] = AtomicBoolean(false)
        return try {
            val probe = probe(url, userAgent, cookie)
            if (cancelled(downloadId)) return FetchResult.Cancelled
            val segmented = probe.total > SEGMENT_MIN_BYTES && probe.acceptsRanges
            val result = if (segmented) {
                val multi = fetchSegmented(context, downloadId, url, userAgent, cookie, destination, probe.total, onProgress)
                if (multi is FetchResult.Failure) {
                    fetchSingle(context, downloadId, url, userAgent, cookie, destination, onProgress)
                } else {
                    multi
                }
            } else {
                fetchSingle(context, downloadId, url, userAgent, cookie, destination, onProgress)
            }
            result
        } catch (e: IOException) {
            FetchResult.Failure(e.message ?: "Network error")
        } catch (e: SecurityException) {
            FetchResult.Failure("Permission denied")
        } catch (e: IllegalStateException) {
            FetchResult.Failure(e.message ?: "Download failed")
        } finally {
            cancelFlags.remove(downloadId)
        }
    }

    private data class Probe(val total: Long, val acceptsRanges: Boolean)

    private fun probe(url: String, userAgent: String?, cookie: String?): Probe {
        val request = baseRequest(url, userAgent, cookie)
            .header("Range", "bytes=0-0")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                val acceptRanges = response.header("Accept-Ranges").orEmpty().contains("bytes", ignoreCase = true)
                val contentRange = response.header("Content-Range")
                val totalFromRange = parseTotalFromContentRange(contentRange)
                val contentLength = response.body?.contentLength() ?: -1L
                val total = when {
                    totalFromRange > 0 -> totalFromRange
                    response.code == 206 && contentLength > 0 -> contentLength
                    response.code == 200 && contentLength > 1 -> contentLength
                    else -> -1L
                }
                Probe(total = total, acceptsRanges = acceptRanges || response.code == 206 || totalFromRange > 0)
            }
        } catch (_: Exception) {
            Probe(total = -1L, acceptsRanges = false)
        }
    }

    private fun parseTotalFromContentRange(header: String?): Long {
        if (header.isNullOrBlank()) return -1L
        val slash = header.lastIndexOf('/')
        if (slash < 0 || slash == header.lastIndex) return -1L
        return header.substring(slash + 1).toLongOrNull() ?: -1L
    }

    private fun partCount(total: Long): Int = when {
        total < 2L * 1024 * 1024 -> 2
        total < 16L * 1024 * 1024 -> 4
        total < 80L * 1024 * 1024 -> 6
        else -> 8
    }

    private fun fetchSegmented(
        context: Context,
        downloadId: Long,
        url: String,
        userAgent: String?,
        cookie: String?,
        destination: DownloadDestination,
        total: Long,
        onProgress: (Long, Long) -> Unit,
    ): FetchResult {
        val parts = partCount(total)
        val writesDirectly = destination is DownloadDestination.FileDestination
        val temp = if (writesDirectly) {
            (destination as DownloadDestination.FileDestination).file
        } else {
            File(context.cacheDir, "wh-dl-$downloadId.bin")
        }
        if (!writesDirectly && temp.exists()) temp.delete()
        try {
            RandomAccessFile(temp, "rw").use { it.setLength(total) }
            val ranges = splitRanges(total, parts)
            val transferred = AtomicLong(0)
            val lastProgressAt = AtomicLong(0)
            val failed = AtomicBoolean(false)
            val failureReason = arrayOfNulls<String>(1)

            runBlocking {
                coroutineScope {
                    ranges.map { range ->
                        async(Dispatchers.IO) {
                            val partResult = fetchRange(
                                downloadId = downloadId,
                                url = url,
                                userAgent = userAgent,
                                cookie = cookie,
                                file = temp,
                                start = range.first,
                                end = range.last,
                            ) { extra ->
                                val now = transferred.addAndGet(extra)
                                val t = System.currentTimeMillis()
                                if (t - lastProgressAt.get() >= 200L || now >= total) {
                                    lastProgressAt.set(t)
                                    onProgress(now, total)
                                }
                            }
                            if (partResult != null) {
                                failed.set(true)
                                failureReason[0] = partResult
                            }
                        }
                    }.awaitAll()
                }
            }

            if (cancelled(downloadId)) return FetchResult.Cancelled
            if (failed.get()) return FetchResult.Failure(failureReason[0] ?: "Segment failed")
            if (temp.length() != total) return FetchResult.Failure("Incomplete file")

            if (!writesDirectly) copyToDestination(context, temp, destination)
            onProgress(total, total)
            return FetchResult.Success(total, total)
        } catch (e: IOException) {
            return FetchResult.Failure(e.message ?: "Network error")
        } finally {
            if (!writesDirectly) temp.delete()
        }
    }

    private fun fetchRange(
        downloadId: Long,
        url: String,
        userAgent: String?,
        cookie: String?,
        file: File,
        start: Long,
        end: Long,
        onBytes: (Long) -> Unit,
    ): String? {
        val request = baseRequest(url, userAgent, cookie)
            .header("Range", "bytes=$start-$end")
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.code != 206) {
                    return "not-ranged"
                }
                val body = response.body ?: return "Empty response body"
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(start)
                    val buffer = ByteArray(BUFFER_SIZE)
                    var remaining = end - start + 1
                    body.byteStream().use { input ->
                        while (remaining > 0) {
                            if (cancelled(downloadId)) return "cancelled"
                            val cap = minOf(buffer.size.toLong(), remaining).toInt()
                            val read = input.read(buffer, 0, cap)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            remaining -= read
                            onBytes(read.toLong())
                        }
                    }
                }
            }
            null
        } catch (e: IOException) {
            e.message ?: "Network error"
        }
    }

    private fun fetchSingle(
        context: Context,
        downloadId: Long,
        url: String,
        userAgent: String?,
        cookie: String?,
        destination: DownloadDestination,
        onProgress: (Long, Long) -> Unit,
    ): FetchResult {
        val request = baseRequest(url, userAgent, cookie).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return FetchResult.Failure("Server returned ${response.code}")
            val body = response.body ?: return FetchResult.Failure("Empty response body")
            val total = body.contentLength().coerceAtLeast(0)
            val outputStream = openDestination(context, destination)
                ?: return FetchResult.Failure("Could not open destination for writing")
            var downloaded = 0L
            var lastProgressAt = 0L
            val buffer = ByteArray(BUFFER_SIZE)
            outputStream.use { out ->
                body.byteStream().use { input ->
                    while (true) {
                        if (cancelled(downloadId)) return FetchResult.Cancelled
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastProgressAt >= 200L) {
                            lastProgressAt = now
                            onProgress(downloaded, total)
                        }
                    }
                    out.flush()
                }
            }
            finishMediaStore(context, destination)
            onProgress(downloaded, if (total > 0) total else downloaded)
            return FetchResult.Success(downloaded, if (total > 0) total else downloaded)
        }
    }

    private fun copyToDestination(context: Context, temp: File, destination: DownloadDestination) {
        val out = openDestination(context, destination) ?: throw IOException("Could not open destination")
        FileInputStream(temp).use { input ->
            out.use { output ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }
        finishMediaStore(context, destination)
    }

    private fun openDestination(context: Context, destination: DownloadDestination) = when (destination) {
        is DownloadDestination.MediaStoreDestination -> context.contentResolver.openOutputStream(destination.uri)
        is DownloadDestination.FileDestination -> FileOutputStream(destination.file)
    }

    private fun finishMediaStore(context: Context, destination: DownloadDestination) {
        if (destination is DownloadDestination.MediaStoreDestination) {
            val values = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(destination.uri, values, null, null)
        }
    }

    private fun baseRequest(url: String, userAgent: String?, cookie: String?): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .apply {
                userAgent?.let { header("User-Agent", it) }
                cookie?.let { if (it.isNotBlank()) header("Cookie", it) }
            }

    private fun splitRanges(total: Long, parts: Int): List<LongRange> {
        val size = total / parts
        return (0 until parts).map { index ->
            val start = index * size
            val end = if (index == parts - 1) total - 1 else (start + size - 1)
            start..end
        }
    }

    fun deletePartialFile(context: Context, destinationUri: String) {
        try {
            val uri = Uri.parse(destinationUri)
            if (uri.scheme == "file") {
                uri.path?.let { File(it).delete() }
            } else {
                context.contentResolver.delete(uri, null, null)
            }
        } catch (_: Exception) {
        }
    }

    private fun uniqueFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        var i = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($i)$ext")
            i++
        }
        return candidate
    }

    private const val BUFFER_SIZE = 256 * 1024
    private const val SEGMENT_MIN_BYTES = 1L * 1024 * 1024
}

sealed interface DownloadDestination {
    val uriString: String

    data class MediaStoreDestination(val uri: Uri) : DownloadDestination {
        override val uriString: String get() = uri.toString()
    }

    data class FileDestination(val file: File) : DownloadDestination {
        override val uriString: String get() = Uri.fromFile(file).toString()
    }
}

sealed interface FetchResult {
    data class Success(val bytesDownloaded: Long, val bytesTotal: Long) : FetchResult
    data class Failure(val reason: String) : FetchResult
    data object Cancelled : FetchResult
}
