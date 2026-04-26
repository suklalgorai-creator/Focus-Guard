package com.focusguard.app.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Records every friction attempt for analytics and escalation tracking.
 */
@Entity(tableName = "attempts")
data class AttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Timestamp of when the attempt started */
    val timestamp: Long = System.currentTimeMillis(),

    /** Which app was blocked (package name) */
    val blockedPackage: String,

    /** Attempt number for the day (1-based) */
    val attemptNumber: Int,

    /** Escalation level that was applied */
    val escalationLevel: Int,

    /** Did the user eventually get through? */
    val wasSuccessful: Boolean = false,

    /** How long the friction session lasted (ms) */
    val durationMs: Long = 0,

    /** Did user use emergency exit? */
    val wasEmergencyExit: Boolean = false,

    /** Did user give up voluntarily? */
    val wasAbandoned: Boolean = false,

    /** Number of task failures during this attempt */
    val taskFailures: Int = 0,

    /** Date string for daily grouping (yyyy-MM-dd) */
    val dateKey: String
)
