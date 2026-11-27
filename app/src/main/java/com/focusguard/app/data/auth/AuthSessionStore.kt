package com.focusguard.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.focusguard.app.domain.auth.AuthSession
import com.focusguard.app.domain.auth.AuthUser

/**
 * Keystore-backed session store.
 *
 * Security contract:
 * - Tokens are stored exclusively in [EncryptedSharedPreferences] (AES256-SIV keys, AES256-GCM values).
 * - Never falls back to plaintext SharedPreferences for access or refresh tokens.
 * - On Keystore/keyset corruption, clears the corrupted store once and retries; if still
 *   unavailable, fails closed and requires reauthentication.
 */
class AuthSessionStore(context: Context) {

    private val appContext = context.applicationContext

    private val uiPrefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(UI_PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Volatile
    var isSecureStorageUnavailable: Boolean = false
        private set

    private val encryptedPrefs: SharedPreferences? by lazy {
        purgeLegacyPlaintextFallback()
        createEncryptedPrefsWithSingleReset()
    }

    private fun createEncryptedPrefsWithSingleReset(): SharedPreferences? {
        val firstAttempt = runCatching { buildEncryptedPrefs() }
        if (firstAttempt.isSuccess) {
            isSecureStorageUnavailable = false
            uiPrefs.edit().putBoolean(KEY_RESET_ATTEMPTED, false).apply()
            return firstAttempt.getOrNull()
        }

        if (uiPrefs.getBoolean(KEY_RESET_ATTEMPTED, false)) {
            Log.e(TAG, "Encrypted auth session store remains unavailable; failing closed without repeated reset")
            isSecureStorageUnavailable = true
            return null
        }

        Log.w(TAG, "Encrypted session store initialization failed; performing one-time reset")
        uiPrefs.edit().putBoolean(KEY_RESET_ATTEMPTED, true).apply()
        runCatching { appContext.deleteSharedPreferences(STORE_NAME) }

        val retryAttempt = runCatching { buildEncryptedPrefs() }
        if (retryAttempt.isSuccess) {
            isSecureStorageUnavailable = false
            uiPrefs.edit().putBoolean(KEY_RESET_ATTEMPTED, false).apply()
            return retryAttempt.getOrNull()
        }

        Log.e(TAG, "Encrypted auth session store unavailable; failing closed")
        isSecureStorageUnavailable = true
        return null
    }

    private fun buildEncryptedPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            appContext,
            STORE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun purgeLegacyPlaintextFallback() {
        runCatching {
            appContext.deleteSharedPreferences("${STORE_NAME}_fallback")
        }
    }

    fun loadSession(): AuthSession? {
        val store = encryptedPrefs ?: return null
        return runCatching {
            val accessToken = store.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
                ?: return@runCatching null
            val userId = store.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }
                ?: return@runCatching null

            AuthSession(
                accessToken = accessToken,
                refreshToken = store.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() },
                expiresAtMillis = store.getLong(KEY_EXPIRES_AT, 0L),
                user = AuthUser(
                    id = userId,
                    email = store.getString(KEY_EMAIL, null),
                    name = store.getString(KEY_NAME, null)
                )
            )
        }.onFailure {
            Log.w(TAG, "Failed to decrypt stored session; clearing invalid credentials")
            clearSession()
        }.getOrNull()
    }

    fun saveSession(session: AuthSession) {
        val store = encryptedPrefs
            ?: throw IllegalStateException("Secure credential storage is unavailable on this device.")
        store.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_EMAIL, session.user.email)
            .putString(KEY_NAME, session.user.name)
            .apply()
        setLoginPromptSkipped(false)
    }

    fun clearSession() {
        purgeLegacyPlaintextFallback()
        runCatching {
            encryptedPrefs?.edit()
                ?.remove(KEY_ACCESS_TOKEN)
                ?.remove(KEY_REFRESH_TOKEN)
                ?.remove(KEY_EXPIRES_AT)
                ?.remove(KEY_USER_ID)
                ?.remove(KEY_EMAIL)
                ?.remove(KEY_NAME)
                ?.apply()
        }
    }

    fun hasSkippedLogin(): Boolean {
        return uiPrefs.getBoolean(KEY_AUTH_PROMPT_SKIPPED, false)
    }

    fun setLoginPromptSkipped(value: Boolean) {
        uiPrefs.edit().putBoolean(KEY_AUTH_PROMPT_SKIPPED, value).apply()
    }

    companion object {
        private const val TAG = "AuthSessionStore"
        private const val STORE_NAME = "focus_guard_auth_session"
        private const val UI_PREFS_NAME = "focus_guard_auth_ui_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
        private const val KEY_AUTH_PROMPT_SKIPPED = "auth_prompt_skipped"
        private const val KEY_RESET_ATTEMPTED = "keystore_reset_attempted"
    }
}
