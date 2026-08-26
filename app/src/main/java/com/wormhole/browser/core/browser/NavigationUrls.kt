package com.wormhole.browser.core.browser

import android.net.Uri

/**
 * URL comparison used to decide whether a Gecko location update is a new
 * navigation (load it) or the same page after a redirect / www canonicalization
 * (update the address bar only).
 */
object NavigationUrls {

    fun isAboutBlank(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.isEmpty() ||
            trimmed.equals("about:blank", ignoreCase = true) ||
            trimmed.equals("about:newtab", ignoreCase = true)
    }

    fun hostsEquivalent(a: String?, b: String?): Boolean {
        val left = normalizeHost(a)
        val right = normalizeHost(b)
        return left.isNotEmpty() && left == right
    }

    fun areEquivalent(left: String, right: String): Boolean {
        if (left.isBlank() || right.isBlank()) return false
        if (left == right) return true
        val a = runCatching { Uri.parse(left.trim()) }.getOrNull() ?: return false
        val b = runCatching { Uri.parse(right.trim()) }.getOrNull() ?: return false
        if (!hostsEquivalent(a.host, b.host)) return false
        val schemeA = a.scheme?.lowercase().orEmpty()
        val schemeB = b.scheme?.lowercase().orEmpty()
        if (schemeA.isNotEmpty() && schemeB.isNotEmpty() && schemeA != schemeB) {
            // http <-> https on the same host/path is still the same document for reload suppression
            if (!(schemeA == "http" && schemeB == "https" || schemeA == "https" && schemeB == "http")) {
                return false
            }
        }
        return normalizePath(a.path) == normalizePath(b.path) &&
            (a.encodedQuery ?: "") == (b.encodedQuery ?: "")
    }

    /** Same page except fragment (in-page jump). */
    fun isSameDocument(left: String, right: String): Boolean {
        if (areEquivalent(left, right)) return true
        val a = stripFragment(left)
        val b = stripFragment(right)
        return a.isNotBlank() && a == b
    }

    fun shouldSkipAutomaticLoad(
        requested: String,
        lastRequested: String,
        lastCommitted: String,
    ): Boolean {
        if (requested.isBlank() || isAboutBlank(requested)) return true
        if (lastCommitted.isBlank()) {
            // Nothing has painted yet — skip only if this exact load is already in flight.
            return lastRequested.isNotBlank() &&
                (areEquivalent(requested, lastRequested) || isSameDocument(requested, lastRequested))
        }
        if (areEquivalent(requested, lastCommitted) || isSameDocument(requested, lastCommitted)) return true
        if (areEquivalent(requested, lastRequested) || isSameDocument(requested, lastRequested)) return true
        return false
    }

    private fun normalizeHost(host: String?): String {
        var value = host?.lowercase()?.trim().orEmpty()
        if (value.startsWith("www.")) value = value.removePrefix("www.")
        if (value.endsWith(".")) value = value.dropLast(1)
        return value
    }

    private fun normalizePath(path: String?): String {
        val raw = path.orEmpty()
        if (raw.isEmpty() || raw == "/") return "/"
        return raw.trimEnd('/')
    }

    private fun stripFragment(url: String): String {
        val index = url.indexOf('#')
        return if (index >= 0) url.substring(0, index) else url
    }
}
