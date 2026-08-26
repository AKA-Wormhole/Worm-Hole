package com.wormhole.browser.core.gecko

/** Shared find-in-page surface for WebView and Gecko controllers. */
interface FindController {
    val query: String
    val activeMatchIndex: Int
    val totalMatches: Int
    val isActive: Boolean
    fun start()
    fun search(text: String)
    fun findNext()
    fun findPrevious()
    fun stop()
}
