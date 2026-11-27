package com.focusguard.app.reelcount.domain

/**
 * Pure-Kotlin evaluator for Reel Count's daily limit state machine.
 *
 * Enforces:
 * - Local calendar date (`yyyy-MM-dd`) rollover; yesterday's decisions never carry over.
 * - `ASK` behavior: triggers the choice prompt exactly once on first crossing.
 * - `CONTINUE_TODAY` suppresses further prompts for the remainder of the local day.
 * - `BLOCK_TODAY` enforces Reel Count blocking until the next local midnight.
 * - Disabled Reel Count never enforces its own limit.
 */
class ReelLimitEvaluator(
    private val clock: ReelCountClock
) {
    data class EvaluationResult(
        val effectiveState: ReelCountLimitState,
        val shouldPromptUser: Boolean,
        val shouldBlockByReelCount: Boolean,
        val stateChanged: Boolean
    )

    fun evaluate(
        settings: ReelCountSettings,
        stats: ReelCountDailyStats,
        persistedState: ReelCountLimitState
    ): EvaluationResult {
        val todayKey = clock.todayDateKey()

        // Roll over to a clean NOT_REACHED state if persisted state belongs to a previous day.
        val currentState = if (persistedState.dateKey == todayKey) {
            persistedState
        } else {
            ReelCountLimitState(dateKey = todayKey, status = ReelCountLimitStatus.NOT_REACHED)
        }

        if (!settings.enabled) {
            return EvaluationResult(
                effectiveState = currentState,
                shouldPromptUser = false,
                shouldBlockByReelCount = false,
                stateChanged = false
            )
        }

        if (!settings.limitEnabled || settings.dailyLimit <= 0) {
            return EvaluationResult(
                effectiveState = currentState,
                shouldPromptUser = false,
                shouldBlockByReelCount = false,
                stateChanged = false
            )
        }

        return when (currentState.status) {
            ReelCountLimitStatus.BLOCK_TODAY -> EvaluationResult(
                effectiveState = currentState,
                shouldPromptUser = false,
                shouldBlockByReelCount = true,
                stateChanged = false
            )

            ReelCountLimitStatus.CONTINUE_TODAY -> EvaluationResult(
                effectiveState = currentState,
                shouldPromptUser = false,
                shouldBlockByReelCount = false,
                stateChanged = false
            )

            ReelCountLimitStatus.AWAITING_CHOICE -> EvaluationResult(
                effectiveState = currentState,
                shouldPromptUser = false,
                shouldBlockByReelCount = false,
                stateChanged = false
            )

            ReelCountLimitStatus.NOT_REACHED -> {
                if (stats.dateKey == todayKey && stats.viewedCount >= settings.dailyLimit) {
                    val now = clock.nowMillis()
                    val nextState = ReelCountLimitState(
                        dateKey = todayKey,
                        status = ReelCountLimitStatus.AWAITING_CHOICE,
                        lastPromptedAt = now,
                        decisionUpdatedAt = now
                    )
                    EvaluationResult(
                        effectiveState = nextState,
                        shouldPromptUser = true,
                        shouldBlockByReelCount = false,
                        stateChanged = true
                    )
                } else {
                    EvaluationResult(
                        effectiveState = currentState,
                        shouldPromptUser = false,
                        shouldBlockByReelCount = false,
                        stateChanged = false
                    )
                }
            }
        }
    }
}
