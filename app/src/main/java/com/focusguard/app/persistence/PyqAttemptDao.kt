package com.focusguard.app.persistence

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * DAO for question-level PYQ attempts.
 */
@Dao
interface PyqAttemptDao {

    @Insert
    suspend fun insertAttempt(attempt: PyqAttemptEntity): Long

    @Query("SELECT * FROM pyq_attempts WHERE subject = :subject ORDER BY timestamp DESC")
    suspend fun getAttemptsBySubject(subject: String): List<PyqAttemptEntity>

    @Query("SELECT * FROM pyq_attempts WHERE subject = :subject ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastAttemptsForSubject(subject: String, limit: Int): List<PyqAttemptEntity>

    @Query("SELECT * FROM pyq_attempts WHERE subject = :subject AND isCorrect = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getIncorrectAttemptsBySubject(subject: String, limit: Int): List<PyqAttemptEntity>

    @Query("SELECT * FROM pyq_attempts WHERE questionId = :questionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAttemptsByQuestionId(questionId: Int, limit: Int = 20): List<PyqAttemptEntity>

    @Query("SELECT * FROM pyq_attempts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAttempts(limit: Int = 50): List<PyqAttemptEntity>

    @Query("SELECT * FROM pyq_attempts WHERE isCorrect = 0 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentIncorrectAttempts(limit: Int = 20): List<PyqAttemptEntity>

    @Query("SELECT * FROM pyq_attempts WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getAttemptsSince(sinceTimestamp: Long, limit: Int = 100): List<PyqAttemptEntity>

    @Query("SELECT COUNT(*) FROM pyq_attempts WHERE timestamp BETWEEN :startTimestamp AND :endTimestamp")
    suspend fun getAttemptCountBetween(startTimestamp: Long, endTimestamp: Long): Int

    @Query("SELECT AVG(timeTakenMs) FROM pyq_attempts")
    suspend fun getAverageTimeTakenMs(): Double?

    @Query("SELECT AVG(timeTakenMs) FROM pyq_attempts WHERE subject = :subject")
    suspend fun getAverageTimeTakenMsBySubject(subject: String): Double?

    @Query(
        """
        SELECT
            date(timestamp / 1000, 'unixepoch', 'localtime') AS day,
            COUNT(*) AS attempts
        FROM pyq_attempts
        WHERE timestamp >= :sinceTimestamp
        GROUP BY day
        ORDER BY day ASC
        """
    )
    suspend fun getDailyAttemptCountsSince(sinceTimestamp: Long): List<DailyAttemptCountRow>

    @Query("SELECT MAX(timestamp) FROM pyq_attempts")
    suspend fun getLastAttemptTimestamp(): Long?

    @Query(
        """
        SELECT day FROM (
            SELECT
                date(timestamp / 1000, 'unixepoch', 'localtime') AS day,
                MAX(timestamp) AS latestTimestamp
            FROM pyq_attempts
            GROUP BY day
            ORDER BY latestTimestamp DESC
            LIMIT :limit
        )
        LIMIT :limit
        """
    )
    suspend fun getRecentAttemptDays(limit: Int = 60): List<String>

    @Query("SELECT questionId FROM pyq_attempts ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentlyAttemptedQuestionIds(limit: Int = 10): List<Int>

    @Query("SELECT DISTINCT questionId FROM pyq_attempts")
    suspend fun getDistinctAttemptedQuestionIds(): List<Int>

    @Query("SELECT DISTINCT subject FROM pyq_attempts ORDER BY subject ASC")
    suspend fun getAttemptedSubjects(): List<String>

    @Query(
        """
        SELECT
            subject,
            COUNT(*) AS totalAttempts,
            COALESCE(SUM(CASE WHEN isCorrect = 1 THEN 1 ELSE 0 END), 0) AS correctAttempts,
            (
                CAST(COALESCE(SUM(CASE WHEN isCorrect = 1 THEN 1 ELSE 0 END), 0) AS REAL)
                / COUNT(*)
            ) * 100.0 AS accuracyPercent
        FROM pyq_attempts
        GROUP BY subject
        ORDER BY accuracyPercent ASC
        """
    )
    suspend fun getAccuracyBySubject(): List<SubjectAccuracyRow>
}

/**
 * Lightweight query projection used by the adaptive selector.
 */
data class SubjectAccuracyRow(
    val subject: String,
    val totalAttempts: Long,
    val correctAttempts: Long,
    val accuracyPercent: Double
)

data class DailyAttemptCountRow(
    val day: String,
    val attempts: Int
)
