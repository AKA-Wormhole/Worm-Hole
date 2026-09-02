package holocore.browser.app.core.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Lingva Translate REST client. No API key.
 * GET /api/v1/{source}/{target}/{query}
 */
class LingvaTranslateClient(
    private val httpClient: OkHttpClient = defaultHttpClient,
    private val hosts: List<String> = DEFAULT_HOSTS,
) {

    data class Detection(val code: String, val confident: Boolean)

    suspend fun detect(sample: String): Detection? {
        val trimmed = sample.trim().take(400)
        if (trimmed.isBlank()) return null
        val result = translateOne(trimmed, source = "auto", target = "en") ?: return null
        val code = result.detected?.lowercase()?.substringBefore('-')
        if (code.isNullOrBlank() || code == "und") return null
        return Detection(code, confident = true)
    }

    suspend fun translate(
        texts: List<String>,
        targetCode: String,
        sourceCode: String = "auto",
    ): List<String>? = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val target = targetCode.lowercase().substringBefore('-').ifBlank { "en" }
        val source = sourceCode.lowercase().substringBefore('-').ifBlank { "auto" }
        val (hits, missing) = TranslationCache.getAll(source, target, texts)
        if (missing.isEmpty()) return@withContext hits.map { it.orEmpty() }

        val out = hits.toMutableList()
        missing.forEach { index ->
            val original = texts[index]
            val translated = translateOne(original, source, target)?.text ?: return@withContext null
            TranslationCache.put(source, target, original, translated)
            out[index] = translated
        }
        out.map { it.orEmpty() }
    }

    internal data class LingvaResult(val text: String, val detected: String?)

    private fun translateOne(text: String, source: String, target: String): LingvaResult? {
        val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name()).replace("+", "%20")
        if (encoded.length > MAX_QUERY_CHARS) {
            return translateLong(text, source, target)
        }
        hosts.forEach { host ->
            val url = host.trimEnd('/') + "/api/v1/$source/$target/$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()
            val body = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            }.getOrNull()
            val parsed = body?.let { parse(it) }
            if (parsed != null) return parsed
        }
        return null
    }

    private fun translateLong(text: String, source: String, target: String): LingvaResult? {
        val parts = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }.ifEmpty { listOf(text) }
        val translated = StringBuilder()
        var detected: String? = null
        parts.forEach { part ->
            val piece = if (part.length > 400) part.take(400) else part
            val result = translateOne(piece, source, target) ?: return null
            if (detected == null) detected = result.detected
            if (translated.isNotEmpty()) translated.append(' ')
            translated.append(result.text)
        }
        return LingvaResult(translated.toString(), detected)
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36 HoloCore/1.9"
        private const val MAX_QUERY_CHARS = 1400
        val DEFAULT_HOSTS = listOf(
            "https://lingva.ml",
            "https://translate.igna.wtf",
            "https://translate.plausibility.cloud",
            "https://lingva.lunar.icu",
            "https://translate.projectsegfau.lt",
            "https://lingva.garudalinux.org",
            "https://translate.dr460nf1r3.org",
            "https://translate.jae.fi",
        )
        private val defaultHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun parseTranslation(raw: String): String? = parse(raw)?.text

        internal fun parse(raw: String): LingvaResult? {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val text = json.optString("translation")
            if (text.isBlank()) return null
            val info = json.optJSONObject("info")
            val detected = info?.optString("detectedSource")
                ?.ifBlank { info.optString("detected") }
                ?.ifBlank { null }
            return LingvaResult(text, detected)
        }
    }
}
