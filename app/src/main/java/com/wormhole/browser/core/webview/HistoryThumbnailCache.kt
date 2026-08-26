package com.wormhole.browser.core.webview

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Recent-history card previews on the new-tab page.
 * Bitmaps come from GeckoView captures (same pipeline as tab cards).
 */
object HistoryThumbnailCache {
    private const val MAX_ENTRIES = 24

    private val thumbnails: SnapshotStateMap<String, Bitmap> = mutableStateMapOf()
    private val accessOrder = ArrayDeque<String>()

    fun get(url: String): Bitmap? {
        val live = thumbnails[url]
        if (live != null && !live.isRecycled) {
            accessOrder.remove(url)
            accessOrder.addLast(url)
            return live
        }
        return null
    }

    fun put(url: String, bitmap: Bitmap) {
        if (url.isBlank() || bitmap.isRecycled) return
        thumbnails[url] = bitmap
        accessOrder.remove(url)
        accessOrder.addLast(url)
        while (accessOrder.size > MAX_ENTRIES) {
            val oldest = accessOrder.removeFirstOrNull() ?: break
            thumbnails.remove(oldest)
        }
    }

    fun remove(url: String) {
        accessOrder.remove(url)
        thumbnails.remove(url)
    }

    fun clear() {
        accessOrder.clear()
        thumbnails.clear()
    }
}
