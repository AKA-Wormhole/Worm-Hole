package com.knot.browser.core.webview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap

/**
 * Live thumbnails for the tab grid (KnotSidebar's replacement for the
 * plain tab list): a small bitmap per tab, captured straight from its
 * WebView, cached here so switching Spaces or reopening the grid doesn't
 * need to re-render every tab's page to get a preview.
 *
 * Backed by a Compose SnapshotStateMap rather than a plain HashMap so the
 * tab grid recomposes automatically when a thumbnail updates, without the
 * caller needing a separate "thumbnails changed" signal.
 *
 * Deliberately capped in resolution (see [THUMBNAIL_WIDTH_PX]) -- these
 * are grid-card previews at ~130dp wide, not full-resolution screenshots,
 * so there's no reason to hold a full device-resolution bitmap per tab.
 */
object TabThumbnailCache {
    private const val THUMBNAIL_WIDTH_PX = 320

    private val thumbnails: SnapshotStateMap<String, Bitmap> = mutableStateMapOf()

    fun get(tabId: String): Bitmap? = thumbnails[tabId]

    /** Draws the WebView's current contents into a small cached bitmap.
     * Safe to call often (e.g. on every onPageFinished) -- capture cost
     * scales with the downscaled output size, not the page's real size,
     * since we draw straight into the small target bitmap's canvas. */
    fun capture(tabId: String, webView: WebView) {
        val viewWidth = webView.width
        val viewHeight = webView.height
        if (viewWidth <= 0 || viewHeight <= 0) return

        val scale = THUMBNAIL_WIDTH_PX / viewWidth.toFloat()
        val targetWidth = THUMBNAIL_WIDTH_PX
        val targetHeight = (viewHeight * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        webView.draw(canvas)

        thumbnails[tabId]?.recycle()
        thumbnails[tabId] = bitmap
    }

    fun remove(tabId: String) {
        thumbnails.remove(tabId)?.recycle()
    }

    fun clear() {
        thumbnails.keys.toList().forEach { remove(it) }
    }
}
