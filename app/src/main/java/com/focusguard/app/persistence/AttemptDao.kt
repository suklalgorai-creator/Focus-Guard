package com.focusguard.app.persistence

import androidx.room.*

/**
 * DAO for attempt records. Provides queries for escalation logic and dashboard stats.
 */
@Dao
interface AttemptDao {

    @Insert
    suspend fun insert(attempt: AttemptEntity): Long

    @Update
    suspend fun update(attempt: AttemptEntity)

    /** Get all attempts for a specific date (for daily escalation) */
    @Query("SELECT * FROM attempts WHERE dateKey = :dateKey ORDER BY timestamp ASC")
    suspend fun getAttemptsForDate(dateKey: String): List<AttemptEntity>

    /** Count attempts for a specific date */
    @Query("SELECT COUNT(*) FROM attempts WHERE dateKey = :dateKey")
    suspend fun getAttemptCountForDate(dateKey: String): Int

    /** Count attempts for a specific app on a specific date */
    @Query("SELECT COUNT(*) FROM attempts WHERE dateKey = :dateKey AND blockedPackage = :packageName")
    suspend fun getAttemptCountForAppOnDate(dateKey: String, packageName: String): Int

    /** Get all attempts (for dashboard history) */
    @Query("SELECT * FROM attempts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAttempts(limit: Int = 50): List<AttemptEntity>

    /** Total blocks ever */
    @Query("SELECT COUNT(*) FROM attempts")
    suspend fun getTotalBlockCount(): Int

    /** Total successful bypasses */
    @Query("SELECT COUNT(*) FROM attempts WHERE wasSuccessful = 1")
    suspend fun getTotalSuccessCount(): Int

    /** Total abandoned (user gave up) */
    @Query("SELECT COUNT(*) FROM attempts WHERE wasAbandoned = 1")
    suspend fun getTotalAbandonedCount(): Int

    /** Get today's stats */
    @Query("SELECT COUNT(*) FROM attempts WHERE dateKey = :dateKey AND wasSuccessful = 1")
    suspend fun getSuccessCountForDate(dateKey: String): Int

    /** Average friction duration for today */
    @Query("SELECT AVG(durationMs) FROM attempts WHERE dateKey = :dateKey")
    suspend fun getAvgDurationForDate(dateKey: String): Long?

    /** Delete old records (keep last 90 days) */
    @Query("DELETE FROM attempts WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long)
}
