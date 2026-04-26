package com.focusguard.app.domain.usage

import com.focusguard.app.domain.AppUsageData

data class AppUsageSession(
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val isDistracting: Boolean
)

data class UsageAnalyticsSummary(
    val dateKey: String,
    val totalUsageTimeMs: Long = 0L,
    val distractionTimeMs: Long = 0L,
    val timeSavedMs: Long = 0L,
    val sessionsBlocked: Int = 0,
    val topApps: List<AppUsageData> = emptyList(),
    val allApps: List<AppUsageData> = emptyList()
)
