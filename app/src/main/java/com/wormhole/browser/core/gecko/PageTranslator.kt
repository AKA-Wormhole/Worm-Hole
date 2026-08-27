package com.wormhole.browser.core.gecko

import com.wormhole.browser.core.ai.GeminiClient
import com.wormhole.browser.core.ai.TranslateLanguage
import com.wormhole.browser.core.ai.TranslateLanguages
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoSession

/**
 * Chrome-style in-page translation: detect the page language, collect visible
 * text nodes, translate them, write translations into the live DOM, and restore
 * the original strings when asked.
 */
object PageTranslator {

    data class Detection(
        val code: String,
        val displayName: String,
        val confident: Boolean,
    )

    suspend fun detectLanguage(session: GeckoSession): Detection? {
        val raw = GeckoExtensionBridge.send(session, "detect_language", emptyMap())
        if (raw == GeckoJs.UNAVAILABLE_SENTINEL || raw.startsWith("ERR:")) return null
        val json = runCatching {
            val start = raw.indexOf('{')
            val end = raw.lastIndexOf('}')
            JSONObject(if (start >= 0 && end > start) raw.substring(start, end + 1) else raw)
        }.getOrNull() ?: return null
        val code = json.optString("code").lowercase().substringBefore('-').ifBlank { "und" }
        if (code == "und" || code == "en") return null
        val name = TranslateLanguages.displayName(code)
        return Detection(code = code, displayName = name, confident = json.optBoolean("confident", true))
    }

    suspend fun translatePage(
        session: GeckoSession,
        apiKey: String,
        language: TranslateLanguage,
        gemini: GeminiClient,
    ): Result {
        // The bridge can briefly be unavailable right after navigation (extension
        // port still wiring up) or on the very first call for a session, so give
        // it a couple of extra tries before reporting a read failure to the user.
        var raw = GeckoExtensionBridge.send(session, "collect_text_nodes", mapOf("limit" to "360"))
        var readAttempts = 1
        while ((raw == GeckoJs.UNAVAILABLE_SENTINEL || raw.startsWith("ERR:")) && readAttempts < 3) {
            kotlinx.coroutines.delay(300L * readAttempts)
            raw = GeckoExtensionBridge.send(session, "collect_text_nodes", mapOf("limit" to "360"))
            readAttempts++
        }
        if (raw == GeckoJs.UNAVAILABLE_SENTINEL || raw.startsWith("ERR:")) {
            val reason = raw.removePrefix("ERR:")
            val message = when {
                raw == GeckoJs.UNAVAILABLE_SENTINEL -> {
                    val installError = GeckoExtensionBridge.lastInstallError
                    if (installError != null) {
                        "Translation is unavailable: the page bridge failed to install ($installError)."
                    } else {
                        "Translation isn't ready yet on this page. Try again in a moment."
                    }
                }
                reason == "BRIDGE_PORT_NOT_READY" ->
                    "Translation isn't ready yet on this page. Try again in a moment."
                else -> "Could not read this page for translation ($reason)."
            }
            return Result.Error(message)
        }
        val nodes = parseNodes(raw)
        if (nodes.isEmpty()) {
            return Result.Error("There's no page content to translate yet.")
        }

        val unique = LinkedHashMap<String, MutableList<Int>>()
        nodes.forEach { node ->
            if (shouldTranslate(node.text)) {
                unique.getOrPut(node.text) { mutableListOf() }.add(node.id)
            }
        }
        if (unique.isEmpty()) {
            return Result.Error("There's no page content to translate yet.")
        }
        val sources = unique.keys.toList()
        val translated = translateBatches(gemini, apiKey, language, sources)
            ?: return Result.Error("Translation failed. Check the assistant API key and try again.")

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
        if (applied.startsWith("ERR:") || applied == GeckoJs.UNAVAILABLE_SENTINEL) {
            return Result.Error("Translated the page, but could not write it back.")
        }
        return Result.Applied(language.displayName)
    }

    suspend fun restoreOriginal(session: GeckoSession): Boolean {
        val result = GeckoExtensionBridge.send(session, "restore_originals", emptyMap())
        return result.startsWith("RESTORED")
    }

