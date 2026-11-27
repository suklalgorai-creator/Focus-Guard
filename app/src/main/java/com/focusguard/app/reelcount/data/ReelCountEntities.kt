package com.focusguard.app.reelcount.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.focusguard.app.reelcount.domain.ReelCountDailyStats
import com.focusguard.app.reelcount.domain.ReelCountLimitState
import com.focusguard.app.reelcount.domain.ReelCountLimitStatus

/**
 * Daily Reel Count statistics persisted in FocusGuardDatabase.
 * Uses local calendar dateKey (`yyyy-MM-dd`) as primary key to prevent duplicate daily rows.
 */
@Entity(
    tableName = "reel_count_daily_stats",
    indices = [Index(value = ["updatedAt"])]
)
data class ReelCountDailyStatsEntity(
    @PrimaryKey
    val dateKey: String,
    val viewedCount: Int,
    val blockedAttempts: Int,
    val limitReached: Boolean,
    val updatedAt: Long
) {
    fun toDomain(): ReelCountDailyStats = ReelCountDailyStats(
        dateKey = dateKey,
        viewedCount = viewedCount,
        blockedAttempts = blockedAttempts,
        limitReached = limitReached,
        updatedAt = updatedAt
    )
}

/**
 * Persisted daily limit decision state (`NOT_REACHED`, `AWAITING_CHOICE`, `CONTINUE_TODAY`, `BLOCK_TODAY`).
 * Scoped to `dateKey` so yesterday's decision automatically expires at local midnight.
 */
@Entity(tableName = "reel_count_limit_state")
data class ReelCountLimitStateEntity(
    @PrimaryKey
    val dateKey: String,
    val status: String,
    val lastPromptedAt: Long,
    val decisionUpdatedAt: Long
) {
    fun toDomain(): ReelCountLimitState = ReelCountLimitState(
        dateKey = dateKey,
        status = ReelCountLimitStatus.fromStored(status),
        lastPromptedAt = lastPromptedAt,
        decisionUpdatedAt = decisionUpdatedAt
    )
}
