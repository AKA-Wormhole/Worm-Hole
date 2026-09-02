package holocore.browser.app.core.gecko

import holocore.browser.app.core.ai.TranslateLanguage
import holocore.browser.app.core.ai.TranslateLanguages
import org.mozilla.geckoview.GeckoSession

/**
 * Page translation the Firefox way: TranslationsController rewrites the
 * current document in Gecko. No page-bridge port and no external viewer.
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

    suspend fun detectLanguage(session: GeckoSession): Detection? {
        val code = FirefoxPageTranslations.detectedLanguage(session)
            ?.lowercase()
            ?.substringBefore('-')
            ?: return null
        if (code == "und" || code == "en") return null
        return Detection(code, TranslateLanguages.displayName(code), true)
    }

    suspend fun translatePage(
        session: GeckoSession,
        language: TranslateLanguage,
        pageUrl: String = "",
        onOpenViewer: ((String) -> Unit)? = null,
    ): Result {
        if (!pageUrl.startsWith("http://") && !pageUrl.startsWith("https://")) {
            return Result.Error("Open a finished https page to translate.")
        }
        val to = language.code.lowercase().substringBefore('-').ifBlank { "en" }
        var from = FirefoxPageTranslations.detectedLanguage(session)
            ?.lowercase()
            ?.substringBefore('-')
        if (from.isNullOrBlank() || from == "und") {
            repeat(8) {
                kotlinx.coroutines.delay(150)
                from = FirefoxPageTranslations.detectedLanguage(session)
                    ?.lowercase()
                    ?.substringBefore('-')
                if (!from.isNullOrBlank() && from != "und") return@repeat
            }
        }
        if (from.isNullOrBlank() || from == "und") from = if (to == "en") "es" else "en"
        if (from == to) {
            return Result.Error("This page is already in ${language.displayName}.")
        }
        return try {
            FirefoxPageTranslations.translate(session, from!!, to)
            Result.Applied(language.displayName, Mode.IN_PAGE)
        } catch (e: Throwable) {
            val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
            Result.Error("Couldn't translate this page. $detail")
        }
    }

    suspend fun restoreOriginal(session: GeckoSession, pageUrl: String = ""): Boolean {
        return try {
            FirefoxPageTranslations.restore(session)
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
}
