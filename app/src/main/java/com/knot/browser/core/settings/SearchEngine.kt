package com.knot.browser.core.settings

import android.net.Uri

/**
 * The set of search engines Knot can hand a typed query off to. Adding a
 * new engine (Bing, DuckDuckGo, etc.) later means adding one more entry
 * here with its URL template -- nothing else in the app needs to change,
 * since BrowserViewModel.resolveInput just calls [buildQueryUrl].
 */
enum class SearchEngine(
    val id: String,
    val displayName: String,
) {
    GOOGLE(id = "google", displayName = "Google") {
        override fun buildQueryUrl(query: String): String =
            "https://www.google.com/search?q=${Uri.encode(query)}"
    },

    KNOT(id = "knot", displayName = "Knot Search") {
        override fun buildQueryUrl(query: String): String =
            "knot://search?q=${Uri.encode(query)}"
    };

    abstract fun buildQueryUrl(query: String): String

    companion object {
        val DEFAULT = GOOGLE

        fun fromId(id: String?): SearchEngine =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
