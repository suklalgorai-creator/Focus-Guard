package com.focusguard.app.data.behavior

import com.focusguard.app.data.pyq.PyqRepository
import com.focusguard.app.domain.behavior.BehaviorEngine
import com.focusguard.app.domain.behavior.BehaviorState
import com.focusguard.app.domain.behavior.UserProfile
import com.focusguard.app.domain.behavior.UserStats
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.persistence.PyqAttemptDao
import java.time.LocalDate
import java.time.ZoneId

class BehaviorRepository(
    private val prefs: FocusGuardPrefs,
    private val attemptDao: PyqAttemptDao,
    private val pyqRepository: PyqRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun getUserProfile(): UserProfile {
        return UserProfile(
            exam = prefs.targetExam.uppercase(),
            targetDate = prefs.examDate,
            preferredSubjects = prefs.preferredSubjects.toList().sorted(),
            createdAt = prefs.getOrCreateUserProfileCreatedAt()
        )
    }

    suspend fun getUserStats(): UserStats {
        val now = clock()
        val todayStart = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val subjectAccuracy = pyqRepository.getSubjectPerformance()
            .associate { stats ->
                stats.subject to (stats.accuracyPercent / 100.0).coerceIn(0.0, 1.0)
            }

        val attemptsToday = attemptDao.getAttemptCountBetween(todayStart, now)
        val lastActive = attemptDao.getLastAttemptTimestamp() ?: 0L

        return UserStats(
            subjectAccuracy = subjectAccuracy,
            attemptsPerDay = attemptsToday,
            streak = calculateStreak(attemptDao.getRecentAttemptDays()),
            lastActive = lastActive,
            consistencyScore = BehaviorEngine.calculateConsistency(
                attemptsPerDay = attemptsToday,
                lastActive = lastActive,
                nowMs = now
            )
        )
    }

    suspend fun getBehaviorState(): BehaviorState {
        return BehaviorEngine.buildBehaviorState(getUserStats(), clock())
    }

    suspend fun getDailyGoal(): Int {
        return getBehaviorState().dailyGoal
    }

    private fun calculateStreak(dayStrings: List<String>): Int {
        if (dayStrings.isEmpty()) return 0

        val activeDays = dayStrings
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
            .toSet()

        var cursor = LocalDate.now(ZoneId.systemDefault())
        if (cursor !in activeDays) {
            cursor = cursor.minusDays(1)
        }

        var streak = 0
        while (cursor in activeDays) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

}
