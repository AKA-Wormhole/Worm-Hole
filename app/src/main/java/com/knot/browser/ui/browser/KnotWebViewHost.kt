package com.knot.browser.ui.browser

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.knot.browser.core.browser.Tab
import com.knot.browser.core.webview.TabThumbnailCache
import com.knot.browser.core.webview.WebViewCallbacks
import com.knot.browser.core.webview.WebViewFactory
import com.knot.browser.core.webview.WebViewPool

/**
 * Renders exactly ONE tab's WebView -- the currently active one. Only the
 * active tab is composed into the view hierarchy at a time; inactive
 * tabs' WebViews stay alive inside [webViewPool] (up to its cap) but
 * detached from any parent view. That's what makes switching tabs
 * preserve scroll position/history without keeping every tab's view on
 * screen at once.
 *
 * This composable does not create tabs or manage the tab list -- it only
 * bridges [tab.id] to a real WebView, loading [tab.url] into it the
 * first time that WebView is created for this tab.
 */
@Composable
fun KnotWebViewHost(
    tab: Tab,
    webViewPool: WebViewPool,
    callbacks: WebViewCallbacks,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            FrameLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { container ->
            val wasAlreadyLive = webViewPool.isLive(tab.id)

            val webView = webViewPool.getOrCreate(tab.id) {
                WebViewFactory.create(container, tab.id, callbacks)
            }

            // The WebView may currently be parented to a different
            // container (e.g. it was the active tab in a previous
            // composition of this same host, or -- more commonly -- this
            // is the very first attach). Re-parent defensively.
            val currentParent = webView.parent as? FrameLayout
            if (currentParent !== container) {
                currentParent?.removeView(webView)
                container.removeAllViews()
                container.addView(webView)
            }

            if (!wasAlreadyLive && tab.url.isNotBlank()) {
                webView.loadUrl(tab.url)
            }
        },
        onRelease = { container ->
            // Deliberately do NOT destroy the WebView here: onRelease
            // fires when this AndroidView leaves composition (e.g. user
            // switched tabs), and we want the WebView to survive that so
            // switching back doesn't reload the page. WebViewPool is the
            // only thing that destroys WebViews, via eviction or
            // destroyAll() on Activity teardown.
            container.removeAllViews()
        },
    )

    DisposableEffect(lifecycleOwner, tab.id) {
        val observer = LifecycleEventObserver { _, event ->
            val webView = webViewPool.get(tab.id) ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Refreshes the tab grid's thumbnail once a page finishes loading.
    // Keyed on (tab.id, tab.isLoading) so it fires exactly on the
    // true -> false transition, not on every recomposition while a page
    // is still loading (which would waste time capturing partial frames).
    LaunchedEffect(tab.id, tab.isLoading) {
        if (!tab.isLoading) {
            // A short delay so the capture reflects the page's post-load
            // paint rather than whatever frame was on screen the instant
            // onPageFinished fired -- WebView's own layout/draw pass for
            // the finished page can lag that callback by a beat.
            kotlinx.coroutines.delay(150)
            webViewPool.get(tab.id)?.let { webView ->
                TabThumbnailCache.capture(tab.id, webView)
            }
        }
    }
}
