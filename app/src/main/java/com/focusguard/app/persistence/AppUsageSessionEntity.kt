package com.focusguard.app.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_usage_sessions",
    indices = [
        Index(value = ["dateKey"]),
        Index(value = ["packageName", "startTime"]),
        Index(value = ["isDistracting", "dateKey"])
    ]
)
data class AppUsageSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val packageName: String,
    val startTime: Long,
    val endTime: Long,
    val durationMs: Long,
    val dateKey: String,
    val isDistracting: Boolean
)

@Entity(tableName = "daily_stats")
data class DailyStatsEntity(
    @PrimaryKey
    val dateKey: String,
    val totalUsageTimeMs: Long,
    val distractionTimeMs: Long,
    val timeSavedMs: Long,
    val sessionsBlocked: Int,
    val updatedAt: Long
)
