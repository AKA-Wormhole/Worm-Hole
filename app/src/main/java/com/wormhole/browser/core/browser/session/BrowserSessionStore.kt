package com.wormhole.browser.core.browser.session

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.sessionDataStore by preferencesDataStore("wormhole_session")

@Serializable
data class PersistedTab(
    val id: String,
    val title: String,
    val url: String,
    val displayUrl: String,
    val faviconUrl: String? = null,
    val spaceId: String,
    val createdAtMillis: Long,
    val isBlankTab: Boolean,
    val sortOrder: Int,
    val isIncognito: Boolean = false,
)

@Serializable
data class PersistedSpace(
    val id: String,
    val name: String,
    val accent: String,
    val order: Int,
)

@Serializable
data class PersistedSession(
    val tabs: List<PersistedTab>,
    val activeTabId: String?,
    val activeSpaceId: String,
    val spaces: List<PersistedSpace> = emptyList(),
)

class BrowserSessionStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    suspend fun save(session: PersistedSession) {
        context.sessionDataStore.edit { it[SESSION_KEY] = json.encodeToString(PersistedSession.serializer(), session) }
    }
    suspend fun load(): PersistedSession? {
        val raw = context.sessionDataStore.data.first()[SESSION_KEY] ?: return null
        return runCatching { json.decodeFromString(PersistedSession.serializer(), raw) }.getOrNull()
    }
    companion object { private val SESSION_KEY = stringPreferencesKey("session") }
}
