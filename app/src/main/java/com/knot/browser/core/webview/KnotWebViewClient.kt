package com.knot.browser.core.webview

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Per-tab navigation client. One instance per WebView (created fresh in
 * [WebViewFactory] alongside the WebView itself), holding the tabId it
 * belongs to as a closure so callbacks can report which tab changed.
 */
class KnotWebViewClient(
    private val tabId: String,
    private val callbacks: WebViewCallbacks,
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let { callbacks.onPageStarted(tabId, it) }
        callbacks.onNavigationStateChanged(tabId, view.canGoBack(), view.canGoForward())
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        url?.let { callbacks.onPageFinished(tabId, it) }
        callbacks.onNavigationStateChanged(tabId, view.canGoBack(), view.canGoForward())
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // Delegate everything (including non-http(s) schemes like mailto:,
        // tel:, intent:, market:) to the callback. It decides what counts
        // as "handled" -- e.g. launching an external Intent for schemes
        // WebView can't render itself. This class deliberately holds no
        // Context, so it can't launch Intents directly.
        return callbacks.shouldOverrideUrl(tabId, request.url.toString())
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            callbacks.onReceivedError(tabId, error.description?.toString() ?: "Unknown error", true)
        }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): android.webkit.WebResourceResponse? =
        null // no ad-blocking/content-filtering yet -- a natural later stage
}
