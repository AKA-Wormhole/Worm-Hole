package com.knot.browser.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "knot_settings")

/**
 * Single source of truth for user-configurable settings that need to
 * survive app restarts. Backed by Jetpack DataStore (not SharedPreferences
 * -- DataStore is the current recommended approach, async and
 * Flow-friendly, which matches how the rest of Knot is built).
 *
 * Holds the search engine choice, the appearance (theme) choice, and the
 * user-supplied Gemini API key used by the Assistant/Translate features.
 */
class SettingsRepository(private val context: Context) {

    val searchEngine: Flow<SearchEngine> =
        context.settingsDataStore.data.map { prefs ->
            SearchEngine.fromId(prefs[SEARCH_ENGINE_KEY])
        }

    suspend fun setSearchEngine(engine: SearchEngine) {
        context.settingsDataStore.edit { prefs ->
            prefs[SEARCH_ENGINE_KEY] = engine.id
        }
    }

    val themeMode: Flow<ThemeMode> =
        context.settingsDataStore.data.map { prefs ->
            ThemeMode.fromId(prefs[THEME_MODE_KEY])
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.id
        }
    }

    /** Empty string means "no key set" -- callers should treat blank the
     * same as null rather than attempting a network call with it. */
    val geminiApiKey: Flow<String> =
        context.settingsDataStore.data.map { prefs ->
            prefs[GEMINI_API_KEY_KEY].orEmpty()
        }

    suspend fun setGeminiApiKey(key: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[GEMINI_API_KEY_KEY] = key
        }
    }

    companion object {
        private val SEARCH_ENGINE_KEY = stringPreferencesKey("search_engine")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val GEMINI_API_KEY_KEY = stringPreferencesKey("gemini_api_key")
    }
}
