package com.focusguard.app.data.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.focusguard.app.domain.profile.UserProfile
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private const val PROFILE_DATASTORE_NAME = "focus_guard_profile"
private const val LEGACY_PREFS_NAME = "focus_guard_prefs"

private val Context.focusGuardProfileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = PROFILE_DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, LEGACY_PREFS_NAME))
    }
)

class ProfileDataStore(private val context: Context) {

    val profile: Flow<UserProfile> = context.focusGuardProfileDataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences ->
            val exam = preferences[Keys.EXAM] ?: "neet"
            UserProfile(
                deviceId = preferences[Keys.DEVICE_ID] ?: "",
                exam = exam,
                targetDate = preferences[Keys.TARGET_DATE] ?: 0L,
                preferredSubjects = decodeSubjects(preferences[Keys.PREFERRED_SUBJECTS], exam),
                isOnboardingComplete = preferences[Keys.ONBOARDING_COMPLETE] ?: false,
                createdAt = preferences[Keys.CREATED_AT] ?: 0L
            )
        }

    suspend fun saveProfile(
        deviceId: String,
        exam: String,
        targetDate: Long,
        preferredSubjects: List<String>,
        isOnboardingComplete: Boolean
    ) {
        context.focusGuardProfileDataStore.edit { preferences ->
            preferences[Keys.DEVICE_ID] = deviceId
            preferences[Keys.EXAM] = exam.lowercase()
            preferences[Keys.TARGET_DATE] = targetDate
            preferences[Keys.PREFERRED_SUBJECTS] = preferredSubjects.joinToString("|")
            preferences[Keys.ONBOARDING_COMPLETE] = isOnboardingComplete
            if ((preferences[Keys.CREATED_AT] ?: 0L) == 0L) {
                preferences[Keys.CREATED_AT] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setOnboardingComplete(value: Boolean) {
        context.focusGuardProfileDataStore.edit { preferences ->
            preferences[Keys.ONBOARDING_COMPLETE] = value
        }
    }

    private fun decodeSubjects(raw: String?, exam: String): List<String> {
        return raw
            ?.split("|")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
            ?: UserProfile.defaultSubjectsForExam(exam)
    }

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val EXAM = stringPreferencesKey("target_exam")
        val TARGET_DATE = longPreferencesKey("exam_date")
        val PREFERRED_SUBJECTS = stringPreferencesKey("preferred_subjects_csv")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val CREATED_AT = longPreferencesKey("user_profile_created_at")
    }
}
