package com.focusguard.app.data.settings

import com.focusguard.app.domain.settings.FocusSettings
import com.focusguard.app.persistence.FocusGuardPrefs
import kotlinx.coroutines.flow.Flow

/**
 * Repository boundary for settings.
 *
 * During Phase 2 this repository dual-writes to DataStore and FocusGuardPrefs.
 * DataStore is the new async source for UI/ViewModels; FocusGuardPrefs remains
 * the compatibility layer for existing services that require instant reads.
 */
class SettingsRepository(
    private val settingsDataStore: SettingsDataStore,
    private val legacyPrefs: FocusGuardPrefs
) {
    val settings: Flow<FocusSettings> = settingsDataStore.settings

    fun getLegacySnapshot(): FocusSettings {
        return FocusSettings(
            strictModeEndTimeMillis = legacyPrefs.blockEndTime,
            strictModeDurationMinutes = legacyPrefs.blockDurationMinutes,
            isStrictModeExitProtectionEnabled = legacyPrefs.isStrictModeExitProtectionEnabled,
            hasAcceptedAccessibilityDisclosure = legacyPrefs.hasAcceptedAccessibilityDisclosure
        )
    }

    suspend fun setStrictModeEndTimeMillis(value: Long) {
        legacyPrefs.blockEndTime = value
        if (value <= 0L) {
            legacyPrefs.strictModeStartElapsed = 0L
            legacyPrefs.strictModeDurationMs = 0L
        }
        settingsDataStore.setStrictModeEndTimeMillis(value)
    }

    suspend fun setStrictModeDurationMinutes(value: Int) {
        legacyPrefs.blockDurationMinutes = value
        settingsDataStore.setStrictModeDurationMinutes(value)
    }

    suspend fun setStrictModeExitProtectionEnabled(value: Boolean) {
        legacyPrefs.isStrictModeExitProtectionEnabled = value
        settingsDataStore.setStrictModeExitProtectionEnabled(value)
    }

    suspend fun setAccessibilityDisclosureAccepted(value: Boolean) {
        legacyPrefs.hasAcceptedAccessibilityDisclosure = value
        settingsDataStore.setAccessibilityDisclosureAccepted(value)
    }

    suspend fun enableStrictMode(
        durationMinutes: Int,
        exitProtectionEnabled: Boolean,
        keepRequestedDuration: Boolean = false
    ) {
        val effectiveDurationMinutes = if (exitProtectionEnabled && !keepRequestedDuration) {
            STRICT_HARDLOCK_MINUTES
        } else {
            durationMinutes
        }
        val durationMs = effectiveDurationMinutes.toLong() * 60_000L
        val endTimeMillis = System.currentTimeMillis() + durationMs
        setStrictModeDurationMinutes(effectiveDurationMinutes)
        setStrictModeExitProtectionEnabled(exitProtectionEnabled)
        legacyPrefs.strictModeStartElapsed = android.os.SystemClock.elapsedRealtime()
        legacyPrefs.strictModeDurationMs = durationMs
        setStrictModeEndTimeMillis(endTimeMillis)
    }

    suspend fun disableStrictMode() {
        setStrictModeEndTimeMillis(0L)
        setStrictModeExitProtectionEnabled(false)
    }

    companion object {
        const val STRICT_HARDLOCK_MINUTES = 30 * 24 * 60
    }
}
