package com.knot.browser.core.webview

import android.webkit.ValueCallback
import android.webkit.WebView
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import kotlin.coroutines.resume

/**
 * Small WebView utilities for the Assistant/Translate/desktop-site menu
 * actions -- these all need to reach into a live WebView beyond what
 * WebViewCallbacks already covers, but none of them are big enough to
 * justify their own controller class the way find-in-page is.
 */
object PageContentExtractor {

    // innerText (not innerHTML) so Gemini gets readable prose instead of
    // markup; capped so a huge page doesn't blow past request size/token
    // limits or the cost of a single summarize/translate call.
    private const val EXTRACTION_SCRIPT = """
        (function() {
            try {
                var text = document.body ? document.body.innerText : '';
                return text.substring(0, 20000);
            } catch (e) {
                return '';
            }
        })();
    """

    suspend fun extractVisibleText(webView: WebView): String = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(
            EXTRACTION_SCRIPT,
            ValueCallback<String> { rawResult ->
                val text = decodeJsStringResult(rawResult)
                if (continuation.isActive) continuation.resume(text)
            },
        )
    }

    // evaluateJavascript hands back its result as a JSON-encoded string
    // literal (quotes and escapes included), not the raw text -- decode
    // it properly rather than hand-stripping quotes, which breaks on any
    // page whose text contains escaped characters.
    private fun decodeJsStringResult(raw: String?): String {
        if (raw.isNullOrBlank() || raw == "null") return ""
        return try {
            org.json.JSONTokener(raw).nextValue() as? String ?: ""
        } catch (e: JSONException) {
            ""
        }
    }
}

/**
 * User-agent strings for "Request desktop site". Swapping the WebView's
 * user agent alone is what every mobile browser's desktop-mode toggle
 * actually does -- the server decides what markup to send based on this
 * string, Knot doesn't need to do any layout work itself.
 */
object DesktopSiteMode {
    private const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"

    fun apply(webView: WebView, useDesktopSite: Boolean) {
        val settings = webView.settings
        settings.userAgentString = if (useDesktopSite) DESKTOP_USER_AGENT else null
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        webView.reload()
    }
}
