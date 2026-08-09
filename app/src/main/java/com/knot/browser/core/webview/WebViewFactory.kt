package com.knot.browser.core.webview

import android.view.ViewGroup
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/**
 * Central place that configures every new WebView Knot creates. Settings
 * here are deliberate choices, not defaults left untouched:
 *
 * - JS + DOM storage enabled: required for the modern web to work at all.
 * - File access disabled: no reason a general-purpose browser tab needs
 *   to read app-local files; closes off a common WebView attack surface.
 * - Mixed content blocked: don't silently downgrade HTTPS pages.
 * - Third-party cookies allowed only because most real sites break
 *   without them (login/SSO flows) -- revisit if/when Knot gets a
 *   privacy-focused mode.
 */
object WebViewFactory {

    fun create(
        container: ViewGroup,
        tabId: String,
        callbacks: WebViewCallbacks,
    ): WebView {
        val webView = WebView(container.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            configureSettings(settings)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            webViewClient = KnotWebViewClient(tabId, callbacks)
            webChromeClient = KnotWebChromeClient(tabId, callbacks)
            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                callbacks.onDownloadRequested(tabId, url, userAgent, mimeType, contentDisposition, contentLength)
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        return webView
    }

    private fun configureSettings(settings: WebSettings) {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        settings.allowFileAccess = false
        settings.allowContentAccess = false

        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = true
        settings.textZoom = 100
        settings.loadsImagesAutomatically = true
        settings.blockNetworkLoads = false
        settings.setSupportZoom(false)
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
    }
}
