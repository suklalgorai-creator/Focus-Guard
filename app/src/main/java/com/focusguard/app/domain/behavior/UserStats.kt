package com.focusguard.app.domain.behavior

data class UserStats(
    /**
     * Accuracy ratio from 0.0 to 1.0, not percentage.
     */
    val subjectAccuracy: Map<String, Double>,
    val attemptsPerDay: Int,
    val streak: Int,
    val lastActive: Long,
    val consistencyScore: Double
)
