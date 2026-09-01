package com.wormhole.browser.core.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * Live "best of the web" stories for the home Discover row.
 * Never returns a hardcoded article list — every card is fetched and ranked.
 */
object DiscoverClient {

    data class Story(
        val title: String,
        val url: String,
        val source: String,
        val category: String,
        val imageUrl: String?,
        val readMinutes: Int,
        val score: Int,
    )

    @Volatile
    private var cache: Pair<List<Story>, Long>? = null
    private val mutex = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun load(force: Boolean = false): List<Story> = mutex.withLock {
        val cached = cache
        if (!force && cached != null && System.currentTimeMillis() - cached.second < CACHE_MS) {
            return cached.first
        }
        val stories = withContext(Dispatchers.IO) { fetchBest() }
        cache = stories to System.currentTimeMillis()
        stories
    }

    private suspend fun fetchBest(): List<Story> = coroutineScope {
        val wiki = async { runCatching { fetchWikipediaFeatured() }.getOrDefault(emptyList()) }
        val space = async { runCatching { fetchRss("https://www.space.com/feeds/all", "space.com", "Science") }.getOrDefault(emptyList()) }
        val wired = async { runCatching { fetchRss("https://www.wired.com/feed/rss", "wired.com", "Tech") }.getOrDefault(emptyList()) }
        val planet = async { runCatching { fetchRss("https://www.lonelyplanet.com/news/feed", "lonelyplanet.com", "Travel") }.getOrDefault(emptyList()) }
        val news = async { runCatching { fetchGoogleNews() }.getOrDefault(emptyList()) }
        val pooled = (wiki.await() + space.await() + wired.await() + planet.await() + news.await())
            .filter { it.title.length >= 12 && it.url.startsWith("http") }
            .filterNot { looksUnsafe(it.title) }
            .distinctBy { it.url.lowercase() }
            .distinctBy { it.title.lowercase().take(40) }
        rank(pooled).take(8)
    }

    private fun rank(stories: List<Story>): List<Story> {
        val usedCategories = mutableSetOf<String>()
        val usedSources = mutableSetOf<String>()
        val chosen = ArrayList<Story>()
        val ordered = stories.sortedByDescending { story ->
            var s = story.score
            if (!story.imageUrl.isNullOrBlank()) s += 40
            if (story.readMinutes in 3..8) s += 8
            s
        }
        for (story in ordered) {
            val catPenalty = if (story.category in usedCategories) 1 else 0
            val srcPenalty = if (story.source in usedSources) 1 else 0
            if (catPenalty + srcPenalty == 2 && chosen.size < 4) continue
            chosen += story
            usedCategories += story.category
            usedSources += story.source
            if (chosen.size >= 8) break
        }
        if (chosen.size < 3) {
            for (story in ordered) {
                if (chosen.none { it.url == story.url }) chosen += story
                if (chosen.size >= 6) break
            }
        }
        return chosen
    }

    private fun fetchWikipediaFeatured(): List<Story> {
        val date = LocalDate.now(ZoneOffset.UTC)
        val path = "%04d/%02d/%02d".format(date.year, date.monthValue, date.dayOfMonth)
        val body = get("https://en.wikipedia.org/api/rest_v1/feed/featured/$path") ?: return emptyList()
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val out = ArrayList<Story>()

        root.optJSONObject("tfa")?.let { page ->
            storyFromWikiPage(page, "Featured", 90)?.let { out += it }
        }

        val mostRead = root.optJSONObject("mostread")?.optJSONArray("articles")
        if (mostRead != null) {
            for (i in 0 until mostRead.length()) {
                val page = mostRead.optJSONObject(i) ?: continue
                val views = page.optInt("views", 0)
                storyFromWikiPage(page, "Trending", 50 + (views / 20_000).coerceAtMost(40))?.let { out += it }
            }
        }

        val news = root.optJSONArray("news")
        if (news != null) {
            for (i in 0 until news.length()) {
                val item = news.optJSONObject(i) ?: continue
                val links = item.optJSONArray("links") ?: continue
                val page = links.optJSONObject(0) ?: continue
                storyFromWikiPage(page, "News", 70)?.let { out += it }
            }
        }

        root.optJSONObject("image")?.let { image ->
            val desc = image.optJSONObject("description")?.optString("text").orEmpty().trim()
            val title = desc.ifBlank { image.optString("title").substringAfter(':') }.take(90)
            val filePage = image.optString("file_page").ifBlank {
                "https://commons.wikimedia.org/wiki/" + image.optString("title")
            }
            val thumb = image.optJSONObject("thumbnail")?.optString("source")
                ?: image.optJSONObject("image")?.optString("source")
            if (title.length >= 12 && !looksUnsafe(title)) {
                out += Story(
                    title = title,
                    url = filePage,
                    source = "commons.wikimedia.org",
                    category = "Travel",
                    imageUrl = thumb,
                    readMinutes = 4,
                    score = 75,
                )
            }
        }
        return out
    }

