package com.focusguard.app.domain.analytics

import com.focusguard.app.domain.pyq.SubjectPerformanceStats

data class DailyAttemptCount(
    val day: String,
    val attempts: Int
)

data class AnalyticsDashboardState(
    val subjectAccuracy: List<SubjectPerformanceStats> = emptyList(),
    val todayAttempts: Int = 0,
    val last7Days: List<DailyAttemptCount> = emptyList(),
    val streak: Int = 0,
    val weakSubjects: List<String> = emptyList(),
    val averageTimeMs: Long = 0L,
    val recentAttemptCount: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
