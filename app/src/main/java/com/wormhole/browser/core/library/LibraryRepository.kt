package com.wormhole.browser.core.library

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.legacyLibraryDataStore by preferencesDataStore(name = "wormhole_library")

@Serializable
data class LibraryEntry(val title: String, val url: String, val createdAt: Long)

@Serializable
data class ShortcutEntry(val title: String, val url: String, val createdAt: Long)

data class OmniboxSuggestion(val title: String, val url: String, val isBookmark: Boolean)

data class SearchQuerySuggestion(val text: String)

class LibraryRepository(private val context: Context) {
    private val dao = LibraryDatabase.get(context).dao()
    private val json = Json { ignoreUnknownKeys = true }
    private val migrationMutex = Mutex()

    val bookmarks: Flow<List<LibraryEntry>> = dao.bookmarks().map { rows ->
        rows.map { LibraryEntry(it.title, it.url, it.createdAt) }
    }.onStart { migrateLegacyIfNeeded() }

    val history: Flow<List<LibraryEntry>> = dao.history().map { rows ->

        rows.distinctBy { it.url }.map { LibraryEntry(it.title, it.url, it.visitedAt) }
    }.onStart { migrateLegacyIfNeeded() }

    val shortcuts: Flow<List<ShortcutEntry>> = dao.shortcuts().map { rows ->
        rows.map { ShortcutEntry(it.title, it.url, it.createdAt) }
    }.onStart { migrateLegacyIfNeeded() }

    val recentSearches: Flow<List<String>> = context.legacyLibraryDataStore.data.map { prefs ->
        decodeStringList(prefs[RECENT_SEARCHES_KEY])
    }

    val hasStoredRecentSearches: Flow<Boolean> = context.legacyLibraryDataStore.data.map { prefs ->
        RECENT_SEARCHES_KEY in prefs
    }

    suspend fun recordSearchQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || trimmed.length > 200) return
        context.legacyLibraryDataStore.edit { prefs ->
            val current = decodeStringList(prefs[RECENT_SEARCHES_KEY])
            prefs[RECENT_SEARCHES_KEY] = json.encodeToString(
                ListSerializer(String.serializer()),
                (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) }).take(12),
            )
        }
    }

    suspend fun clearRecentSearches() {
        context.legacyLibraryDataStore.edit { prefs ->
            prefs[RECENT_SEARCHES_KEY] = "[]"
        }
    }

    suspend fun addBookmark(entry: LibraryEntry) { migrateLegacyIfNeeded(); dao.insertBookmark(BookmarkEntity(title = entry.title, url = entry.url, createdAt = entry.createdAt)) }
    suspend fun removeBookmark(url: String) { migrateLegacyIfNeeded(); dao.deleteBookmark(url) }
    suspend fun addShortcut(entry: ShortcutEntry) { migrateLegacyIfNeeded(); dao.insertShortcut(ShortcutEntity(title = entry.title, url = entry.url, createdAt = entry.createdAt)) }
    suspend fun removeShortcut(url: String) { migrateLegacyIfNeeded(); dao.deleteShortcut(url) }
    suspend fun addHistory(entry: LibraryEntry) { migrateLegacyIfNeeded(); dao.insertHistory(HistoryEntity(title = entry.title, url = entry.url, visitedAt = entry.createdAt)) }
    suspend fun clearHistory() { migrateLegacyIfNeeded(); dao.clearHistory() }

    suspend fun deleteHistoryEntry(url: String) { migrateLegacyIfNeeded(); dao.deleteHistory(url) }

    suspend fun searchOmnibox(query: String, limit: Int = 2): List<OmniboxSuggestion> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()
        migrateLegacyIfNeeded()
        val escaped = escapeLikeWildcards(trimmed)

        val fetchLimit = limit * 3
        val bookmarkMatches = dao.searchBookmarks(escaped, fetchLimit)
            .map { OmniboxSuggestion(title = it.title, url = it.url, isBookmark = true) }
        val bookmarkedUrls = bookmarkMatches.map { it.url }.toSet()
        val historyMatches = dao.searchHistory(escaped, fetchLimit)
            .filter { it.url !in bookmarkedUrls }
            .map { OmniboxSuggestion(title = it.title, url = it.url, isBookmark = false) }
        return (bookmarkMatches + historyMatches)
            .distinctBy { it.url }
            .take(limit)
    }

    private fun escapeLikeWildcards(raw: String): String =
        raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private suspend fun migrateLegacyIfNeeded() = migrationMutex.withLock {
        val prefs = context.legacyLibraryDataStore.data.first()
        if (prefs[MIGRATED_KEY] == true) return@withLock

        val bookmarks = decodeEntries(prefs[BOOKMARKS_KEY])
        val history = decodeEntries(prefs[HISTORY_KEY])
        val shortcuts = decodeShortcuts(prefs[SHORTCUTS_KEY])

        bookmarks.forEach { dao.insertBookmark(BookmarkEntity(title = it.title, url = it.url, createdAt = it.createdAt)) }
        history.asReversed().forEach { dao.insertHistory(HistoryEntity(title = it.title, url = it.url, visitedAt = it.createdAt)) }
        shortcuts.forEach { dao.insertShortcut(ShortcutEntity(title = it.title, url = it.url, createdAt = it.createdAt)) }

        context.legacyLibraryDataStore.edit { it[MIGRATED_KEY] = true }
    }

    private fun decodeEntries(value: String?): List<LibraryEntry> = runCatching {
        if (value.isNullOrBlank()) emptyList() else json.decodeFromString(ListSerializer(LibraryEntry.serializer()), value)
    }.getOrDefault(emptyList())

    private fun decodeShortcuts(value: String?): List<ShortcutEntry> = runCatching {
        if (value.isNullOrBlank()) emptyList() else json.decodeFromString(ListSerializer(ShortcutEntry.serializer()), value)
    }.getOrDefault(emptyList())

    private fun decodeStringList(value: String?): List<String> = runCatching {
        if (value.isNullOrBlank()) emptyList()
        else json.decodeFromString(ListSerializer(String.serializer()), value)
    }.getOrDefault(emptyList())

    companion object {
        private val MIGRATED_KEY = booleanPreferencesKey("room_migrated")
        private val BOOKMARKS_KEY = stringPreferencesKey("bookmarks")
        private val HISTORY_KEY = stringPreferencesKey("history")
        private val SHORTCUTS_KEY = stringPreferencesKey("shortcuts")
        private val RECENT_SEARCHES_KEY = stringPreferencesKey("recent_searches")
    }
}
