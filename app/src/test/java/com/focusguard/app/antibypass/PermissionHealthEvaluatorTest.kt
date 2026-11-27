package com.focusguard.app.antibypass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionHealthEvaluatorTest {

    @Test
    fun allPermissionsGranted_isActive() {
        val summary = PermissionHealthEvaluator.evaluate(
            PermissionSnapshot(
                accessibilityEnabled = true,
                overlayEnabled = true,
                usageStatsEnabled = true,
                notificationsEnabled = true
            )
        )

        assertEquals(EnforcementLevel.ACTIVE, summary.enforcementLevel)
        assertTrue(summary.canEnforceCoreBlocking)
        assertTrue(summary.canShowChallengeOverlay)
        assertTrue(summary.canRunUsageFallback)
        assertTrue(summary.missingRequired.isEmpty())
    }

    @Test
    fun accessibilityRevoked_isUnavailableWithSpecificMessage() {
        val summary = PermissionHealthEvaluator.evaluate(
            PermissionSnapshot(
                accessibilityEnabled = false,
                overlayEnabled = true,
                usageStatsEnabled = true
            )
        )

        assertEquals(EnforcementLevel.UNAVAILABLE, summary.enforcementLevel)
        assertFalse(summary.canEnforceCoreBlocking)
        assertEquals(MissingPermissionType.ACCESSIBILITY, summary.primaryMissing)
        assertTrue(summary.affectedFeaturesSummary.contains("Accessibility Service"))
    }

    @Test
    fun overlayRevoked_isDegradedButCoreBlockingRemainsAvailable() {
        val summary = PermissionHealthEvaluator.evaluate(
            PermissionSnapshot(
                accessibilityEnabled = true,
                overlayEnabled = false,
                usageStatsEnabled = true
            )
        )

        assertEquals(EnforcementLevel.DEGRADED, summary.enforcementLevel)
        assertTrue(summary.canEnforceCoreBlocking)
        assertFalse(summary.canShowChallengeOverlay)
        assertEquals(MissingPermissionType.OVERLAY, summary.primaryMissing)
        assertTrue(summary.affectedFeaturesSummary.contains("PYQ challenge overlay"))
    }

    @Test
    fun usageStatsRevoked_isDegradedButCoreBlockingRemainsAvailable() {
        val summary = PermissionHealthEvaluator.evaluate(
            PermissionSnapshot(
                accessibilityEnabled = true,
                overlayEnabled = true,
                usageStatsEnabled = false
            )
        )

        assertEquals(EnforcementLevel.DEGRADED, summary.enforcementLevel)
        assertTrue(summary.canEnforceCoreBlocking)
        assertFalse(summary.canRunUsageFallback)
        assertEquals(MissingPermissionType.USAGE_STATS, summary.primaryMissing)
        assertTrue(summary.affectedFeaturesSummary.contains("backup foreground detection"))
    }
}
