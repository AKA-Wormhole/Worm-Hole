package com.wormhole.browser.core.browser

import java.util.UUID

data class Tab(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",

    val displayUrl: String = "",
    val faviconUrl: String? = null,
    val isLoading: Boolean = false,
    val loadProgress: Float = 0f,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isSecure: Boolean = false,
    val spaceId: String = Space.DEFAULT_SPACE_ID,

    val isIncognito: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis(),

    val isBlankTab: Boolean = true,

    val sortOrder: Int = 0,
)
