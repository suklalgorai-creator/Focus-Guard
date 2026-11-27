package com.focusguard.app.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockDeadlineTrackerTest {

    @Test
    fun createAndEvaluateSameBoot_preservesRemainingDuration() {
        val state = LockDeadlineTracker.create(
            durationMs = 30 * 60_000L,
            nowWallMs = 1_700_000_000_000L,
            nowElapsedMs = 100_000L
        )

        val eval = LockDeadlineTracker.evaluate(
            state = state,
            nowWallMs = 1_700_000_600_000L,
            nowElapsedMs = 700_000L
        )

        assertTrue(eval.isActive)
        assertFalse(eval.rebootDetected)
        assertEquals(20 * 60_000L, eval.remainingMs)
    }

    @Test
    fun rebootDetected_recalculatesRemainingTimeSafely() {
        val initial = LockDeadlineTracker.create(
            durationMs = 60 * 60_000L,
            nowWallMs = 1_700_000_000_000L,
            nowElapsedMs = 500_000L
        )

        // Device rebooted: elapsedRealtime reset to 15_000L (< 500_000L), 20 minutes passed on wall clock
        val postReboot = LockDeadlineTracker.evaluate(
            state = initial,
            nowWallMs = 1_700_001_200_000L,
            nowElapsedMs = 15_000L
        )

        assertTrue(postReboot.isActive)
        assertTrue(postReboot.rebootDetected)
        assertEquals(40 * 60_000L, postReboot.remainingMs)
        assertEquals(15_000L + 40 * 60_000L, postReboot.updatedState.elapsedDeadlineMs)
    }

    @Test
    fun expiredLockAfterReboot_returnsInactive() {
        val initial = LockDeadlineTracker.create(
            durationMs = 15 * 60_000L,
            nowWallMs = 1_700_000_000_000L,
            nowElapsedMs = 400_000L
        )

        val postReboot = LockDeadlineTracker.evaluate(
            state = initial,
            nowWallMs = 1_700_001_800_000L, // 30 minutes later
            nowElapsedMs = 10_000L
        )

        assertFalse(postReboot.isActive)
        assertEquals(0L, postReboot.remainingMs)
    }

    @Test
    fun backwardClockManipulationBeforeReboot_cannotExtendLockBeyondCheckpoint() {
        val initial = LockDeadlineTracker.create(
            durationMs = 30 * 60_000L,
            nowWallMs = 1_700_000_000_000L,
            nowElapsedMs = 200_000L
        )

        // 10 minutes pass in same boot (20m remaining checkpoint)
        val checkpoint = LockDeadlineTracker.evaluate(
            state = initial,
            nowWallMs = 1_700_000_600_000L,
            nowElapsedMs = 800_000L
        ).updatedState

        // User turns wall clock backwards by 1 hour and reboots
        val postReboot = LockDeadlineTracker.evaluate(
            state = checkpoint,
            nowWallMs = 1_699_996_400_000L,
            nowElapsedMs = 5_000L
        )

        assertTrue(postReboot.isActive)
        assertTrue(postReboot.remainingMs <= 20 * 60_000L)
    }

    @Test
    fun invalidTimestamps_failSafelyAsInactive() {
        val invalidDuration = LockDeadlineTracker.create(
            durationMs = -1000L,
            nowWallMs = 1_700_000_000_000L,
            nowElapsedMs = 1000L
        )
        val eval = LockDeadlineTracker.evaluate(
            state = invalidDuration,
            nowWallMs = 1_700_000_001_000L,
            nowElapsedMs = 2000L
        )

        assertFalse(eval.isActive)
        assertEquals(0L, eval.remainingMs)
    }
}
