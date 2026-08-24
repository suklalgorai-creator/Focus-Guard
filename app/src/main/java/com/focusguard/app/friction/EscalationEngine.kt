package com.focusguard.app.friction

import com.focusguard.app.FocusGuardApp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Tracks attempts per day and calculates escalation parameters.
 *
 * Escalation Table:
 * ┌──────────┬────────────┬────────────┬────────────────┬─────────────────┐
 * │ Attempt  │ Base Delay │ Task Count │ Failure Chance │ Est. Total Time │
 * ├──────────┼────────────┼────────────┼────────────────┼─────────────────┤
 * │ 1        │ 10-30s     │ 1          │ 20%            │ ~1-2 min        │
 * │ 2        │ 30-60s     │ 1-2        │ 35%            │ ~4-5 min        │
 * │ 3        │ 60-120s    │ 2-3        │ 45%            │ ~8-10 min       │
 * │ 4+       │ 120-300s+  │ 3-4        │ 55%            │ ~15+ min        │
 * └──────────┴────────────┴────────────┴────────────────┴─────────────────┘
 *
 * Bypass penalties add +2 to escalation level on top of attempt-based scaling.
 */
class EscalationEngine {

    data class EscalationParams(
        val escalationLevel: Int,
        val attemptNumber: Int,
        val baseDelayRange: IntRange,
        val taskCount: Int,
        val failureChance: Float,
        val pipelineLayerCount: Int
    )

    /**
     * Calculate escalation parameters for the current attempt.
     */
    fun calculate(): EscalationParams {
        val prefs = FocusGuardApp.instance.prefs

        // Increment daily attempt count (auto-resets on new day)
        val attemptNumber = prefs.incrementDailyAttempt()
        prefs.updateStreakOnNewDay()

        // Base escalation from attempt count
        val baseLevel = when (attemptNumber) {
            1 -> 0
            2 -> 1
            3 -> 2
            else -> 3 + (attemptNumber - 4) // Keeps growing
        }

        // Add bypass penalty
        val bypassPenalty = prefs.bypassPenalty
        val escalationLevel = min(baseLevel + bypassPenalty, 10) // Cap at 10

        // Store current level
        prefs.currentEscalationLevel = escalationLevel

        val taskCount = when (escalationLevel) {
            0 -> 1
            1 -> Random.nextInt(1, 3)
            2 -> Random.nextInt(2, 4)
            else -> Random.nextInt(3, 5)
        }

        val failureChance = when {
            escalationLevel <= 0 -> 0.20f
            escalationLevel == 1 -> 0.35f
            escalationLevel == 2 -> 0.45f
            else -> min(0.55f + (escalationLevel - 3) * 0.05f, 0.80f)
        }

        // Number of friction layers in the pipeline (more layers = more steps)
        val layerCount = when {
            escalationLevel <= 0 -> 3  // Delay + Task + Wait
            escalationLevel == 1 -> 4  // + FailureInjection
            escalationLevel == 2 -> 5  // + extra Wait
            else -> 6 + (escalationLevel - 3) // Keeps growing
        }

        return EscalationParams(
            escalationLevel = escalationLevel,
            attemptNumber = attemptNumber,
            baseDelayRange = calculateDelayRange(escalationLevel),
            taskCount = taskCount,
            failureChance = failureChance,
            pipelineLayerCount = layerCount
        )
    }

    private fun calculateDelayRange(level: Int): IntRange {
        return when (level) {
            0 -> 10..30
            1 -> 30..60
            2 -> 60..120
            3 -> 120..300
            else -> 300..(300 + level * 60)
        }
    }

    /**
     * Apply bypass penalty (called when user tries to access Settings).
     */
    fun applyBypassPenalty(extraLevels: Int = 2) {
        val prefs = FocusGuardApp.instance.prefs
        prefs.bypassPenalty = prefs.bypassPenalty + extraLevels
    }

    /**
     * Reset bypass penalty (daily reset or after extended good behavior).
     */
    fun resetBypassPenalty() {
        FocusGuardApp.instance.prefs.bypassPenalty = 0
    }
}
