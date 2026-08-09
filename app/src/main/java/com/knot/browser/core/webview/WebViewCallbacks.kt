package com.knot.browser.core.webview

import android.graphics.Bitmap

/**
 * Everything a tab's WebView needs to report upward. Kept as a plain
 * interface so the ViewModel layer never has to import android.webkit
 * types directly -- WebViewClient/WebChromeClient implementations call
 * these, and the ViewModel translates into Tab state updates.
 */
interface WebViewCallbacks {
    fun onPageStarted(tabId: String, url: String)
    fun onPageFinished(tabId: String, url: String)
    fun onProgressChanged(tabId: String, progress: Int)
    fun onTitleChanged(tabId: String, title: String)
    fun onFaviconChanged(tabId: String, favicon: Bitmap?)
    fun onNavigationStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean)
    /** Returning true means "I handled it, don't let WebView navigate" --
     * used to intercept things like custom URI schemes later on. */
    fun shouldOverrideUrl(tabId: String, url: String): Boolean
    fun onReceivedError(tabId: String, errorDescription: String, isMainFrame: Boolean)
    fun onDownloadRequested(
        tabId: String,
        url: String,
        userAgent: String,
        mimeType: String,
        contentDisposition: String,
        contentLength: Long,
    )
}