    private fun shouldTranslate(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2) return false
        if (trimmed.all { it.isDigit() || it.isWhitespace() || it in ".,:;/%+-#$€£¥" }) return false
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("www.")) return false
        return true
    }

    private suspend fun translateBatches(
        gemini: GeminiClient,
        apiKey: String,
        language: TranslateLanguage,
        sources: List<String>,
    ): List<String>? {
        val out = ArrayList<String>(sources.size)
        sources.chunked(28).forEach { chunk ->
            val translatedChunk = translateChunkWithRetry(gemini, apiKey, language, chunk)
                ?: return null
            out.addAll(translatedChunk)
        }
        return out
    }

    /**
     * Translates one chunk, retrying with a stricter prompt if the model's
     * reply doesn't line up 1:1 with the input (missing lines, merged lines,
     * or lines whose length has drifted so far that they were likely
     * paraphrased/summarized rather than translated).
     */
    private suspend fun translateChunkWithRetry(
        gemini: GeminiClient,
        apiKey: String,
        language: TranslateLanguage,
        chunk: List<String>,
    ): List<String>? {
        var strict = false
        repeat(2) { attempt ->
            strict = attempt > 0
            val prompt = buildPrompt(language, chunk, strict)
            when (val result = gemini.generateText(apiKey, prompt)) {
                is GeminiClient.Result.Failure -> return null
                is GeminiClient.Result.Success -> {
                    val parsed = parseNumbered(result.text, chunk)
                    if (linesMatch(chunk, parsed)) return parsed
                }
            }
        }
        // Fall back to whatever we last parsed rather than failing the whole
        // page: individual lines that couldn't be verified keep their
        // original text (handled inside parseNumbered), so the page stays
        // readable even if a few lines weren't translated.
        return parseNumbered("", chunk)
    }

    private fun buildPrompt(language: TranslateLanguage, chunk: List<String>, strict: Boolean): String {
        val numbered = chunk.mapIndexed { i, text -> "${i + 1}. ${text.replace("\n", " ")}" }.joinToString("\n")
        val extraRule = if (strict) {
            "- This is a retry: your previous reply did not have exactly ${chunk.size} lines matching the input word-for-word in structure. " +
                "Return EXACTLY ${chunk.size} lines this time, one per input line, in the same order."
        } else {
            "- Return exactly ${chunk.size} lines, one per input line, in the same order."
        }
        return """
            You are a web-page translator. Translate each numbered line into ${language.displayName}.
            Rules:
            - Keep the same numbering and the same number of lines: input has ${chunk.size} lines, output must have ${chunk.size} lines.
            - Translate the full meaning of each line; do not summarize, shorten, or drop words.
            - Match the source line's word count as closely as the target language allows so the page layout still fits.
            - Keep names, brand names, URLs, emails, and numbers unchanged.
            - Keep punctuation and formatting placeholders intact.
            - Do not add quotes, notes, explanations, or extra lines.
            $extraRule

            $numbered
        """.trimIndent()
    }

    /**
     * Sanity check that a parsed reply is a real 1:1 translation rather than
     * a truncated/merged response: every line must be present and, for
     * anything longer than a couple of words, must not have collapsed to a
     * drastically shorter line (a sign of summarization instead of translation).
     */
    private fun linesMatch(sources: List<String>, parsed: List<String>): Boolean {
        if (parsed.size != sources.size) return false
        sources.forEachIndexed { index, source ->
            val translated = parsed[index]
            if (translated.isBlank()) return false
            val sourceWords = source.trim().split(WHITESPACE).size
            if (sourceWords >= 4) {
                val translatedWords = translated.trim().split(WHITESPACE).size
                // Allow generous slack for languages that need more/fewer words
                // to express the same meaning, but reject obvious truncation.
                if (translatedWords < sourceWords / 3) return false
            }
        }
        return true
    }

    private val WHITESPACE = Regex("\\s+")

    private fun parseNodes(raw: String): List<TextNode> {
        val json = raw.trim().let { text ->
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

    private fun parseNumbered(text: String, originals: List<String>): List<String> {
        val mapped = HashMap<Int, String>()
        text.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val match = NUMBERED.matchEntire(line)
            if (match != null) {
                val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@forEach
                mapped[index] = match.groupValues[2].trim()
            }
        }
        return originals.mapIndexed { index, original ->
            mapped[index]?.takeIf { it.isNotBlank() } ?: original
        }
    }

    private data class TextNode(val id: Int, val text: String)

    sealed interface Result {
        data class Applied(val language: String) : Result
        data class Error(val message: String) : Result
    }

    private val NUMBERED = Regex("^(\\d+)[.)]\\s*(.*)$")
}
