package com.wormhole.browser.core.ai

data class TranslateLanguage(val code: String, val displayName: String)

object TranslateLanguages {
    fun displayName(code: String): String {
        val normalized = code.lowercase().substringBefore('-')
        return ALL.firstOrNull { it.code == normalized }?.displayName
            ?: EXTRA_NAMES[normalized]
            ?: code.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun fromCode(code: String): TranslateLanguage? {
        val normalized = code.lowercase().substringBefore('-')
        return ALL.firstOrNull { it.code == normalized }
    }

    val ENGLISH: TranslateLanguage = TranslateLanguage("en", "English")

    private val EXTRA_NAMES = mapOf(
        "fa" to "Persian",
        "uk" to "Ukrainian",
        "ro" to "Romanian",
        "cs" to "Czech",
        "hu" to "Hungarian",
        "el" to "Greek",
        "he" to "Hebrew",
        "th" to "Thai",
        "ms" to "Malay",
        "fi" to "Finnish",
        "no" to "Norwegian",
        "da" to "Danish",
        "ta" to "Tamil",
        "te" to "Telugu",
        "mr" to "Marathi",
        "gu" to "Gujarati",
        "kn" to "Kannada",
        "ml" to "Malayalam",
        "pa" to "Punjabi",
    )

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
