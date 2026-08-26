package com.wormhole.browser.core.webview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import java.io.File
import java.net.URI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Caches real site favicons (as delivered by WebView's onReceivedIcon) keyed by
 * host, so shortcuts, tab cards, and history entries can all show the actual
 * website logo instead of a generic globe or hand-picked glyph.
 *
 * Backed by an in-memory LRU plus a disk directory, so icons survive app
 * restarts instead of refetching from every site on cold start.
 */
object FaviconCache {
    private const val MAX_ENTRIES = 64
    private const val DIR_NAME = "favicons"

    private val favicons: SnapshotStateMap<String, Bitmap> = mutableStateMapOf()
    private val accessOrder = ArrayDeque<String>()
    private var diskDir: File? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)

    /** Call once (e.g. from Application.onCreate) to load previously saved icons from disk. */
    fun init(context: Context) {
        if (diskDir != null) return
        val dir = File(context.filesDir, DIR_NAME).apply { mkdirs() }
        diskDir = dir
        ioScope.launch {
            dir.listFiles()?.sortedBy { it.lastModified() }?.takeLast(MAX_ENTRIES)?.forEach { file ->
                val bmp = runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() ?: return@forEach
                val host = file.nameWithoutExtension
                favicons[host] = bmp
                accessOrder.remove(host)
                accessOrder.addLast(host)
            }
        }
    }

    fun hostOf(url: String): String = try {
        URI(url).host?.removePrefix("www.")?.lowercase() ?: url
    } catch (_: Exception) {
        url
    }

    fun get(url: String): Bitmap? {
        val host = hostOf(url)
        if (favicons.containsKey(host)) {
            accessOrder.remove(host)
            accessOrder.addLast(host)
        }
        return favicons[host]
    }

    fun put(url: String, icon: Bitmap?) {
        if (icon == null || icon.isRecycled) return
        val host = hostOf(url)
        if (host.isBlank()) return

        // Prefer higher-resolution icons when a better one comes in for the same host.
        val existing = favicons[host]
        if (existing != null && !existing.isRecycled &&
            existing.width * existing.height >= icon.width * icon.height
        ) {
            accessOrder.remove(host)
            accessOrder.addLast(host)
            return
        }

        favicons[host] = icon
        accessOrder.remove(host)
        accessOrder.addLast(host)
        saveToDisk(host, icon)

        while (accessOrder.size > MAX_ENTRIES) {
            val oldest = accessOrder.removeFirstOrNull() ?: break
            favicons.remove(oldest)
            diskDir?.let { File(it, "$oldest.png").delete() }
        }
    }

    private fun saveToDisk(host: String, icon: Bitmap) {
        val dir = diskDir ?: return
        ioScope.launch {
            runCatching {
                File(dir, "$host.png").outputStream().use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }


    /**
     * Download a logo/favicon from a direct URL (or engine home) and cache it under the given key host.
     * Safe to call repeatedly; no-ops if already cached at equal or higher resolution.
     */
    fun fetchAndCache(keyUrl: String, directLogoUrl: String? = null) {
        val host = hostOf(keyUrl)
        if (host.isBlank()) return
        if (favicons[host] != null) return
        ioScope.launch {
            val urls = listOfNotNull(
                directLogoUrl,
                keyUrl.trimEnd('/') + "/favicon.ico",
                "https://www.google.com/s2/favicons?domain=${host}&sz=64",
            )
            for (u in urls) {
                val bmp = runCatching {
                    val conn = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 4000
                    conn.readTimeout = 6000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
                if (bmp != null && !bmp.isRecycled && bmp.width > 8) {
                    put(keyUrl, bmp)
                    return@launch
                }
            }
        }
    }

    fun clear() {
        accessOrder.clear()
        favicons.clear()
        diskDir?.listFiles()?.forEach { it.delete() }
    }
}
