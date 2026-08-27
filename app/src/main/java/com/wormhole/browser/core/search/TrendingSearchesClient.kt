package com.wormhole.browser.core.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Live trending search terms. Never returns a hardcoded placeholder list —
 * if every network source fails the result is empty.
 */
object TrendingSearchesClient {

    data class Snapshot(val terms: List<String>, val fetchedAt: Long)

    @Volatile
    private var cache: Snapshot? = null
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun load(force: Boolean = false): List<String> = mutex.withLock {
        val cached = cache
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < CACHE_MS) {
            return cached.terms
        }
        val terms = withContext(Dispatchers.IO) {
            fetchGoogleRss()
                ?: fetchGoogleDailyTrends()
                ?: fetchWikipediaMostRead()
                ?: emptyList()
        }.distinct().take(8)
        cache = Snapshot(terms, System.currentTimeMillis())
        terms
    }

    private fun fetchGoogleRss(): List<String>? {
        val body = get("https://trends.google.com/trending/rss?geo=US") ?: return null
        val titles = TITLE_TAG.findAll(body).map { decodeXml(it.groupValues[1]).trim() }
            .filter { it.isNotBlank() && it != "Daily Search Trends" }
            .toList()
        return titles.takeIf { it.size >= 3 }
    }

    private fun fetchGoogleDailyTrends(): List<String>? {
        val body = get("https://trends.google.com/trends/api/dailytrends?hl=en-US&geo=US&ns=15")
            ?: return null
        val json = body.trim().removePrefix(")]}'").trim()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val days = root.optJSONObject("default")?.optJSONArray("trendingSearchesDays") ?: return null
        val out = ArrayList<String>()
        for (d in 0 until days.length()) {
            val searches = days.optJSONObject(d)?.optJSONArray("trendingSearches") ?: continue
            for (i in 0 until searches.length()) {
                val title = searches.optJSONObject(i)
                    ?.optJSONObject("title")
                    ?.optString("query")
                    .orEmpty()
                    .trim()
                if (title.isNotBlank()) out += title
            }
        }
        return out.takeIf { it.size >= 3 }
    }

    private fun fetchWikipediaMostRead(): List<String>? {
        val now = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        now.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val y = now.get(java.util.Calendar.YEAR)
        val m = (now.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
        val d = now.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val body = get("https://en.wikipedia.org/api/rest_v1/feed/featured/$y/$m/$d") ?: return null
        val articles = runCatching {
            JSONObject(body).optJSONObject("mostread")?.optJSONArray("articles")
        }.getOrNull() ?: return null
        val out = ArrayList<String>()
        for (i in 0 until articles.length()) {
            val obj = articles.optJSONObject(i) ?: continue
            val title = obj.optString("normalizedtitle").ifBlank { obj.optString("title") }
                .replace('_', ' ')
                .trim()
            if (title.isBlank()) continue
            if (title.startsWith("Wikipedia:") || title.startsWith("Special:")) continue
            if (title.equals("Main Page", ignoreCase = true)) continue
            out += title
        }
        return out.takeIf { it.size >= 3 }
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, application/rss+xml, text/xml, */*")
            .get()
            .build()
        return runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
    }

    private fun decodeXml(raw: String): String =
        raw.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")

    private val TITLE_TAG = Regex("<title>([^<]+)</title>", RegexOption.IGNORE_CASE)
    private const val CACHE_MS = 30 * 60 * 1000L
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
}
