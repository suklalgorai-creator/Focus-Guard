package com.focusguard.app.data.analytics

import com.focusguard.app.data.behavior.BehaviorRepository
import com.focusguard.app.data.pyq.PyqRepository
import com.focusguard.app.domain.analytics.AnalyticsDashboardState
import com.focusguard.app.domain.analytics.DailyAttemptCount
import com.focusguard.app.persistence.PyqAttemptDao
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsRepository(
    private val attemptDao: PyqAttemptDao,
    private val pyqRepository: PyqRepository,
    private val behaviorRepository: BehaviorRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    @Volatile
    private var cachedDashboardState: AnalyticsDashboardState? = null

    @Volatile
    private var cachedDashboardStateAtMs: Long = 0L

    suspend fun getSubjectAccuracy() = pyqRepository.getSubjectPerformance()

    suspend fun getTodayAttempts(): Int {
        return attemptDao.getAttemptCountBetween(todayStartMillis(), clock())
    }

    suspend fun getStreak(): Int {
        return behaviorRepository.getUserStats().streak
    }

    suspend fun getWeakSubjects(): List<String> {
        return behaviorRepository.getBehaviorState().weakSubjects
    }

    suspend fun getAverageTime(): Long {
        return (attemptDao.getAverageTimeTakenMs() ?: 0.0).toLong()
    }

    suspend fun getLast7DaysAttempts(): List<DailyAttemptCount> {
        val since = LocalDate.now(ZoneId.systemDefault())
            .minusDays(6)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val rowsByDay = attemptDao.getDailyAttemptCountsSince(since)
            .associateBy { it.day }

        return (6 downTo 0).map { offset ->
            val day = LocalDate.now(ZoneId.systemDefault()).minusDays(offset.toLong()).toString()
            DailyAttemptCount(
                day = day,
                attempts = rowsByDay[day]?.attempts ?: 0
            )
        }
    }

    suspend fun getDashboardState(forceRefresh: Boolean = false): AnalyticsDashboardState {
        val now = clock()
        cachedDashboardState?.takeIf {
            !forceRefresh && (now - cachedDashboardStateAtMs) < DASHBOARD_CACHE_TTL_MS
        }?.let { return it }

        val freshState = buildDashboardState()
        cachedDashboardState = freshState
        cachedDashboardStateAtMs = now
        return freshState
    }

    private suspend fun buildDashboardState(): AnalyticsDashboardState {
        val subjectAccuracy = getSubjectAccuracy()
        return AnalyticsDashboardState(
            subjectAccuracy = subjectAccuracy,
            todayAttempts = getTodayAttempts(),
            last7Days = getLast7DaysAttempts(),
            streak = getStreak(),
            weakSubjects = getWeakSubjects(),
            averageTimeMs = getAverageTime(),
            recentAttemptCount = pyqRepository.getRecentAttempts(limit = 50).size
        )
    }

    private fun todayStartMillis(): Long {
        return LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    companion object {
        private const val DASHBOARD_CACHE_TTL_MS = 10_000L
    }
}
