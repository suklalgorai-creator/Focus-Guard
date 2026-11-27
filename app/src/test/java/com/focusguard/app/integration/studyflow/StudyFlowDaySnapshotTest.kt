package com.focusguard.app.integration.studyflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StudyFlowDaySnapshotTest {

    @Test
    fun invalidDateKey_isRejected() {
        val json = """{"dateKey":"not-a-date","pendingItems":[]}"""
        assertNull(StudyFlowDaySnapshot.fromJsonString(json))
    }

    @Test
    fun oversizedPayload_isRejected() {
        val huge = "x".repeat(40_000)
        val json = """{"dateKey":"2026-11-27","headline":"$huge","pendingItems":[]}"""
        assertNull(StudyFlowDaySnapshot.fromJsonString(json))
    }

    @Test
    fun validPayload_sanitizesControlCharsAndBoundsFields() {
        val json = """
            {
              "dateKey": "2026-11-27",
              "headline": "Focus\u0000 Today",
              "pendingItems": [
                {"id": "1", "title": "Revise Optics", "etaMinutes": 45},
                {"id": "2", "title": "Bad ETA", "etaMinutes": 99999}
              ]
            }
        """.trimIndent()

        val snapshot = StudyFlowDaySnapshot.fromJsonString(json)
        assertNotNull(snapshot)
        assertEquals("Focus Today", snapshot!!.headline)
        assertEquals(2, snapshot.pendingItems.size)
        assertEquals(45, snapshot.pendingItems[0].etaMinutes)
        assertNull(snapshot.pendingItems[1].etaMinutes)
    }
}
