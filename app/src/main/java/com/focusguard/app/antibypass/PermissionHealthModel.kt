package com.focusguard.app.antibypass

enum class EnforcementLevel {
    ACTIVE,
    DEGRADED,
    UNAVAILABLE
}

enum class MissingPermissionType(val label: String) {
    ACCESSIBILITY("Accessibility Service"),
    OVERLAY("Display Over Other Apps"),
    USAGE_STATS("Usage Access"),
    NOTIFICATIONS("Notifications")
}

data class PermissionSnapshot(
    val accessibilityEnabled: Boolean,
    val overlayEnabled: Boolean,
    val usageStatsEnabled: Boolean,
    val notificationsEnabled: Boolean = true,
    val batteryOptimizationIgnored: Boolean = true
)

data class PermissionHealthSummary(
    val snapshot: PermissionSnapshot,
    val enforcementLevel: EnforcementLevel,
    val canEnforceCoreBlocking: Boolean,
    val canShowChallengeOverlay: Boolean,
    val canRunUsageFallback: Boolean,
    val missingRequired: List<MissingPermissionType>,
    val missingOptional: List<MissingPermissionType>,
    val primaryMissing: MissingPermissionType?,
    val affectedFeaturesSummary: String
)

/**
 * Pure-Kotlin evaluator for Focus Guard's permission dependencies.
 *
 * Dependency contract:
 * - Accessibility Service: REQUIRED for core app/Reels blocking and Settings protection.
 * - Display Over Other Apps: OPTIONAL for PYQ challenge overlay; without it, blocking degrades
 *   to force-back / force-home without an interactive overlay.
 * - Usage Access: OPTIONAL for background fallback detection and screen-time reconciliation;
 *   without it, Accessibility-based blocking still functions.
 * - Notifications: OPTIONAL for service status and permission/fallback alerts.
 */
object PermissionHealthEvaluator {

    fun evaluate(snapshot: PermissionSnapshot): PermissionHealthSummary {
        val missingRequired = buildList {
            if (!snapshot.accessibilityEnabled) add(MissingPermissionType.ACCESSIBILITY)
        }
        val missingOptional = buildList {
            if (!snapshot.overlayEnabled) add(MissingPermissionType.OVERLAY)
            if (!snapshot.usageStatsEnabled) add(MissingPermissionType.USAGE_STATS)
            if (!snapshot.notificationsEnabled) add(MissingPermissionType.NOTIFICATIONS)
        }

        val enforcementLevel = when {
            !snapshot.accessibilityEnabled -> EnforcementLevel.UNAVAILABLE
            !snapshot.overlayEnabled || !snapshot.usageStatsEnabled -> EnforcementLevel.DEGRADED
            else -> EnforcementLevel.ACTIVE
        }

        val primaryMissing = missingRequired.firstOrNull() ?: missingOptional.firstOrNull()

        val affectedFeaturesSummary = when {
            !snapshot.accessibilityEnabled ->
                "Accessibility Service is off. Reels detection, app blocking, and exit protection are paused."
            !snapshot.overlayEnabled && !snapshot.usageStatsEnabled ->
                "Core blocking works, but PYQ challenge overlays and usage fallback/analytics are unavailable."
            !snapshot.overlayEnabled ->
                "Core blocking works, but the PYQ challenge overlay cannot appear without Display Over Other Apps."
            !snapshot.usageStatsEnabled ->
                "Core blocking works, but backup foreground detection and screen-time reconciliation are unavailable."
            !snapshot.notificationsEnabled ->
                "Core blocking works, but permission and fallback alerts are muted."
            else ->
                "All permissions granted. Full blocking, PYQ overlays, and usage analytics are active."
        }

        return PermissionHealthSummary(
            snapshot = snapshot,
            enforcementLevel = enforcementLevel,
            canEnforceCoreBlocking = snapshot.accessibilityEnabled,
            canShowChallengeOverlay = snapshot.accessibilityEnabled && snapshot.overlayEnabled,
            canRunUsageFallback = snapshot.usageStatsEnabled,
            missingRequired = missingRequired,
            missingOptional = missingOptional,
            primaryMissing = primaryMissing,
            affectedFeaturesSummary = affectedFeaturesSummary
        )
    }
}
