package com.focusguard.app.domain.behavior

object BehaviorEngine {

    fun buildBehaviorState(
        stats: UserStats,
        nowMs: Long = System.currentTimeMillis()
    ): BehaviorState {
        val userType = detectUserType(stats)
        return BehaviorState(
            weakSubjects = detectWeakSubjects(stats.subjectAccuracy),
            strongSubjects = detectStrongSubjects(stats.subjectAccuracy),
            riskLevel = detectRisk(stats, nowMs),
            userType = userType,
            dailyGoal = getDailyGoal(userType)
        )
    }

    fun detectWeakSubjects(accuracy: Map<String, Double>): List<String> {
        return accuracy
            .filter { (_, value) -> value < WEAK_ACCURACY_THRESHOLD }
            .toList()
            .sortedBy { (_, value) -> value }
            .map { (subject, _) -> subject }
    }

    fun detectStrongSubjects(accuracy: Map<String, Double>): List<String> {
        return accuracy
            .filter { (_, value) -> value > STRONG_ACCURACY_THRESHOLD }
            .toList()
            .sortedByDescending { (_, value) -> value }
            .map { (subject, _) -> subject }
    }

    fun calculateConsistency(
        attemptsPerDay: Int,
        lastActive: Long,
        nowMs: Long = System.currentTimeMillis()
    ): Double {
        val hoursInactive = inactiveHours(lastActive, nowMs)
        return when {
            attemptsPerDay >= 30 && hoursInactive < 6 -> 0.9
            attemptsPerDay >= 15 && hoursInactive < 12 -> 0.7
            attemptsPerDay >= 5 && hoursInactive < 24 -> 0.55
            else -> 0.4
        }
    }

    fun detectRisk(
        stats: UserStats,
        nowMs: Long = System.currentTimeMillis()
    ): RiskLevel {
        val inactiveHours = inactiveHours(stats.lastActive, nowMs)
        return when {
            stats.lastActive == 0L -> RiskLevel.HIGH
            inactiveHours > 24 -> RiskLevel.HIGH
            inactiveHours > 12 -> RiskLevel.HIGH
            stats.consistencyScore < 0.5 -> RiskLevel.MEDIUM
            stats.attemptsPerDay == 0 && inactiveHours > 6 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
    }

    fun detectUserType(stats: UserStats): UserType {
        return when {
            stats.streak >= 5 && stats.consistencyScore > 0.7 -> UserType.CONSISTENT
            stats.consistencyScore < 0.5 -> UserType.IRREGULAR
            else -> UserType.STRUGGLING
        }
    }

    fun getDailyGoal(userType: UserType): Int {
        return when (userType) {
            UserType.CONSISTENT -> 50
            UserType.STRUGGLING -> 20
            UserType.IRREGULAR -> 10
        }
    }

    private fun inactiveHours(lastActive: Long, nowMs: Long): Long {
        if (lastActive <= 0L) return Long.MAX_VALUE
        return ((nowMs - lastActive).coerceAtLeast(0L)) / HOUR_MS
    }

    private const val HOUR_MS = 1000L * 60L * 60L
    private const val WEAK_ACCURACY_THRESHOLD = 0.60
    private const val STRONG_ACCURACY_THRESHOLD = 0.75
}
