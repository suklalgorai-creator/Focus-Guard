package com.focusguard.app.reelcount.domain

/**
 * User-configurable Reel Count preferences.
 */
data class ReelCountSettings(
    val enabled: Boolean = false,
    val floatingCounterEnabled: Boolean = true,
    val dailyLimit: Int = DEFAULT_DAILY_LIMIT,
    val limitEnabled: Boolean = true
) {
    companion object {
        const val DEFAULT_DAILY_LIMIT = 30
        const val MIN_DAILY_LIMIT = 1
        const val MAX_DAILY_LIMIT = 500
        val PRESET_LIMITS = listOf(15, 30, 50, 100)
    }
}

/**
 * Daily Reel Count statistics for a single local calendar day (`yyyy-MM-dd`).
 * Viewed Reels and blocked attempts are tracked strictly separately.
 */
data class ReelCountDailyStats(
    val dateKey: String,
    val viewedCount: Int = 0,
    val blockedAttempts: Int = 0,
    val limitReached: Boolean = false,
    val updatedAt: Long = 0L
)

/**
 * Daily limit decision state for a single local calendar day.
 */
enum class ReelCountLimitStatus {
    NOT_REACHED,
    AWAITING_CHOICE,
    CONTINUE_TODAY,
    BLOCK_TODAY;

    companion object {
        fun fromStored(raw: String?): ReelCountLimitStatus {
            return entries.firstOrNull { it.name == raw } ?: NOT_REACHED
        }
    }
}

data class ReelCountLimitState(
    val dateKey: String,
    val status: ReelCountLimitStatus = ReelCountLimitStatus.NOT_REACHED,
    val lastPromptedAt: Long = 0L,
    val decisionUpdatedAt: Long = 0L
)

/**
 * Combined snapshot exposed to UI and overlay observers.
 */
data class ReelCountSnapshot(
    val settings: ReelCountSettings,
    val todayStats: ReelCountDailyStats,
    val limitState: ReelCountLimitState,
    val isInstagramForeground: Boolean = false
) {
    val remainingCount: Int?
        get() = if (settings.limitEnabled && settings.dailyLimit > 0) {
            (settings.dailyLimit - todayStats.viewedCount).coerceAtLeast(0)
        } else {
            null
        }
}

/**
 * Distinguishable tracking events emitted by the Reel Count pipeline.
 */
sealed interface ReelTrackingEvent {
    data class ReelViewDetected(
        val dateKey: String,
        val timestampMs: Long,
        val confidenceScore: Int
    ) : ReelTrackingEvent

    data class ReelBlockedAttempt(
        val dateKey: String,
        val timestampMs: Long
    ) : ReelTrackingEvent
}
