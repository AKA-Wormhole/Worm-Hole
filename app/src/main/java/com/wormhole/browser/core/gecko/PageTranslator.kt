package com.wormhole.browser.core.gecko

import com.wormhole.browser.core.ai.TranslateLanguage
import com.wormhole.browser.core.ai.TranslateLanguages
import com.wormhole.browser.core.translate.ArgosTranslateClient
import com.wormhole.browser.core.translate.LingvaTranslateClient
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession

/**
 * In-page translation: Argos first, Lingva second, LibreTranslate fallback.
 * Results are cached in memory. No LLM.
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

    suspend fun detectLanguage(session: GeckoSession): Detection? {
        val raw = GeckoExtensionBridge.send(session, "detect_language", emptyMap())
        if (raw != GeckoJs.UNAVAILABLE_SENTINEL && !raw.startsWith("ERR:")) {
            val json = runCatching {
                val start = raw.indexOf('{')
                val end = raw.lastIndexOf('}')
                JSONObject(if (start >= 0 && end > start) raw.substring(start, end + 1) else raw)
            }.getOrNull()
            if (json != null) {
                val code = json.optString("code").lowercase().substringBefore('-').ifBlank { "und" }
                if (code != "und" && code != "en") {
                    return Detection(code, TranslateLanguages.displayName(code), json.optBoolean("confident", true))
                }
            }
        }
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
        var raw = GeckoExtensionBridge.send(session, "collect_text_nodes", mapOf("limit" to "80"))
        if (raw == GeckoJs.UNAVAILABLE_SENTINEL || raw.startsWith("ERR:")) {
            kotlinx.coroutines.delay(350)
            raw = GeckoExtensionBridge.send(session, "collect_text_nodes", mapOf("limit" to "80"))
        }

        val nodes = if (raw == GeckoJs.UNAVAILABLE_SENTINEL || raw.startsWith("ERR:")) {
            emptyList()
        } else {
            parseNodes(raw)
        }

        val unique = LinkedHashMap<String, MutableList<Int>>()
        nodes.forEach { node ->
            if (shouldTranslate(node.text)) {
                unique.getOrPut(node.text) { mutableListOf() }.add(node.id)
            }
        }

        if (unique.isNotEmpty()) {
            val sources = unique.keys.sortedByDescending { it.length }.take(40)
            val translated = translateTexts(sources, language.code)
            if (translated != null && translated.size == sources.size) {
                val pairs = JSONArray()
                sources.forEachIndexed { index, source ->
                    val text = translated.getOrNull(index) ?: source
                    unique[source]?.forEach { id ->
                        pairs.put(JSONObject().put("id", id).put("text", text))
                    }
                }
                val applied = GeckoExtensionBridge.send(
                    session,
                    "apply_translations",
                    JSONObject().put("pairs", pairs),
                )
                if (!applied.startsWith("ERR:") && applied != GeckoJs.UNAVAILABLE_SENTINEL && !applied.startsWith("APPLIED:0")) {
                    return Result.Applied(language.displayName, Mode.IN_PAGE)
                }
            }
        }

        val article = readReadableText(session).trim()
        if (article.length >= 40) {
            val chunks = article.chunked(900).take(8)
            val translatedChunks = translateTexts(chunks, language.code)
            if (translatedChunks != null && translatedChunks.size == chunks.size) {
                val full = translatedChunks.joinToString("")
                val applied = GeckoExtensionBridge.send(
                    session,
                    "apply_full_text",
                    mapOf("text" to full.take(16000)),
                )
                if (applied.contains("APPLIED")) {
                    return Result.Applied(language.displayName, Mode.IN_PAGE)
                }
            }
        }

        val viewer = viewerUrl(pageUrl, language.code)
        if (viewer != null && onOpenViewer != null) {
            onOpenViewer(viewer)
            return Result.Applied(language.displayName, Mode.VIEWER)
        }

        val reason = raw.removePrefix("ERR:")
        val message = when {
            raw == GeckoJs.UNAVAILABLE_SENTINEL || reason == "BRIDGE_PORT_NOT_READY" ->
                "Couldn't reach this page. Open a finished https page and try again."
            unique.isEmpty() -> "There's no page content to translate yet."
            else -> "Couldn't rewrite this page. Check your connection and try again."
        }
        return Result.Error(message)
    }

    private fun viewerUrl(pageUrl: String, targetCode: String): String? {
        val url = pageUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        val target = targetCode.lowercase().substringBefore('-').ifBlank { "en" }
        val encoded = java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8)
        return "https://www.translatetheweb.com/?from=&to=$target&a=$encoded"
    }

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
        val result = GeckoExtensionBridge.send(session, "restore_originals", emptyMap())
        return result.startsWith("RESTORED")
    }

    suspend fun readReadableText(session: GeckoSession): String {
        repeat(3) { attempt ->
            val viaBridge = GeckoExtensionBridge.send(session, "read_page")
            if (viaBridge != GeckoJs.UNAVAILABLE_SENTINEL &&
                !viaBridge.startsWith("ERR:") &&
                viaBridge.isNotBlank()
            ) {
                return viaBridge.trim()
            }
            kotlinx.coroutines.delay(250L * (attempt + 1))
        }
        val viaJs = GeckoJs.evaluate(
            session,
            "(function(){try{" +
                "var t=(document.body&&document.body.innerText)||'';" +
                "return String(t).slice(0,16000);" +
                "}catch(e){return ''}})()",
        )
        if (viaJs == GeckoJs.UNAVAILABLE_SENTINEL || viaJs.startsWith("ERR:")) return ""
        return viaJs.trim()
    }

    private fun shouldTranslate(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2) return false
        if (trimmed.all { it.isDigit() || it.isWhitespace() || it in ".,:;/%+-#$€£¥" }) return false
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("www.")) return false
        return true
    }

    private fun parseNodes(raw: String): List<TextNode> {
        val json = raw.trim()
            .removeSurrounding("\"")
            .replace("\\\"", "\"")
            .let { text ->
            val start = text.indexOf('[')
            val end = text.lastIndexOf(']')
            if (start >= 0 && end > start) text.substring(start, end + 1) else text
        }
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optInt("id", i)
                    val text = obj.optString("text")
                    if (text.isNotBlank()) add(TextNode(id, text))
                }
            }
        }.getOrDefault(emptyList())
    }

    private data class TextNode(val id: Int, val text: String)
}
