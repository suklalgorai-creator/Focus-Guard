package com.focusguard.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusguard.app.domain.settings.FocusSettings
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val SETTINGS_DATASTORE_NAME = "focus_guard_settings"
private const val LEGACY_PREFS_NAME = "focus_guard_prefs"

private val Context.focusGuardSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, LEGACY_PREFS_NAME))
    }
)

/**
 * DataStore-backed source of truth for Focus Guard settings.
 *
 * Key names intentionally match the old SharedPreferences keys so AndroidX can
 * migrate existing installs without losing Strict Mode or disclosure state.
 */
class SettingsDataStore(private val context: Context) {

    val settings: Flow<FocusSettings> = context.focusGuardSettingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            FocusSettings(
                strictModeEndTimeMillis = preferences[Keys.STRICT_MODE_END_TIME_MILLIS] ?: 0L,
                strictModeDurationMinutes = preferences[Keys.STRICT_MODE_DURATION_MINUTES] ?: 60,
                isStrictModeExitProtectionEnabled =
                    preferences[Keys.STRICT_MODE_EXIT_PROTECTION_ENABLED] ?: false,
                hasAcceptedAccessibilityDisclosure =
                    preferences[Keys.ACCESSIBILITY_DISCLOSURE_ACCEPTED] ?: false
            )
        }

    suspend fun setStrictModeEndTimeMillis(value: Long) {
        context.focusGuardSettingsDataStore.edit { preferences ->
            preferences[Keys.STRICT_MODE_END_TIME_MILLIS] = value
        }
    }

    suspend fun setStrictModeDurationMinutes(value: Int) {
        context.focusGuardSettingsDataStore.edit { preferences ->
            preferences[Keys.STRICT_MODE_DURATION_MINUTES] = value
        }
    }

    suspend fun setStrictModeExitProtectionEnabled(value: Boolean) {
        context.focusGuardSettingsDataStore.edit { preferences ->
            preferences[Keys.STRICT_MODE_EXIT_PROTECTION_ENABLED] = value
        }
    }

    suspend fun setAccessibilityDisclosureAccepted(value: Boolean) {
        context.focusGuardSettingsDataStore.edit { preferences ->
            preferences[Keys.ACCESSIBILITY_DISCLOSURE_ACCEPTED] = value
        }
    }

    private object Keys {
        val STRICT_MODE_END_TIME_MILLIS = longPreferencesKey("block_end_time")
        val STRICT_MODE_DURATION_MINUTES = intPreferencesKey("block_duration")
        val STRICT_MODE_EXIT_PROTECTION_ENABLED = booleanPreferencesKey("strict_exit_protection")
        val ACCESSIBILITY_DISCLOSURE_ACCEPTED =
            booleanPreferencesKey("accessibility_disclosure_accepted")
    }
}
