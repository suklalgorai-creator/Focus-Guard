package com.focusguard.app.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScheduleEvaluatorTest {

    @Test
    fun daytimeBlock_matchesInsideWindowOnly() {
        val block = StudyBlockSchedule(
            id = "morning",
            title = "Morning",
            startHour = 9,
            startMinute = 0,
            endHour = 12,
            endMinute = 30,
            days = setOf(Calendar.MONDAY, Calendar.TUESDAY),
            enabled = true
        )

        assertTrue(ScheduleEvaluator.isWithinSchedule(true, listOf(block), Calendar.MONDAY, 10 * 60))
        assertFalse(ScheduleEvaluator.isWithinSchedule(true, listOf(block), Calendar.MONDAY, 12 * 60 + 30))
        assertFalse(ScheduleEvaluator.isWithinSchedule(true, listOf(block), Calendar.WEDNESDAY, 10 * 60))
    }

    @Test
    fun overnightBlock_crossesMidnightIntoNextDay() {
        val block = StudyBlockSchedule(
            id = "night",
            title = "Night",
            startHour = 22,
            startMinute = 0,
            endHour = 6,
            endMinute = 0,
            days = setOf(Calendar.SATURDAY),
            enabled = true
        )

        assertTrue(ScheduleEvaluator.isWithinSchedule(true, listOf(block), Calendar.SATURDAY, 23 * 60))
        assertTrue(ScheduleEvaluator.isWithinSchedule(true, listOf(block), Calendar.SUNDAY, 3 * 60))
        assertFalse(ScheduleEvaluator.isWithinSchedule(true, listOf(block), Calendar.SUNDAY, 7 * 60))
    }

    @Test
    fun overlappingBlocks_returnsFirstActiveEnabledBlock() {
        val disabled = StudyBlockSchedule("1", "Off", 9, 0, 12, 0, setOf(Calendar.MONDAY), enabled = false)
        val active = StudyBlockSchedule("2", "Active", 10, 0, 13, 0, setOf(Calendar.MONDAY), enabled = true)

        val result = ScheduleEvaluator.getActiveBlock(true, listOf(disabled, active), Calendar.MONDAY, 11 * 60)

        assertEquals("2", result?.id)
    }
}
