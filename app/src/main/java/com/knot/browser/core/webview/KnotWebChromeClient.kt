package com.knot.browser.core.webview

import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebView

class KnotWebChromeClient(
    private val tabId: String,
    private val callbacks: WebViewCallbacks,
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        callbacks.onProgressChanged(tabId, newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        callbacks.onTitleChanged(tabId, title ?: view.url.orEmpty())
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        super.onReceivedIcon(view, icon)
        callbacks.onFaviconChanged(tabId, icon)
    }
}
