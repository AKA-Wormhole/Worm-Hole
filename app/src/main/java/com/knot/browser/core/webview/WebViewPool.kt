package com.knot.browser.core.webview

import android.util.Log
import android.view.ViewGroup
import android.webkit.WebView

/**
 * Owns the real [WebView] instances backing tabs, keyed by tab id.
 *
 * WebViews are heavyweight (each one is roughly its own mini-Chromium
 * render process context) and Compose recomposition/navigation churn
 * makes it easy to accidentally leak them if you create-and-forget on
 * every recomposition. This pool is the single place WebViews get
 * created and destroyed, so lifecycle is auditable in one file.
 *
 * Strategy: keep up to [maxLiveWebViews] real WebView instances alive
 * (their history stack and scroll position survive tab switches, which
 * is what makes switching tabs feel instant). Beyond that cap, the
 * least-recently-used tab's WebView is destroyed and will be recreated
 * (as a fresh load of tab.url) if the user switches back to it.
 *
 * Deliberately does NOT hold a Context itself -- WebView should be
 * constructed with a UI Context (an Activity/View context), not an
 * Application context, which is a known source of subtle bugs/crashes
 * on some OEM WebView implementations. Callers supply a ready-made
 * WebView via the [getOrCreate] `create` lambda instead, built from
 * whatever UI Context they have on hand.
 */
class WebViewPool(
    private val maxLiveWebViews: Int = 8,
) {
    private val liveViews = LinkedHashMap<String, WebView>(16, 0.75f, true) // access-order = LRU

    fun get(tabId: String): WebView? = liveViews[tabId]

    fun isLive(tabId: String): Boolean = liveViews.containsKey(tabId)

    /**
     * Returns the live WebView for [tabId], creating one via [create] if
     * none exists yet. [create] is only invoked on a cache miss.
     */
    fun getOrCreate(tabId: String, create: () -> WebView): WebView {
        liveViews[tabId]?.let { return it }
        evictIfOverCapacity()
        val webView = create()
        liveViews[tabId] = webView
        return webView
    }

    fun destroy(tabId: String) {
        liveViews.remove(tabId)?.let { webView ->
            try {
                webView.stopLoading()
                webView.clearHistory()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying WebView for tab $tabId", e)
            }
        }
    }

    fun destroyAll() {
        liveViews.keys.toList().forEach { destroy(it) }
    }

    private fun evictIfOverCapacity() {
        while (liveViews.size >= maxLiveWebViews) {
            val oldestKey = liveViews.keys.firstOrNull() ?: break
            Log.d(TAG, "Evicting WebView for tab $oldestKey (pool at capacity)")
            destroy(oldestKey)
        }
    }

    companion object {
        private const val TAG = "WebViewPool"
    }
}
