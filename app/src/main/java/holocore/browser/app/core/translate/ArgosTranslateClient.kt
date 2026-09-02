package holocore.browser.app.core.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * LibreTranslate-compatible client. Those servers run Argos Translate models,
 * not Google and not an LLM.
 */
class ArgosTranslateClient(
    private val httpClient: OkHttpClient = defaultHttpClient,
    private val hosts: List<String> = DEFAULT_HOSTS,
) {

    data class Detection(val code: String, val confident: Boolean)

    suspend fun detect(sample: String): Detection? {
        val trimmed = sample.trim().take(400)
        if (trimmed.isBlank()) return null
        val payload = JSONObject().put("q", trimmed)
        val body = postFirstSuccessful("/detect", payload) ?: return null
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return null
        val first = array.optJSONObject(0) ?: return null
        val code = first.optString("language").lowercase().substringBefore('-')
        if (code.isBlank() || code == "und") return null
        val confidence = first.optDouble("confidence", 0.0)
        return Detection(code, confident = confidence >= 50.0)
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
        val pending = missing.map { texts[it] }
        val fetched = ArrayList<String>(pending.size)
        pending.chunked(BATCH).forEach { chunk ->
            val translated = translateChunk(chunk, source, target) ?: return@withContext null
            fetched.addAll(translated)
        }
        if (fetched.size != pending.size) return@withContext null
        val out = hits.toMutableList()
        missing.forEachIndexed { i, textIndex ->
            TranslationCache.put(source, target, texts[textIndex], fetched[i])
            out[textIndex] = fetched[i]
        }
        out.map { it.orEmpty() }
    }

    private fun translateChunk(chunk: List<String>, source: String, target: String): List<String>? {
        if (chunk.size == 1) {
            return listOf(translateOne(chunk[0], source, target) ?: return null)
        }
        val array = JSONArray().also { chunk.forEach(it::put) }
        val payload = JSONObject()
            .put("q", array)
            .put("source", source)
            .put("target", target)
            .put("format", "text")
        val body = postFirstSuccessful("/translate", payload)
        parseTranslatedList(body, chunk.size)?.let { return it }

        val joined = chunk.joinToString("\n")
        val combined = translateOne(joined, source, target) ?: return null
        val lines = combined.split('\n')
        return chunk.mapIndexed { index, original ->
            lines.getOrNull(index)?.ifBlank { original } ?: original
        }
    }

    private fun translateOne(text: String, source: String, target: String): String? {
        val payload = JSONObject()
            .put("q", text)
            .put("source", source)
            .put("target", target)
            .put("format", "text")
        val body = postFirstSuccessful("/translate", payload) ?: return null
        return parseTranslatedText(body)
    }

    private fun postFirstSuccessful(path: String, payload: JSONObject): String? {
        hosts.forEach { host ->
            val url = host.trimEnd('/') + path
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .post(payload.toString().toRequestBody(JSON))
                .build()
            val body = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
            }.getOrNull()
            if (!body.isNullOrBlank() && !body.trimStart().startsWith("<")) {
                return body
            }
        }
        return null
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36 HoloCore/1.9"
        private const val BATCH = 8
        val ARGOS_HOSTS = listOf(
            "https://translate.argosopentech.com",
        )
        val LIBRETRANSLATE_HOSTS = listOf(
            "https://translate.fedilab.app",
            "https://translate.cutie.dating",
            "https://translate.terraprint.co",
            "https://libretranslate.com",
        )
        val DEFAULT_HOSTS = ARGOS_HOSTS
        private val defaultHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun parseTranslatedText(raw: String): String? {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val text = json.optString("translatedText")
            return text.takeIf { it.isNotBlank() }
        }

        fun parseTranslatedList(raw: String?, expected: Int): List<String>? {
            if (raw.isNullOrBlank()) return null
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val value = json.opt("translatedText") ?: return null
            if (value is JSONArray) {
                if (value.length() != expected) return null
                return List(value.length()) { value.optString(it) }
            }
            if (value is String && expected == 1 && value.isNotBlank()) {
                return listOf(value)
            }
            return null
        }
    }
}
