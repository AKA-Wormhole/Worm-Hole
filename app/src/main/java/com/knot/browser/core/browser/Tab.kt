package com.knot.browser.core.browser

import java.util.UUID

/**
 * Immutable snapshot of a single tab's state. The actual WebView instance
 * backing a tab is NOT stored here -- Compose state should stay free of
 * platform view references. WebView instances live in [WebViewPool] and
 * are looked up by [id] when a tab needs to be rendered.
 */
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",
    /** What to show in the address/command bar before the page has loaded
     * or if the user hasn't navigated yet. */
    val displayUrl: String = "",
    val faviconUrl: String? = null,
    val isLoading: Boolean = false,
    val loadProgress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
    val spaceId: String = Space.DEFAULT_SPACE_ID,
    val createdAtMillis: Long = System.currentTimeMillis(),
    /** True for the ephemeral "new tab" state before any navigation. */
    val isBlankTab: Boolean = true,
    /** Manual ordering within its Space's tab list, for drag-reorder
     * (Stage 3 sidebar). New tabs get appended past the current max. */
    val sortOrder: Int = 0,
)
