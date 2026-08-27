package com.wormhole.browser.core.translate

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Talks to Google Translate's public gtx client (the same endpoint Chrome
 * extensions and many offline translators use). No Gemini / LLM involved.
 */
class GoogleTranslateClient(
    private val httpClient: OkHttpClient = defaultHttpClient,
) {

    data class Detection(val code: String, val confident: Boolean)

    suspend fun detect(sample: String): Detection? {
        val trimmed = sample.trim().take(400)
        if (trimmed.isBlank()) return null
        lastDetected = null
        translate(listOf(trimmed), targetCode = "en", sourceCode = "auto") ?: return null
        return lastDetected
    }

    @Volatile
    private var lastDetected: Detection? = null

    suspend fun translate(
        texts: List<String>,
        targetCode: String,
        sourceCode: String = "auto",
    ): List<String>? = withContext(Dispatchers.IO) {
        if (texts.isEmpty()) return@withContext emptyList()
        val target = targetCode.lowercase().substringBefore('-').ifBlank { "en" }
        val source = sourceCode.lowercase().substringBefore('-').ifBlank { "auto" }
        val out = ArrayList<String>(texts.size)
        texts.chunked(BATCH).forEach { chunk ->
            val translated = translateChunk(chunk, source, target) ?: return@withContext null
            out.addAll(translated)
        }
        out
    }

    private fun translateChunk(chunk: List<String>, source: String, target: String): List<String>? {
        // Prefer POST so long pages do not blow the URL limit.
        val form = FormBody.Builder()
            .add("client", "gtx")
            .add("sl", source)
            .add("tl", target)
            .add("dt", "t")
            .add("q", chunk.joinToString("\n"))
            .build()
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(form)
            .build()
        val body = runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
        }.getOrNull()
        if (body.isNullOrBlank()) {
            return chunk.map { line ->
                translateOneGet(line, source, target) ?: return null
            }
        }
        return parseGtx(body, chunk)
    }

    private fun translateOneGet(text: String, source: String, target: String): String? {
        val encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name())
        val url = "$ENDPOINT?client=gtx&sl=$source&tl=$target&dt=t&q=$encoded"
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
        }.getOrNull() ?: return null
        return parseGtx(body, listOf(text))?.firstOrNull()
    }

    private fun parseGtx(raw: String, originals: List<String>): List<String>? {
        val json = raw.trim().removePrefix(")]}'")
        val root = runCatching { JSONArray(json) }.getOrNull() ?: return null
        val sentences = root.optJSONArray(0) ?: return null
        val detected = root.optString(2).lowercase().substringBefore('-')
        if (detected.isNotBlank() && detected != "und") {
            lastDetected = Detection(detected, confident = true)
        }
        val combined = buildString {
            for (i in 0 until sentences.length()) {
                val pair = sentences.optJSONArray(i) ?: continue
                append(pair.optString(0))
            }
        }
        if (originals.size == 1) return listOf(combined.ifBlank { originals[0] })
        val lines = combined.split('\n')
        if (lines.size >= originals.size) {
            return originals.mapIndexed { index, original ->
                lines.getOrNull(index)?.ifBlank { original } ?: original
            }
        }
        // Model collapsed newlines; keep originals for unmatched tails.
        return originals.mapIndexed { index, original ->
            lines.getOrNull(index)?.ifBlank { original } ?: original
        }
    }

    companion object {
        private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"
        private const val BATCH = 18
        private val defaultHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
