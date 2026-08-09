package com.knot.browser.core.library

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.libraryDataStore by preferencesDataStore(name = "knot_library")

@Serializable
data class LibraryEntry(
    val title: String,
    val url: String,
    val createdAt: Long,
)

class LibraryRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val bookmarks: Flow<List<LibraryEntry>> = context.libraryDataStore.data.map { prefs ->
        decode(prefs[BOOKMARKS_KEY])
    }

    val history: Flow<List<LibraryEntry>> = context.libraryDataStore.data.map { prefs ->
        decode(prefs[HISTORY_KEY])
    }

    suspend fun addBookmark(entry: LibraryEntry) {
        context.libraryDataStore.edit { prefs ->
            val current = decode(prefs[BOOKMARKS_KEY])
            if (current.none { it.url == entry.url }) {
                prefs[BOOKMARKS_KEY] = json.encodeToString(
                    ListSerializer(LibraryEntry.serializer()),
                    (listOf(entry) + current).take(MAX_BOOKMARKS)
                )
            }
        }
    }

    suspend fun removeBookmark(url: String) {
        context.libraryDataStore.edit { prefs ->
            prefs[BOOKMARKS_KEY] = json.encodeToString(
                ListSerializer(LibraryEntry.serializer()),
                decode(prefs[BOOKMARKS_KEY]).filterNot { it.url == url }
            )
        }
    }

    suspend fun addHistory(entry: LibraryEntry) {
        context.libraryDataStore.edit { prefs ->
            val current = decode(prefs[HISTORY_KEY]).filterNot { it.url == entry.url }
            prefs[HISTORY_KEY] = json.encodeToString(
                ListSerializer(LibraryEntry.serializer()),
                (listOf(entry) + current).take(MAX_HISTORY)
            )
        }
    }

    suspend fun clearHistory() {
        context.libraryDataStore.edit { it.remove(HISTORY_KEY) }
    }

    private fun decode(value: String?): List<LibraryEntry> = try {
        if (value.isNullOrBlank()) {
            emptyList()
        } else {
            json.decodeFromString(ListSerializer(LibraryEntry.serializer()), value)
        }
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        private const val MAX_BOOKMARKS = 200
        private const val MAX_HISTORY = 500
        private val BOOKMARKS_KEY = stringPreferencesKey("bookmarks")
        private val HISTORY_KEY = stringPreferencesKey("history")
    }
}
