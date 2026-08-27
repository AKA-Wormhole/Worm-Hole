package com.wormhole.browser.core.translate

/**
 * In-memory LRU cache for translated strings so the same page (or a later
 * visit) does not hit Argos / Lingva / LibreTranslate again for identical text.
 */
object TranslationCache {
    private const val MAX_ENTRIES = 1200
    private val lock = Any()
    private val map = object : LinkedHashMap<String, String>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun key(source: String, target: String, text: String): String {
        return "${source.lowercase()}|${target.lowercase()}|$text"
    }

    fun get(source: String, target: String, text: String): String? {
        synchronized(lock) {
            return map[key(source, target, text)]
        }
    }

    fun put(source: String, target: String, text: String, translated: String) {
        if (text.isBlank() || translated.isBlank()) return
        synchronized(lock) {
            map[key(source, target, text)] = translated
        }
    }

    fun getAll(source: String, target: String, texts: List<String>): Pair<List<String?>, List<Int>> {
        val hits = ArrayList<String?>(texts.size)
        val missing = ArrayList<Int>()
        texts.forEachIndexed { index, text ->
            val cached = get(source, target, text)
            hits.add(cached)
            if (cached == null) missing.add(index)
        }
        return hits to missing
    }
}
