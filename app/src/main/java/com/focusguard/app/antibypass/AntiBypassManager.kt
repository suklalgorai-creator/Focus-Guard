package com.focusguard.app.antibypass

import android.content.Context
import android.util.Log
import com.focusguard.app.persistence.FocusGuardPrefs

class AntiBypassManager(
    context: Context,
    private val prefs: FocusGuardPrefs
) {
    private val appContext = context.applicationContext

    fun recordSettingsProtectionTriggered(reason: String) {
        recordEvent(
            type = EVENT_SETTINGS_PROTECTION,
            reason = reason,
            penaltyLevels = 2,
            onlyDuringActiveBlock = true
        )
    }

    fun recordPermissionLoss(
        accessibilityOk: Boolean,
        overlayOk: Boolean,
        usageStatsOk: Boolean
    ) {
        val missingPermissions = mutableListOf<String>()
        if (!accessibilityOk) missingPermissions.add("accessibility")
        if (!overlayOk) missingPermissions.add("overlay")
        if (!usageStatsOk) missingPermissions.add("usage_stats")
        val missing = missingPermissions.joinToString(",").ifBlank { "unknown" }

        recordEvent(
            type = EVENT_PERMISSION_LOSS,
            reason = missing,
            penaltyLevels = 1,
            onlyDuringActiveBlock = true
        )
    }

    fun recordServiceRestart(reason: String) {
        recordEvent(
            type = EVENT_SERVICE_RESTART,
            reason = reason,
            penaltyLevels = 0,
            onlyDuringActiveBlock = true
        )
    }

    private fun recordEvent(
        type: String,
        reason: String,
        penaltyLevels: Int,
        onlyDuringActiveBlock: Boolean
    ) {
        if (onlyDuringActiveBlock && !prefs.isGuardActiveNow()) return

        prefs.bypassEventCount = prefs.bypassEventCount + 1
        prefs.lastBypassEventType = type
        prefs.lastBypassEventAt = System.currentTimeMillis()
        if (penaltyLevels > 0) {
            prefs.bypassPenalty = prefs.bypassPenalty + penaltyLevels
        }

        Log.w(TAG, "Anti-bypass event: type=$type reason=$reason package=${appContext.packageName}")
    }

    companion object {
        private const val TAG = "AntiBypassManager"
        const val EVENT_SETTINGS_PROTECTION = "settings_protection"
        const val EVENT_PERMISSION_LOSS = "permission_loss"
        const val EVENT_SERVICE_RESTART = "service_restart"
    }
}
