package com.wormhole.browser.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wormhole.browser.core.security.EncryptedKeyStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.settingsDataStore by preferencesDataStore(name = "wormhole_settings")

class SettingsRepository(private val context: Context) {

    private val encryptedKeyStore = EncryptedKeyStore(context)
    private val geminiMigrationMutex = Mutex()

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

    val geminiApiKey: Flow<String> =
        encryptedKeyStore.geminiApiKey.onStart { migrateLegacyGeminiKeyIfNeeded() }

    suspend fun setGeminiApiKey(key: String) {
        encryptedKeyStore.setGeminiApiKey(key)

        context.settingsDataStore.edit { prefs -> prefs.remove(GEMINI_API_KEY_KEY) }
    }

    private suspend fun migrateLegacyGeminiKeyIfNeeded() = geminiMigrationMutex.withLock {
        val legacyKey = context.settingsDataStore.data.first()[GEMINI_API_KEY_KEY]
        if (legacyKey.isNullOrEmpty()) return@withLock

        if (encryptedKeyStore.getGeminiApiKeyBlocking().isEmpty()) {
            encryptedKeyStore.setGeminiApiKey(legacyKey)
        }
        context.settingsDataStore.edit { prefs -> prefs.remove(GEMINI_API_KEY_KEY) }
    }

    val onboardingCompleted: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[ONBOARDING_COMPLETED_KEY] ?: false
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    val dynamicColorEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[DYNAMIC_COLOR_KEY] ?: false
        }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[DYNAMIC_COLOR_KEY] = enabled
        }
    }

    val dynamicBackgroundEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[DYNAMIC_BACKGROUND_KEY] ?: true
        }

    suspend fun setDynamicBackgroundEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[DYNAMIC_BACKGROUND_KEY] = enabled
        }
    }

    val trackerBlockingEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[TRACKER_BLOCKING_KEY] ?: true
        }

    suspend fun setTrackerBlockingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[TRACKER_BLOCKING_KEY] = enabled
        }
    }

    val adBlockingEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[AD_BLOCKING_KEY] ?: true
        }

    suspend fun setAdBlockingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[AD_BLOCKING_KEY] = enabled
        }
    }

    val popupBlockingEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[POPUP_BLOCKING_KEY] ?: true
        }

    suspend fun setPopupBlockingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[POPUP_BLOCKING_KEY] = enabled
        }
    }

    val webDarkModeEnabled: Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[WEB_DARK_MODE_KEY] ?: true
        }

    suspend fun setWebDarkModeEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[WEB_DARK_MODE_KEY] = enabled
        }
    }

    val homepageUrl: Flow<String> =
        context.settingsDataStore.data.map { prefs ->
            prefs[HOMEPAGE_URL_KEY].orEmpty()
        }

    suspend fun setHomepageUrl(url: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[HOMEPAGE_URL_KEY] = url.trim()
        }
    }

    companion object {
        private val WEB_DARK_MODE_KEY = booleanPreferencesKey("web_dark_mode_enabled")
        private val HOMEPAGE_URL_KEY = stringPreferencesKey("homepage_url")
        private val SEARCH_ENGINE_KEY = stringPreferencesKey("search_engine")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val GEMINI_API_KEY_KEY = stringPreferencesKey("gemini_api_key")
        private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")
        private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color_enabled")
        private val DYNAMIC_BACKGROUND_KEY = booleanPreferencesKey("dynamic_background_enabled")
        private val TRACKER_BLOCKING_KEY = booleanPreferencesKey("tracker_blocking_enabled")
        private val AD_BLOCKING_KEY = booleanPreferencesKey("ad_blocking_enabled")
        private val POPUP_BLOCKING_KEY = booleanPreferencesKey("popup_blocking_enabled")
    }
}
