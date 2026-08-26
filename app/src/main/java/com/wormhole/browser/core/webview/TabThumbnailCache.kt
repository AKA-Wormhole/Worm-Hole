package com.wormhole.browser.core.webview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView

/**
 * Tab-switcher page previews.
 *
 * Kept in memory for instant Compose updates and mirrored to disk so a preview
 * survives process death / app restart. A preview is removed only when the tab
 * itself is closed — never by an LRU or by recycling the Bitmap Compose is
 * still drawing (that was why cards flashed back to a letter).
 */
object TabThumbnailCache {
    private const val THUMBNAIL_WIDTH_PX = 360
    private const val DIR_NAME = "tab_thumbnails"
    private const val MIN_CAPTURE_INTERVAL_MS = 1_200L

    private val thumbnails: SnapshotStateMap<String, Bitmap> = mutableStateMapOf()
    private val lastCaptureAt = mutableMapOf<String, Long>()
    private var diskDir: File? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        if (diskDir != null) return
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        diskDir = dir
        ioScope.launch {
            dir.listFiles()?.forEach { file ->
                if (!file.name.endsWith(".png")) return@forEach
                val tabId = file.name.removeSuffix(".png")
                val bmp = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
                    ?: return@forEach
                if (!bmp.isRecycled) {
                    thumbnails[tabId] = bmp
                }
            }
        }
    }

    fun get(tabId: String): Bitmap? {
        val live = thumbnails[tabId]
        if (live != null && !live.isRecycled) return live
        val file = diskDir?.let { File(it, "$tabId.png") } ?: return null
        if (!file.exists()) return null
        val fromDisk = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull()
        if (fromDisk != null && !fromDisk.isRecycled) {
            thumbnails[tabId] = fromDisk
            return fromDisk
        }
        return null
    }

    fun capture(tabId: String, geckoView: GeckoView, pageUrl: String? = null) {
        if (tabId.isBlank()) return
        if (geckoView.width <= 0 || geckoView.height <= 0) return
        val now = System.currentTimeMillis()
        val last = lastCaptureAt[tabId] ?: 0L
        if (now - last < MIN_CAPTURE_INTERVAL_MS && thumbnails[tabId] != null) return
        lastCaptureAt[tabId] = now
        runCatching {
            geckoView.capturePixels().accept({ bitmap ->
                if (bitmap == null || bitmap.isRecycled) return@accept
                val scale = THUMBNAIL_WIDTH_PX / bitmap.width.toFloat()
                val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val scaled = runCatching {
                    Bitmap.createScaledBitmap(bitmap, THUMBNAIL_WIDTH_PX, targetHeight, true)
                }.getOrNull() ?: return@accept
                // Do not recycle the previous bitmap here — Compose may still
                // be drawing it on a tab card. Dropping the map reference is enough.
                thumbnails[tabId] = scaled
                persist(tabId, scaled)
                if (!pageUrl.isNullOrBlank()) {
                    HistoryThumbnailCache.put(pageUrl, scaled)
                }
            }, {
                // Compositor not ready. Keep whatever preview we already have.
            })
        }
    }

    fun put(tabId: String, bitmap: Bitmap) {
        if (tabId.isBlank() || bitmap.isRecycled) return
        thumbnails[tabId] = bitmap
        persist(tabId, bitmap)
    }

    fun remove(tabId: String) {
        thumbnails.remove(tabId)
        lastCaptureAt.remove(tabId)
        ioScope.launch {
            diskDir?.let { File(it, "$tabId.png").delete() }
        }
    }

    fun clear() {
        thumbnails.clear()
        ioScope.launch {
            diskDir?.listFiles()?.forEach { it.delete() }
        }
    }

    private fun persist(tabId: String, bitmap: Bitmap) {
        val dir = diskDir ?: return
        val snapshot = bitmap
        ioScope.launch {
            runCatching {
                val tmp = File(dir, "$tabId.png.tmp")
                tmp.outputStream().use { out ->
                    snapshot.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
                val dest = File(dir, "$tabId.png")
                if (dest.exists()) dest.delete()
                tmp.renameTo(dest)
            }
        }
    }
}
