package com.focusguard.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.focusguard.app.domain.auth.AuthSession
import com.focusguard.app.domain.auth.AuthUser

class AuthSessionStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                STORE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrElse { exception ->
            Log.e(TAG, "Encrypted auth session store unavailable; falling back to standard SharedPreferences", exception)
            appContext.getSharedPreferences("${STORE_NAME}_fallback", Context.MODE_PRIVATE)
        }
    }

    fun loadSession(): AuthSession? {
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }
            ?: return null

        return AuthSession(
            accessToken = accessToken,
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() },
            expiresAtMillis = prefs.getLong(KEY_EXPIRES_AT, 0L),
            user = AuthUser(
                id = userId,
                email = prefs.getString(KEY_EMAIL, null),
                name = prefs.getString(KEY_NAME, null)
            )
        )
    }

    fun saveSession(session: AuthSession) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, session.accessToken)
            .putString(KEY_REFRESH_TOKEN, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtMillis)
            .putString(KEY_USER_ID, session.user.id)
            .putString(KEY_EMAIL, session.user.email)
            .putString(KEY_NAME, session.user.name)
            .putBoolean(KEY_AUTH_PROMPT_SKIPPED, false)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_USER_ID)
            .remove(KEY_EMAIL)
            .remove(KEY_NAME)
            .apply()
    }

    fun hasSkippedLogin(): Boolean {
        return prefs.getBoolean(KEY_AUTH_PROMPT_SKIPPED, false)
    }

    fun setLoginPromptSkipped(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTH_PROMPT_SKIPPED, value).apply()
    }

    companion object {
        private const val TAG = "AuthSessionStore"
        private const val STORE_NAME = "focus_guard_auth_session"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_NAME = "name"
        private const val KEY_AUTH_PROMPT_SKIPPED = "auth_prompt_skipped"
    }
}
