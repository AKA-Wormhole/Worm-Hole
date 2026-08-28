package com.wormhole.browser.core.gecko

import android.graphics.Bitmap

/**
 * Engine-agnostic browser callbacks (formerly WebViewCallbacks).
 * Implemented by BrowserViewModel; used by GeckoView host.
 */
interface EngineCallbacks {
    fun onPageStarted(tabId: String, url: String)
    fun onUrlChanged(tabId: String, url: String) {}
    fun onPageFinished(tabId: String, url: String)
    fun onProgressChanged(tabId: String, progress: Int)
    fun onTitleChanged(tabId: String, title: String)
    fun onFaviconChanged(tabId: String, favicon: Bitmap?)
    fun onNavigationStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean)
    fun shouldOverrideUrl(tabId: String, url: String): Boolean
    fun onReceivedError(
        tabId: String,
        errorDescription: String,
        isMainFrame: Boolean,
        failedUrl: String = "",
        title: String = "Connection failed",
        hint: String = "Check your internet connection or DNS, then try again.",
    )
    fun onDownloadRequested(
        tabId: String,
        url: String,
        userAgent: String,
        mimeType: String,
        contentDisposition: String,
        contentLength: Long,
    )

    fun onBlobDownloadReady(tabId: String, base64Data: String, mimeType: String, fileName: String) {}
    fun onBlobDownloadFailed(tabId: String, error: String) {}
    fun onGeolocationPermissionRequested(
        tabId: String,
        origin: String,
        onAllow: (retain: Boolean) -> Unit,
        onDeny: () -> Unit,
    ) {
        onDeny()
    }

    fun onMediaPermissionRequested(
        tabId: String,
        origin: String,
        resources: List<String>,
        onGrant: (List<String>) -> Unit,
        onDeny: () -> Unit,
    ) {
        onDeny()
    }

    fun onRendererCrashed(tabId: String) {}

    fun onSslErrorReceived(
        tabId: String,
        url: String,
        primaryErrorCode: Int,
        onProceed: () -> Unit,
        onCancel: () -> Unit,
    ) {
        onCancel()
    }

    fun onNewWindowRequested(tabId: String, url: String): String? = null

    fun onSiteContextMenu(tabId: String, url: String, title: String) {}
}

/** @deprecated Use EngineCallbacks */
typealias WebViewCallbacks = EngineCallbacks