    private fun storyFromWikiPage(page: JSONObject, fallbackCategory: String, score: Int): Story? {
        val title = page.optJSONObject("titles")?.optString("normalized")
            ?.ifBlank { page.optString("title") }
            .orEmpty()
            .replace('_', ' ')
            .trim()
        if (title.length < 8) return null
        if (looksUnsafe(title)) return null
        val url = page.optJSONObject("content_urls")
            ?.optJSONObject("desktop")
            ?.optString("page")
            .orEmpty()
            .ifBlank { "https://en.wikipedia.org/wiki/" + title.replace(' ', '_') }
        val extract = page.optString("extract").trim()
        val image = page.optJSONObject("thumbnail")?.optString("source")
            ?: page.optJSONObject("originalimage")?.optString("source")
        val category = categorize("$title $extract", fallbackCategory)
        val minutes = ((extract.split(' ').size / 180) + 3).coerceIn(3, 8)
        return Story(
            title = title,
            url = url,
            source = "wikipedia.org",
            category = category,
            imageUrl = image,
            readMinutes = minutes,
            score = score + if (image.isNullOrBlank()) 0 else 15,
        )
    }

    private fun fetchRss(feedUrl: String, source: String, category: String): List<Story> {
        val body = get(feedUrl) ?: return emptyList()
        val items = ITEM_TAG.findAll(body).map { it.groupValues[1] }.toList()
        return items.take(8).mapNotNull { item ->
            val title = decodeXml(tag(item, "title").orEmpty()).trim()
            val link = decodeXml(tag(item, "link").orEmpty()).trim()
                .ifBlank { HREF.find(item)?.groupValues?.getOrNull(1).orEmpty() }
            if (title.length < 12 || !link.startsWith("http")) return@mapNotNull null
            if (looksUnsafe(title)) return@mapNotNull null
            val image = mediaUrl(item)
            val desc = decodeXml(tag(item, "description").orEmpty())
            val minutes = ((desc.split(' ').size / 180) + 4).coerceIn(3, 8)
            Story(
                title = title,
                url = link,
                source = source,
                category = categorize("$title $desc", category),
                imageUrl = image,
                readMinutes = minutes,
                score = 55 + if (image != null) 20 else 0,
            )
        }
    }

    private fun fetchGoogleNews(): List<Story> {
        val body = get("https://news.google.com/rss?hl=en-US&gl=US&ceid=US:en") ?: return emptyList()
        val items = ITEM_TAG.findAll(body).map { it.groupValues[1] }.toList()
        return items.take(10).mapNotNull { item ->
            val rawTitle = decodeXml(tag(item, "title").orEmpty()).trim()
            val title = rawTitle.substringBefore(" - ").substringBefore(" | ").trim()
            val source = rawTitle.substringAfter(" - ", missingDelimiterValue = "")
                .substringAfter(" | ", missingDelimiterValue = "news")
                .ifBlank { "news.google.com" }
                .lowercase()
                .replace(" ", "")
                .let { cleaned -> if ('.' in cleaned) cleaned else "$cleaned.com" }
            val link = decodeXml(tag(item, "link").orEmpty()).trim()
            if (title.length < 12 || !link.startsWith("http")) return@mapNotNull null
            if (looksUnsafe(title)) return@mapNotNull null
            Story(
                title = title,
                url = link,
                source = source.removePrefix("www."),
                category = categorize(title, "News"),
                imageUrl = null,
                readMinutes = 5,
                score = 35,
            )
        }
    }

    private fun mediaUrl(item: String): String? {
        MEDIA_URL.find(item)?.groupValues?.getOrNull(1)?.let { if (it.startsWith("http")) return it }
        ENCLOSURE.find(item)?.groupValues?.getOrNull(1)?.let { if (it.startsWith("http")) return it }
        IMG_SRC.find(item)?.groupValues?.getOrNull(1)?.let { if (it.startsWith("http")) return it }
        return null
    }

    private fun tag(xml: String, name: String): String? {
        val cdata = Regex(
            "<$name[^>]*>\\s*<!\\[CDATA\\[(.*?)]]>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(xml)?.groupValues?.getOrNull(1)
        if (!cdata.isNullOrBlank()) return cdata
        return Regex(
            "<$name[^>]*>([^<]+)</$name>",
            RegexOption.IGNORE_CASE,
        ).find(xml)?.groupValues?.getOrNull(1)
    }

    internal fun categorize(text: String, fallback: String): String {
        val t = text.lowercase()
        return when {
            listOf("space", "planet", "nasa", "black hole", "galaxy", "star", "moon", "mars", "science", "physics", "biology").any { it in t } -> "Science"
            listOf("ai ", "artificial", "chip", "software", "app", "tech", "computer", "robot", "on-device", "gpu").any { it in t } -> "Tech"
            listOf("travel", "city", "island", "park", "beach", "mountain", "hotel", "hidden", "tour").any { it in t } -> "Travel"
            listOf("history", "ancient", "war", "king", "empire", "museum").any { it in t } -> "History"
            listOf("film", "music", "song", "actor", "movie", "book").any { it in t } -> "Culture"
            else -> fallback
        }
    }

    internal fun looksUnsafe(title: String): Boolean {
        val t = title.lowercase()
        val blocked = listOf(
            "sex", "porn", "nude", "nsfw", "kill", "murder", "massacre",
            "suicide", "terror", "bomb", "shooting", "rape", "explicit",
        )
        return blocked.any { it in t }
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

    private val ITEM_TAG = Regex("<item\\b[^>]*>(.*?)</item>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val MEDIA_URL = Regex("(?:media:content|media:thumbnail)[^>]+url=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val ENCLOSURE = Regex("<enclosure[^>]+url=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val IMG_SRC = Regex("<img[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private val HREF = Regex("href=[\"'](https?://[^\"']+)[\"']", RegexOption.IGNORE_CASE)
    private const val CACHE_MS = 30 * 60 * 1000L
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
}
