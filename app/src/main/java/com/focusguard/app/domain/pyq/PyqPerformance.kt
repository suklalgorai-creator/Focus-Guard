package com.focusguard.app.domain.pyq

enum class SubjectPerformanceCategory {
    STRONG,
    MODERATE,
    WEAK
}

data class SubjectPerformanceStats(
    val subject: String,
    val totalAttempts: Int,
    val correctAttempts: Int,
    val accuracyPercent: Double,
    val category: SubjectPerformanceCategory
)

enum class AttemptConfidenceSignal {
    NORMAL,
    SLOW_CORRECT_LOW_CONFIDENCE,
    FAST_WRONG_GUESSING
}

data class AttemptInsight(
    val confidenceScore: Int,
    val signal: AttemptConfidenceSignal
)
