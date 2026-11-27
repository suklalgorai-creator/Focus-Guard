package com.focusguard.app.persistence

import java.util.Calendar

/**
 * Pure-Kotlin evaluator for single and multi-block focus schedules.
 * Supports weekday filtering, same-day windows, full-day windows (start == end),
 * and overnight windows crossing midnight.
 */
object ScheduleEvaluator {

    fun isWithinSchedule(
        scheduleEnabled: Boolean,
        blocks: List<StudyBlockSchedule>,
        dayOfWeek: Int,
        minuteOfDay: Int
    ): Boolean {
        return getActiveBlock(
            scheduleEnabled = scheduleEnabled,
            blocks = blocks,
            dayOfWeek = dayOfWeek,
            minuteOfDay = minuteOfDay
        ) != null
    }

    fun getActiveBlock(
        scheduleEnabled: Boolean,
        blocks: List<StudyBlockSchedule>,
        dayOfWeek: Int,
        minuteOfDay: Int
    ): StudyBlockSchedule? {
        if (!scheduleEnabled) return null
        return blocks.firstOrNull { block ->
            block.enabled && isBlockActiveAt(block, dayOfWeek, minuteOfDay)
        }
    }

    fun isBlockActiveAt(
        block: StudyBlockSchedule,
        currentDay: Int,
        currentMinutes: Int
    ): Boolean {
        if (currentDay !in Calendar.SUNDAY..Calendar.SATURDAY) return false
        if (currentMinutes !in 0 until 24 * 60) return false

        val startMinutes = block.startHour.coerceIn(0, 23) * 60 + block.startMinute.coerceIn(0, 59)
        val endMinutes = block.endHour.coerceIn(0, 23) * 60 + block.endMinute.coerceIn(0, 59)

        return when {
            startMinutes == endMinutes -> currentDay in block.days
            startMinutes < endMinutes ->
                currentDay in block.days && currentMinutes in startMinutes until endMinutes
            else -> {
                val previousDay = if (currentDay == Calendar.SUNDAY) {
                    Calendar.SATURDAY
                } else {
                    currentDay - 1
                }
                (currentDay in block.days && currentMinutes >= startMinutes) ||
                    (previousDay in block.days && currentMinutes < endMinutes)
            }
        }
    }
}
