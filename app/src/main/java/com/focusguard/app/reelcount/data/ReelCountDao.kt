package com.focusguard.app.reelcount.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelCountDao {

    @Query("SELECT * FROM reel_count_daily_stats WHERE dateKey = :dateKey LIMIT 1")
    fun observeStatsForDate(dateKey: String): Flow<ReelCountDailyStatsEntity?>

    @Query("SELECT * FROM reel_count_daily_stats WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getStatsForDate(dateKey: String): ReelCountDailyStatsEntity?

    @Query("SELECT * FROM reel_count_daily_stats ORDER BY dateKey DESC LIMIT :limit")
    fun observeRecentStats(limit: Int = 14): Flow<List<ReelCountDailyStatsEntity>>

    @Query("SELECT * FROM reel_count_daily_stats ORDER BY dateKey DESC LIMIT :limit")
    suspend fun getRecentStats(limit: Int = 14): List<ReelCountDailyStatsEntity>

    @Query("SELECT COUNT(*) FROM reel_count_daily_stats WHERE limitReached = 1")
    fun observeLimitReachedDaysCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStats(entity: ReelCountDailyStatsEntity)

    @Transaction
    suspend fun incrementViewedCount(
        dateKey: String,
        updatedAt: Long,
        dailyLimit: Int,
        limitEnabled: Boolean
    ): ReelCountDailyStatsEntity {
        val existing = getStatsForDate(dateKey)
        val nextViewed = (existing?.viewedCount ?: 0) + 1
        val reached = (existing?.limitReached == true) ||
            (limitEnabled && dailyLimit > 0 && nextViewed >= dailyLimit)
        val updated = ReelCountDailyStatsEntity(
            dateKey = dateKey,
            viewedCount = nextViewed,
            blockedAttempts = existing?.blockedAttempts ?: 0,
            limitReached = reached,
            updatedAt = updatedAt
        )
        upsertStats(updated)
        return updated
    }

    @Transaction
    suspend fun incrementBlockedAttempts(
        dateKey: String,
        updatedAt: Long
    ): ReelCountDailyStatsEntity {
        val existing = getStatsForDate(dateKey)
        val updated = ReelCountDailyStatsEntity(
            dateKey = dateKey,
            viewedCount = existing?.viewedCount ?: 0,
            blockedAttempts = (existing?.blockedAttempts ?: 0) + 1,
            limitReached = existing?.limitReached ?: false,
            updatedAt = updatedAt
        )
        upsertStats(updated)
        return updated
    }

    @Query("SELECT * FROM reel_count_limit_state WHERE dateKey = :dateKey LIMIT 1")
    fun observeLimitStateForDate(dateKey: String): Flow<ReelCountLimitStateEntity?>

    @Query("SELECT * FROM reel_count_limit_state WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getLimitStateForDate(dateKey: String): ReelCountLimitStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimitState(entity: ReelCountLimitStateEntity)
}
