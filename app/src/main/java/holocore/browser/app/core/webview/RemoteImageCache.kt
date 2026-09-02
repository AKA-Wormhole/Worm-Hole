package holocore.browser.app.core.webview

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

/** In-memory bitmap cache for Discover / article hero images. */
object RemoteImageCache {
    private const val MAX = 24
    private val images: SnapshotStateMap<String, Bitmap> = mutableStateMapOf()
    private val inflight = HashSet<String>()
    private val order = ArrayDeque<String>()
    private val scope = CoroutineScope(Dispatchers.IO)

    fun get(url: String): Bitmap? = images[url]

    fun fetch(url: String) {
        if (url.isBlank()) return
        if (images[url] != null) return
        synchronized(inflight) {
            if (!inflight.add(url)) return
        }
        scope.launch {
            val bmp = runCatching {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 8000
                conn.instanceFollowRedirects = true
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.inputStream.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            }.getOrNull()
            if (bmp != null && !bmp.isRecycled && bmp.width > 16) {
                images[url] = bmp
                synchronized(order) {
                    order.remove(url)
                    order.addLast(url)
                    while (order.size > MAX) {
                        val oldest = order.removeFirstOrNull() ?: break
                        images.remove(oldest)
                    }
                }
            }
            synchronized(inflight) { inflight.remove(url) }
        }
    }
}
