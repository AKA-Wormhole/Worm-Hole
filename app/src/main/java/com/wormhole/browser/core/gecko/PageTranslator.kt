package com.wormhole.browser.core.gecko

import com.wormhole.browser.core.ai.TranslateLanguage
import com.wormhole.browser.core.ai.TranslateLanguages
import com.wormhole.browser.core.translate.ArgosTranslateClient
import com.wormhole.browser.core.translate.LingvaTranslateClient
import org.mozilla.geckoview.GeckoSession

/**
 * In-page translation without the native page-bridge port.
 *
 * The app only changes the page hash (`#wh-tl=hi`). The bundled content
 * script sees that hash, asks the extension background to translate the
 * visible text, and rewrites the same page. No Microsoft viewer, no
 * connectNative round-trip.
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

    private val argos = ArgosTranslateClient(hosts = ArgosTranslateClient.ARGOS_HOSTS)
    private val libre = ArgosTranslateClient(hosts = ArgosTranslateClient.LIBRETRANSLATE_HOSTS)
    private val lingva = LingvaTranslateClient()

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
        val url = pageUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Result.Error("Open a finished https page to translate.")
        }
        val marked = translateMarkerUrl(url, language.code)
        if (onOpenViewer != null) {
            onOpenViewer(marked)
        } else {
            session.loadUri(marked)
        }
        return Result.Applied(language.displayName, Mode.IN_PAGE)
    }

    suspend fun restoreOriginal(session: GeckoSession, pageUrl: String = ""): Boolean {
        return try {
            session.loadUri(restoreMarkerUrl(pageUrl))
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun translateMarkerUrl(pageUrl: String, languageCode: String): String {
        val base = pageUrl.substringBefore("#")
        val lang = languageCode.lowercase().substringBefore('-').ifBlank { "en" }
        return "$base#wh-tl=$lang"
    }

    fun restoreMarkerUrl(pageUrl: String = ""): String {
        val base = pageUrl.substringBefore("#")
        return if (base.startsWith("http")) "$base#wh-tl-restore" else "javascript:void(location.hash='wh-tl-restore')"
    }

    suspend fun readReadableText(session: GeckoSession): String {
        val viaJs = GeckoJs.evaluate(
            session,
            "(function(){try{var t=(document.body&&document.body.innerText)||'';return String(t).slice(0,16000);}catch(e){return ''}})()",
        )
        if (viaJs == GeckoJs.UNAVAILABLE_SENTINEL || viaJs.startsWith("ERR:")) return ""
        return viaJs.trim()
    }

    private suspend fun detectSample(sample: String): Pair<String, Boolean>? {
        argos.detect(sample)?.let { return it.code to it.confident }
        lingva.detect(sample)?.let { return it.code to it.confident }
        libre.detect(sample)?.let { return it.code to it.confident }
        return null
    }
}
