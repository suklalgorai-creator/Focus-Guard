package com.focusguard.app.reelcount.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelLimitEvaluatorAndPolicyTest {

    private class TestClock(var dateKey: String = "2026-11-27") : ReelCountClock {
        override fun nowMillis(): Long = 1_700_000_000_000L
        override fun elapsedRealtimeMs(): Long = 10_000L
        override fun todayDateKey(): String = dateKey
    }

    @Test
    fun `below limit does not trigger prompt`() {
        val clock = TestClock("2026-11-27")
        val evaluator = ReelLimitEvaluator(clock)
        val result = evaluator.evaluate(
            settings = ReelCountSettings(enabled = true, dailyLimit = 30, limitEnabled = true),
            stats = ReelCountDailyStats(dateKey = "2026-11-27", viewedCount = 29),
            persistedState = ReelCountLimitState(dateKey = "2026-11-27", status = ReelCountLimitStatus.NOT_REACHED)
        )
        assertFalse(result.shouldPromptUser)
        assertFalse(result.shouldBlockByReelCount)
    }

    @Test
    fun `first crossing triggers ask prompt once`() {
        val clock = TestClock("2026-11-27")
        val evaluator = ReelLimitEvaluator(clock)
        val settings = ReelCountSettings(enabled = true, dailyLimit = 30, limitEnabled = true)
        val stats = ReelCountDailyStats(dateKey = "2026-11-27", viewedCount = 30)

        val first = evaluator.evaluate(
            settings = settings,
            stats = stats,
            persistedState = ReelCountLimitState(dateKey = "2026-11-27", status = ReelCountLimitStatus.NOT_REACHED)
        )
        assertTrue(first.shouldPromptUser)
        assertEquals(ReelCountLimitStatus.AWAITING_CHOICE, first.effectiveState.status)

        // Subsequent events while awaiting choice must not re-prompt.
        val second = evaluator.evaluate(
            settings = settings,
            stats = stats,
            persistedState = first.effectiveState
        )
        assertFalse(second.shouldPromptUser)
    }

    @Test
    fun `continue today suppresses prompt and block today enforces until midnight`() {
        val clock = TestClock("2026-11-27")
        val evaluator = ReelLimitEvaluator(clock)
        val settings = ReelCountSettings(enabled = true, dailyLimit = 30, limitEnabled = true)
        val stats = ReelCountDailyStats(dateKey = "2026-11-27", viewedCount = 35)

        val continueResult = evaluator.evaluate(
            settings = settings,
            stats = stats,
            persistedState = ReelCountLimitState(dateKey = "2026-11-27", status = ReelCountLimitStatus.CONTINUE_TODAY)
        )
        assertFalse(continueResult.shouldPromptUser)
        assertFalse(continueResult.shouldBlockByReelCount)

        val blockResult = evaluator.evaluate(
            settings = settings,
            stats = stats,
            persistedState = ReelCountLimitState(dateKey = "2026-11-27", status = ReelCountLimitStatus.BLOCK_TODAY)
        )
        assertFalse(blockResult.shouldPromptUser)
        assertTrue(blockResult.shouldBlockByReelCount)

        // Midnight rollover to new local date resets yesterday's BLOCK_TODAY.
        clock.dateKey = "2026-11-28"
        val nextDayResult = evaluator.evaluate(
            settings = settings,
            stats = ReelCountDailyStats(dateKey = "2026-11-28", viewedCount = 0),
            persistedState = ReelCountLimitState(dateKey = "2026-11-27", status = ReelCountLimitStatus.BLOCK_TODAY)
        )
        assertEquals("2026-11-28", nextDayResult.effectiveState.dateKey)
        assertEquals(ReelCountLimitStatus.NOT_REACHED, nextDayResult.effectiveState.status)
        assertFalse(nextDayResult.shouldBlockByReelCount)
    }

    @Test
    fun `disabled reel count does not enforce its own limit`() {
        val clock = TestClock("2026-11-27")
        val evaluator = ReelLimitEvaluator(clock)
        val result = evaluator.evaluate(
            settings = ReelCountSettings(enabled = false, dailyLimit = 10, limitEnabled = true),
            stats = ReelCountDailyStats(dateKey = "2026-11-27", viewedCount = 20),
            persistedState = ReelCountLimitState(dateKey = "2026-11-27", status = ReelCountLimitStatus.BLOCK_TODAY)
        )
        assertFalse(result.shouldPromptUser)
        assertFalse(result.shouldBlockByReelCount)
    }

    @Test
    fun `combined blocking policy keeps focus guard independent and dominant`() {
        val focusGuardDecision = ReelsBlockingPolicy.evaluate(
            focusGuardRequiresBlock = true,
            reelCountEnabled = true,
            reelCountLimitEnabled = true,
            todayDateKey = "2026-11-27",
            limitState = ReelCountLimitState(dateKey = "2026-11-27", status = ReelCountLimitStatus.CONTINUE_TODAY)
        )
        assertTrue(focusGuardDecision.shouldBlock)
        assertEquals(ReelsBlockSource.FOCUS_GUARD, focusGuardDecision.source)

        val reelCountDecision = ReelsBlockingPolicy.evaluate(
            focusGuardRequiresBlock = false,
            reelCountEnabled = true,
            reelCountLimitEnabled = true,
            todayDateKey = "2026-11-27",
            limitState = ReelCountLimitState(dateKey = "2026-11-27", status = ReelCountLimitStatus.BLOCK_TODAY)
        )
        assertTrue(reelCountDecision.shouldBlock)
        assertEquals(ReelsBlockSource.REEL_COUNT_LIMIT, reelCountDecision.source)

        val disabledReelCountStillBlocksFocusGuard = ReelsBlockingPolicy.evaluate(
            focusGuardRequiresBlock = true,
            reelCountEnabled = false,
            reelCountLimitEnabled = false,
            todayDateKey = "2026-11-27",
            limitState = ReelCountLimitState(dateKey = "2026-11-27", status = ReelCountLimitStatus.NOT_REACHED)
        )
        assertTrue(disabledReelCountStillBlocksFocusGuard.shouldBlock)
        assertEquals(ReelsBlockSource.FOCUS_GUARD, disabledReelCountStillBlocksFocusGuard.source)
    }
}
