package com.wormhole.browser.core.settings

import android.net.Uri

enum class SearchEngine(
    val id: String,
    val displayName: String,
    /** Canonical homepage used to resolve the real site logo / favicon. */
    val homeUrl: String,
    /** High-quality official favicon / apple-touch / brand icon URL when available. */
    val logoUrl: String,
) {
    GOOGLE(
        id = "google",
        displayName = "Google",
        homeUrl = "https://www.google.com/",
        logoUrl = "https://www.google.com/favicon.ico",
    ) {
        override fun buildQueryUrl(query: String): String =
            "https://www.google.com/search?q=${Uri.encode(query)}"
    },

    DUCKDUCKGO(
        id = "duckduckgo",
        displayName = "DuckDuckGo",
        homeUrl = "https://duckduckgo.com/",
        logoUrl = "https://duckduckgo.com/favicon.ico",
    ) {
        override fun buildQueryUrl(query: String): String =
            "https://duckduckgo.com/?q=${Uri.encode(query)}"
    },

    BING(
        id = "bing",
        displayName = "Bing",
        homeUrl = "https://www.bing.com/",
        logoUrl = "https://www.bing.com/sa/simg/favicon-2x.ico",
    ) {
        override fun buildQueryUrl(query: String): String =
            "https://www.bing.com/search?q=${Uri.encode(query)}"
    },

    YAHOO(
        id = "yahoo",
        displayName = "Yahoo",
        homeUrl = "https://www.yahoo.com/",
        logoUrl = "https://s.yimg.com/cv/apiv2/default/icons/favicon_y19_32x32_custom.ico",
    ) {
        override fun buildQueryUrl(query: String): String =
            "https://search.yahoo.com/search?p=${Uri.encode(query)}"
    },

    BRAVE(
        id = "brave",
        displayName = "Brave Search",
        homeUrl = "https://search.brave.com/",
        logoUrl = "https://cdn.search.brave.com/serp/favicon.ico",
    ) {
        override fun buildQueryUrl(query: String): String =
            "https://search.brave.com/search?q=${Uri.encode(query)}"
    },

    ECOSIA(
        id = "ecosia",
        displayName = "Ecosia",
        homeUrl = "https://www.ecosia.org/",
        logoUrl = "https://cdn-static.ecosia.org/static/icons/favicon.ico",
    ) {
        override fun buildQueryUrl(query: String): String =
            "https://www.ecosia.org/search?q=${Uri.encode(query)}"
    },

    STARTPAGE(
        id = "startpage",
        displayName = "Startpage",
        homeUrl = "https://www.startpage.com/",
        logoUrl = "https://www.startpage.com/sp/cdn/images/favicon-gradient.ico",
    ) {
        override fun buildQueryUrl(query: String): String =
            "https://www.startpage.com/sp/search?query=${Uri.encode(query)}"
    };

    abstract fun buildQueryUrl(query: String): String

    companion object {
        val DEFAULT = GOOGLE

        fun fromId(id: String?): SearchEngine =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
