package com.wormhole.browser.core.gecko

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.TranslationsController
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firefox / Fenix page translation: GeckoView [TranslationsController].
 * The engine rewrites the live page after downloading language models.
 */
object FirefoxPageTranslations {

    private val detectedFrom = ConcurrentHashMap<GeckoSession, String>()

    fun attach(session: GeckoSession) {
        session.setTranslationsSessionDelegate(
            object : TranslationsController.SessionTranslation.Delegate {
                override fun onTranslationStateChange(
                    session: GeckoSession,
                    translationState: TranslationsController.SessionTranslation.TranslationState?,
                ) {
                    val tag = translationState?.detectedLanguages?.docLangTag
                    if (!tag.isNullOrBlank()) detectedFrom[session] = tag
                }
            },
        )
    }

    fun detach(session: GeckoSession) {
        detectedFrom.remove(session)
        runCatching { session.setTranslationsSessionDelegate(null) }
    }

    fun detectedLanguage(session: GeckoSession): String? = detectedFrom[session]

    suspend fun translate(session: GeckoSession, fromLanguage: String, toLanguage: String) {
        val coordinator = session.sessionTranslation
            ?: throw IllegalStateException("Gecko translations are not available")
        val options = TranslationsController.SessionTranslation.TranslationOptions.Builder()
            .downloadModel(true)
            .build()
        coordinator.translate(fromLanguage, toLanguage, options).await()
    }

    suspend fun restore(session: GeckoSession) {
        val coordinator = session.sessionTranslation
            ?: throw IllegalStateException("Gecko translations are not available")
        coordinator.restoreOriginalPage().await()
    }

    private suspend fun <T> GeckoResult<T>.await(): T? =
        suspendCancellableCoroutine { cont ->
            accept(
                { value -> if (cont.isActive) cont.resume(value) },
                { error ->
                    if (cont.isActive) {
                        cont.resumeWithException(error ?: IllegalStateException("Translation failed"))
                    }
                },
            )
        }
}
