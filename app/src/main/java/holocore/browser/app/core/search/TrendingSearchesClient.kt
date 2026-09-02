package holocore.browser.app.core.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Live English trending topics for the home page. Filters out random names
 * and non-English strings. Never returns a hardcoded placeholder list.
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
            val collected = LinkedHashSet<String>()
            fetchGoogleNews()?.let { collected += it }
            fetchGoogleRss()?.let { collected += it }
            fetchGoogleDailyTrends()?.let { collected += it }
            collected.mapNotNull(::cleanTopic).distinctBy { it.lowercase() }.take(8)
        }
        cache = Snapshot(terms, System.currentTimeMillis())
        terms
    }

    private fun fetchGoogleNews(): List<String>? {
        val body = get("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en") ?: return null
        val titles = TITLE_TAG.findAll(body).map { decodeXml(it.groupValues[1]).trim() }
            .filter { it.isNotBlank() && !it.equals("Google News", ignoreCase = true) }
            .map { it.substringBefore(" - ").substringBefore(" | ") }
            .toList()
        return titles.takeIf { it.size >= 3 }
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

    internal fun cleanTopic(raw: String): String? {
        var text = raw.trim()
            .replace(Regex("\\s+"), " ")
            .trim('"', '\'', '“', '”', '‘', '’')
        if (text.length > 42) {
            text = text.take(42).substringBeforeLast(' ').ifBlank { text.take(42) }
        }
        if (text.length < 3) return null
        val letters = text.count { it.isLetter() }
        if (letters < 3) return null
        if (text.any { it.isLetter() && it.code > 0x024F }) return null
        if (!text.all { it.isLetterOrDigit() || it in " -'&.,!?" }) return null
        val titled = titleCase(text)
        if (looksLikePersonName(titled)) return null
        if (titled.equals("Google News", ignoreCase = true)) return null
        return titled
    }

    internal fun looksLikePersonName(text: String): Boolean {
        val parts = text.split(' ').filter { it.isNotBlank() }
        if (parts.size != 2) return false
        return parts.all { token ->
            token.length in 2..12 &&
                token.first().isUpperCase() &&
                token.drop(1).all { it.isLowerCase() || it == '\'' }
        }
    }

    internal fun titleCase(text: String): String {
        val small = setOf("a", "an", "the", "of", "and", "or", "for", "to", "in", "on", "at", "vs")
        val words = text.lowercase().split(' ').filter { it.isNotBlank() }
        return words.mapIndexed { index, word ->
            if (index > 0 && word in small) word
            else word.replaceFirstChar { it.uppercase() }
        }.joinToString(" ")
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, application/rss+xml, text/xml, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
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
