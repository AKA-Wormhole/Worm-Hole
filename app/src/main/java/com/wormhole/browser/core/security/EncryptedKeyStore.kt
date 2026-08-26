package com.wormhole.browser.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.security.GeneralSecurityException

/**
 * Hardware-backed encrypted storage for the Gemini API key.
 * Passkeys stay in the platform Credential Manager — never written here.
 */
class EncryptedKeyStore(context: Context) {

    private val appContext = context.applicationContext

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = createPrefsRecoveringFromInvalidKey()

    private fun createPrefsRecoveringFromInvalidKey(): SharedPreferences = try {
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        val recoverable = e is KeyPermanentlyInvalidatedException ||
            e is GeneralSecurityException ||
            e.cause is KeyPermanentlyInvalidatedException
        if (!recoverable) throw e

        Log.w(TAG, "Secure prefs undecryptable — resetting encrypted file (API key lost).")
        appContext.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        appContext.deleteSharedPreferences(PREFS_FILE_NAME)
        EncryptedSharedPreferences.create(
            appContext,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    val geminiApiKey: Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, key ->
            if (key == GEMINI_API_KEY) {
                trySend(sharedPrefs.getString(GEMINI_API_KEY, "").orEmpty())
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getString(GEMINI_API_KEY, "").orEmpty())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    fun getGeminiApiKey(): String = prefs.getString(GEMINI_API_KEY, "").orEmpty()

    fun getGeminiApiKeyBlocking(): String = getGeminiApiKey()

    fun setGeminiApiKey(value: String) {
        prefs.edit().putString(GEMINI_API_KEY, value.trim()).apply()
    }

    fun clearGeminiApiKey() {
        prefs.edit().remove(GEMINI_API_KEY).apply()
    }

    fun hasGeminiApiKey(): Boolean = getGeminiApiKey().isNotBlank()

    companion object {
        private const val TAG = "EncryptedKeyStore"
        private const val PREFS_FILE_NAME = "wormhole_secure_prefs"
        private const val GEMINI_API_KEY = "gemini_api_key"
    }
}
