package com.wormhole.browser.core.gecko

import com.wormhole.browser.core.ai.TranslateLanguage
import com.wormhole.browser.core.ai.TranslateLanguages
import com.wormhole.browser.core.translate.ArgosTranslateClient
import com.wormhole.browser.core.translate.LingvaTranslateClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mozilla.geckoview.GeckoSession
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Page translation without the Gecko page bridge.
 *
 * GeckoView has no public evaluateJS API, and the bundled WebExtension bridge
 * is unreliable. Translation therefore uses the page URL directly:
 * Microsoft Translate the Web first, then a fetched-text reader document.
 */
object PageTranslator {

    data class Detection(
        val code: String,
        val displayName: String,
        val confident: Boolean,
    )

    enum class Mode { IN_PAGE, VIEWER }

    sealed interface Result {
        data class Applied(val language: String, val mode: Mode) : Result
        data class Error(val message: String) : Result
    }

    private val argos = ArgosTranslateClient(
        hosts = ArgosTranslateClient.ARGOS_HOSTS,
    )
    private val libre = ArgosTranslateClient(
        hosts = ArgosTranslateClient.LIBRETRANSLATE_HOSTS,
    )
    private val lingva = LingvaTranslateClient()

    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun detectLanguage(session: GeckoSession): Detection? {
        val sample = readReadableText(session).trim().take(400)
        if (sample.isBlank()) return null
        val detected = detectSample(sample) ?: return null
        val code = detected.first.lowercase().substringBefore('-')
        if (code == "und" || code == "en") return null
        return Detection(code, TranslateLanguages.displayName(code), detected.second)
    }

    suspend fun translatePage(
        session: GeckoSession,
        language: TranslateLanguage,
        pageUrl: String = "",
        onOpenViewer: ((String) -> Unit)? = null,
    ): Result {
        val viewer = viewerUrl(pageUrl, language.code)
        if (viewer != null && onOpenViewer != null) {
            onOpenViewer(viewer)
            return Result.Applied(language.displayName, Mode.VIEWER)
        }

        val sourceText = fetchPageText(pageUrl).ifBlank { readReadableText(session) }
        if (sourceText.length < 40) {
            return Result.Error("Open a finished https page to translate.")
        }
        val chunks = sourceText.chunked(900).take(8)
        val translated = translateTexts(chunks, language.code)
            ?: return Result.Error("Couldn't reach a translation server. Try again.")
        val body = htmlEscape(translated.joinToString(""))
        val title = htmlEscape("Translated to ${language.displayName}")
        val document = """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <title>$title</title>
              <style>
                body { font-family: sans-serif; line-height: 1.5; padding: 16px; max-width: 40rem; margin: 0 auto; }
                h1 { font-size: 1.1rem; }
                p { white-space: pre-wrap; }
              </style>
            </head>
            <body>
              <h1>$title</h1>
              <p>$body</p>
            </body>
            </html>
        """.trimIndent()
        val dataUrl = "data:text/html;charset=utf-8," + URLEncoder.encode(document, StandardCharsets.UTF_8)
        if (onOpenViewer != null) {
            onOpenViewer(dataUrl)
            return Result.Applied(language.displayName, Mode.VIEWER)
        }
        session.loadUri(dataUrl)
        return Result.Applied(language.displayName, Mode.VIEWER)
    }

    private fun viewerUrl(pageUrl: String, targetCode: String): String? {
        val url = pageUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        if (url.startsWith("data:")) return null
        val target = targetCode.lowercase().substringBefore('-').ifBlank { "en" }
        val encoded = URLEncoder.encode(url, StandardCharsets.UTF_8)
        return "https://www.translatetheweb.com/?from=&to=$target&a=$encoded"
    }

    private suspend fun fetchPageText(pageUrl: String): String = withContext(Dispatchers.IO) {
        val url = pageUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return@withContext ""
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Accept-Language", "en-US,en;q=0.9")
            .get()
            .build()
        val html = runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull().orEmpty()
        stripHtml(html).trim()
    }

    private fun stripHtml(html: String): String {
        if (html.isBlank()) return ""
        var text = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?is)<noscript[^>]*>.*?</noscript>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n\n")
            .replace(Regex("(?i)</h[1-6]>"), "\n\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#39;"), "'")
            .replace(Regex("\\s+"), " ")
        return text.take(12000)
    }

    private fun htmlEscape(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private suspend fun detectSample(sample: String): Pair<String, Boolean>? {
        argos.detect(sample)?.let { return it.code to it.confident }
        lingva.detect(sample)?.let { return it.code to it.confident }
        libre.detect(sample)?.let { return it.code to it.confident }
        return null
    }

    private suspend fun translateTexts(texts: List<String>, targetCode: String): List<String>? {
        val (hits, missing) = com.wormhole.browser.core.translate.TranslationCache.getAll("auto", targetCode, texts)
        if (missing.isEmpty()) return hits.map { it.orEmpty() }
        val need = missing.map { texts[it] }
        val fetched = argos.translate(need, targetCode = targetCode)
            ?: lingva.translate(need, targetCode = targetCode)
            ?: libre.translate(need, targetCode = targetCode)
            ?: return null
        if (fetched.size != need.size) return null
        val out = hits.toMutableList()
        missing.forEachIndexed { i, index ->
            com.wormhole.browser.core.translate.TranslationCache.put("auto", targetCode, texts[index], fetched[i])
            out[index] = fetched[i]
        }
        return out.map { it.orEmpty() }
    }

    suspend fun restoreOriginal(session: GeckoSession): Boolean {
        return try {
            session.goBack()
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun readReadableText(session: GeckoSession): String {
        val viaJs = GeckoJs.evaluate(
            session,
            "(function(){try{var t=(document.body&&document.body.innerText)||'';return String(t).slice(0,16000);}catch(e){return ''}})()",
        )
        if (viaJs == GeckoJs.UNAVAILABLE_SENTINEL || viaJs.startsWith("ERR:")) return ""
        return viaJs.trim()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
}
