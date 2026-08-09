package com.knot.browser.core.webview

import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * WebView already implements find-in-page natively (findAllAsync /
 * findNext / clearMatches) -- this wraps it with Compose-observable state
 * for the match count / index, since WebView reports that back only
 * through a listener callback.
 */
class FindInPageController(private val webView: WebView) {

    var query: String by mutableStateOf("")
        private set

    var activeMatchIndex: Int by mutableStateOf(0)
        private set

    var totalMatches: Int by mutableStateOf(0)
        private set

    var isActive: Boolean by mutableStateOf(false)
        private set

    init {
        webView.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                activeMatchIndex = activeMatchOrdinal
                totalMatches = numberOfMatches
            }
        }
    }

    fun start() {
        isActive = true
    }

    fun search(text: String) {
        query = text
        if (text.isEmpty()) {
            webView.clearMatches()
            totalMatches = 0
            activeMatchIndex = 0
        } else {
            webView.findAllAsync(text)
        }
    }

    fun findNext() = webView.findNext(true)

    fun findPrevious() = webView.findNext(false)

    fun stop() {
        isActive = false
        query = ""
        totalMatches = 0
        activeMatchIndex = 0
        webView.clearMatches()
    }
}
