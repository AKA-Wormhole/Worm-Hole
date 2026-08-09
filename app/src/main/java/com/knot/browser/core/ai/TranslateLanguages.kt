package com.knot.browser.core.ai

/**
 * Languages offered in the Translate target-language picker. A fixed
 * curated list rather than the full ISO-639 set -- Gemini can translate
 * into essentially anything if asked in the prompt, but a shorter list
 * keeps the picker usable on a phone screen.
 */
data class TranslateLanguage(val code: String, val displayName: String)

object TranslateLanguages {
    val ALL: List<TranslateLanguage> = listOf(
        TranslateLanguage("en", "English"),
        TranslateLanguage("es", "Spanish"),
        TranslateLanguage("fr", "French"),
        TranslateLanguage("de", "German"),
        TranslateLanguage("it", "Italian"),
        TranslateLanguage("pt", "Portuguese"),
        TranslateLanguage("nl", "Dutch"),
        TranslateLanguage("ru", "Russian"),
        TranslateLanguage("tr", "Turkish"),
        TranslateLanguage("ar", "Arabic"),
        TranslateLanguage("hi", "Hindi"),
        TranslateLanguage("ur", "Urdu"),
        TranslateLanguage("bn", "Bengali"),
        TranslateLanguage("zh", "Chinese (Simplified)"),
        TranslateLanguage("ja", "Japanese"),
        TranslateLanguage("ko", "Korean"),
        TranslateLanguage("vi", "Vietnamese"),
        TranslateLanguage("id", "Indonesian"),
        TranslateLanguage("pl", "Polish"),
        TranslateLanguage("sv", "Swedish"),
    )
}
