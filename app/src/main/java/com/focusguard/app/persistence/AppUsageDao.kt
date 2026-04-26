package com.focusguard.app.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

data class AppUsageAggregateRow(
    val packageName: String,
    val usageTimeMs: Long,
    val sessionCount: Long,
    val distractingSessionCount: Long
)

data class DailyUsageTotalsRow(
    val totalUsageTimeMs: Long?,
    val distractionTimeMs: Long?,
    val sessionCount: Long
)

@Dao
interface AppUsageDao {

    @Insert
    suspend fun insertSession(session: AppUsageSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyStats(stats: DailyStatsEntity)

    @Query("SELECT * FROM daily_stats WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getDailyStats(dateKey: String): DailyStatsEntity?

    @Query(
        """
        SELECT
            SUM(durationMs) AS totalUsageTimeMs,
            SUM(CASE WHEN isDistracting THEN durationMs ELSE 0 END) AS distractionTimeMs,
            COUNT(*) AS sessionCount
        FROM app_usage_sessions
        WHERE dateKey = :dateKey
        """
    )
    suspend fun getDailyUsageTotals(dateKey: String): DailyUsageTotalsRow

    @Query(
        """
        SELECT
            packageName,
            SUM(durationMs) AS usageTimeMs,
            COUNT(*) AS sessionCount,
            SUM(CASE WHEN isDistracting THEN 1 ELSE 0 END) AS distractingSessionCount
        FROM app_usage_sessions
        WHERE dateKey = :dateKey
        GROUP BY packageName
        ORDER BY usageTimeMs DESC
        LIMIT :limit
        """
    )
    suspend fun getTopApps(dateKey: String, limit: Int): List<AppUsageAggregateRow>

    @Query(
        """
        SELECT
            packageName,
            SUM(durationMs) AS usageTimeMs,
            COUNT(*) AS sessionCount,
            SUM(CASE WHEN isDistracting THEN 1 ELSE 0 END) AS distractingSessionCount
        FROM app_usage_sessions
        WHERE dateKey = :dateKey
        GROUP BY packageName
        ORDER BY usageTimeMs DESC
        """
    )
    suspend fun getUsageByApp(dateKey: String): List<AppUsageAggregateRow>

    @Query(
        """
        SELECT AVG(durationMs)
        FROM app_usage_sessions
        WHERE packageName = :packageName
            AND durationMs >= :minDurationMs
            AND startTime >= :sinceMs
        """
    )
    suspend fun getAverageSessionDuration(
        packageName: String,
        sinceMs: Long,
        minDurationMs: Long
    ): Double?

    @Query(
        """
        SELECT COUNT(*)
        FROM app_usage_sessions
        WHERE packageName = :packageName
            AND startTime < :endTime
            AND endTime > :startTime
        """
    )
    suspend fun countOverlappingSessions(
        packageName: String,
        startTime: Long,
        endTime: Long
    ): Int

    @Query("SELECT * FROM app_usage_sessions WHERE dateKey = :dateKey ORDER BY startTime DESC")
    suspend fun getSessionsForDate(dateKey: String): List<AppUsageSessionEntity>
}
