package com.focusguard.app.persistence

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * Fast-access SharedPreferences wrapper for runtime state.
 * Used for data that needs instant read (no Room suspend overhead).
 */
class FocusGuardPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("focus_guard_prefs", Context.MODE_PRIVATE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ── Service State ──

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var isGuardActive: Boolean
        get() = prefs.getBoolean(KEY_GUARD_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_GUARD_ACTIVE, value).apply()

    // ── Daily Tracking ──

    var lastAttemptDate: String
        get() = prefs.getString(KEY_LAST_ATTEMPT_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_ATTEMPT_DATE, value).apply()

    var dailyAttemptCount: Int
        get() = prefs.getInt(KEY_DAILY_ATTEMPT_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_DAILY_ATTEMPT_COUNT, value).apply()

    var currentEscalationLevel: Int
        get() = prefs.getInt(KEY_ESCALATION_LEVEL, 0)
        set(value) = prefs.edit().putInt(KEY_ESCALATION_LEVEL, value).apply()

    // ── Stats ──

    var totalBlocksEver: Int
        get() = prefs.getInt(KEY_TOTAL_BLOCKS, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_BLOCKS, value).apply()

    var totalGiveUps: Int
        get() = prefs.getInt(KEY_TOTAL_GIVE_UPS, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_GIVE_UPS, value).apply()

    // ── Exam Config ──

    var targetExam: String
        get() = prefs.getString(KEY_TARGET_EXAM, "neet") ?: "neet"
        set(value) = prefs.edit().putString(KEY_TARGET_EXAM, value).apply()

    var examDate: Long
        get() = prefs.getLong(KEY_EXAM_DATE, 0L)
        set(value) = prefs.edit().putLong(KEY_EXAM_DATE, value).apply()

    var preferredSubjects: Set<String>
        get() = prefs.getStringSet(KEY_PREFERRED_SUBJECTS, defaultSubjectsForExam(targetExam))
            ?: defaultSubjectsForExam(targetExam)
        set(value) = prefs.edit().putStringSet(KEY_PREFERRED_SUBJECTS, value).apply()

    var isOnboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETE, value).apply()

    var isDarkThemeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_THEME_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_THEME_ENABLED, value).apply()

    var isUsageTrackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_USAGE_TRACKING_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_USAGE_TRACKING_ENABLED, value).apply()

    var totalFocusXp: Int
        get() = prefs.getInt(KEY_TOTAL_FOCUS_XP, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_FOCUS_XP, value.coerceAtLeast(0)).apply()

    var focusSessionStartTime: Long
        get() = prefs.getLong(KEY_FOCUS_SESSION_START_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_FOCUS_SESSION_START_TIME, value).apply()

    var focusSessionModeName: String
        get() = prefs.getString(KEY_FOCUS_SESSION_MODE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FOCUS_SESSION_MODE, value).apply()

    var isFocusSessionXpAwarded: Boolean
        get() = prefs.getBoolean(KEY_FOCUS_SESSION_XP_AWARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_FOCUS_SESSION_XP_AWARDED, value).apply()

    fun clearFocusSessionMetadata() {
        prefs.edit()
            .remove(KEY_FOCUS_SESSION_START_TIME)
            .remove(KEY_FOCUS_SESSION_MODE)
            .remove(KEY_FOCUS_SESSION_XP_AWARDED)
            .apply()
    }

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, generated).apply()
        return generated
    }

    fun getOrCreateUserProfileCreatedAt(): Long {
        val existing = prefs.getLong(KEY_USER_PROFILE_CREATED_AT, 0L)
        if (existing > 0L) return existing

        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_USER_PROFILE_CREATED_AT, now).apply()
        return now
    }

    private fun defaultSubjectsForExam(exam: String): Set<String> {
        return when (exam.lowercase()) {
            "jee" -> setOf("Physics", "Chemistry", "Mathematics")
            "upsc" -> setOf("Polity", "History", "Geography", "Economy")
            else -> setOf("Physics", "Chemistry", "Biology")
        }
    }

    // ── Strict Mode ──
    var blockEndTime: Long
        get() = prefs.getLong(KEY_BLOCK_END_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_BLOCK_END_TIME, value).apply()

    fun isStrictBlockActive(): Boolean {
        return System.currentTimeMillis() < blockEndTime
    }

    val isStrictModeEnabled: Boolean
        get() = isStrictBlockActive()

    var blockDurationMinutes: Int
        get() = prefs.getInt(KEY_BLOCK_DURATION, 60)
        set(value) = prefs.edit().putInt(KEY_BLOCK_DURATION, value).apply()

    var isStrictModeExitProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_STRICT_EXIT_PROTECTION, false)
        set(value) = prefs.edit().putBoolean(KEY_STRICT_EXIT_PROTECTION, value).apply()

    var hasAcceptedAccessibilityDisclosure: Boolean
        get() = prefs.getBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, value).apply()

    // ── Daily Schedule (User-Configurable) ──

    var isScheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()

    /** Schedule start hour (0-23). User sets this via ScheduleScreen. */
    var scheduleStartHour: Int
        get() = prefs.getInt(KEY_SCHEDULE_START_HOUR, 9)
        set(value) = prefs.edit().putInt(KEY_SCHEDULE_START_HOUR, value).apply()

    /** Schedule start minute (0-59). */
    var scheduleStartMinute: Int
        get() = prefs.getInt(KEY_SCHEDULE_START_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_SCHEDULE_START_MINUTE, value).apply()

    /** Schedule end hour (0-23). User sets this via ScheduleScreen. */
    var scheduleEndHour: Int
        get() = prefs.getInt(KEY_SCHEDULE_END_HOUR, 21)
        set(value) = prefs.edit().putInt(KEY_SCHEDULE_END_HOUR, value).apply()

    /** Schedule end minute (0-59). */
    var scheduleEndMinute: Int
        get() = prefs.getInt(KEY_SCHEDULE_END_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_SCHEDULE_END_MINUTE, value).apply()

    /**
     * Active days of the week. Stored as comma-separated ints.
     * 1=Sunday, 2=Monday, ..., 7=Saturday (Calendar constants)
     */
    var scheduleDays: Set<Int>
        get() {
            val raw = prefs.getString(KEY_SCHEDULE_DAYS, "2,3,4,5,6,7") ?: "2,3,4,5,6,7"
            return raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        }
        set(value) = prefs.edit().putString(KEY_SCHEDULE_DAYS, value.joinToString(",")).apply()

    /**
     * Check if current time is within the user-defined schedule.
     * Supports overnight schedules (e.g., 10PM → 6AM).
     * Returns true if schedule is enabled AND current time/day falls within the window.
     */
    fun isWithinSchedule(): Boolean {
        if (!isScheduleEnabled) return false

        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        if (currentDay !in scheduleDays) return false

        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val startMinutes = scheduleStartHour * 60 + scheduleStartMinute
        val endMinutes = scheduleEndHour * 60 + scheduleEndMinute

        return if (startMinutes <= endMinutes) {
            // Normal schedule: e.g., 9:00 AM (540) → 9:00 PM (1260)
            currentMinutes in startMinutes until endMinutes
        } else {
            // Overnight schedule: e.g., 10:00 PM (1320) → 6:00 AM (360)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        }
    }

    /**
     * Combined check: is the guard active right now?
     * True if EITHER schedule is active OR strict mode timer is running.
     */
    fun isGuardActiveNow(): Boolean {
        return isGuardActive || isWithinSchedule() || isStrictBlockActive()
    }

    val canBlockCriticalActions: Boolean
        get() {
            val isFocusActive = isGuardActiveNow()
            return isStrictModeEnabled &&
                isStrictModeExitProtectionEnabled &&
                isFocusActive &&
                hasAcceptedAccessibilityDisclosure
        }

    // ── Blacklist / Whitelist ──

    var blacklistedApps: Set<String>
        get() = prefs.getStringSet(KEY_BLACKLIST, DEFAULT_BLACKLIST) ?: DEFAULT_BLACKLIST
        set(value) = prefs.edit().putStringSet(KEY_BLACKLIST, value).apply()

    var whitelistedApps: Set<String>
        get() = prefs.getStringSet(KEY_WHITELIST, DEFAULT_WHITELIST) ?: DEFAULT_WHITELIST
        set(value) = prefs.edit().putStringSet(KEY_WHITELIST, value).apply()

    var blockedContentSurfaces: Set<String>
        get() = prefs.getStringSet(KEY_BLOCKED_CONTENT_SURFACES, DEFAULT_BLOCKED_CONTENT_SURFACES)
            ?: DEFAULT_BLOCKED_CONTENT_SURFACES
        set(value) = prefs.edit().putStringSet(KEY_BLOCKED_CONTENT_SURFACES, value).apply()

    // ── Anti-Bypass ──

    var bypassPenalty: Int
        get() = prefs.getInt(KEY_BYPASS_PENALTY, 0)
        set(value) = prefs.edit().putInt(KEY_BYPASS_PENALTY, value).apply()

    var lastPermissionCheckFailed: Boolean
        get() = prefs.getBoolean(KEY_PERM_CHECK_FAILED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERM_CHECK_FAILED, value).apply()

    // ── Helper Methods ──

    fun getTodayKey(): String = dateFormat.format(Date())

    fun getDaysUntilExam(): Int {
        val date = examDate
        if (date == 0L) return -1
        val diff = date - System.currentTimeMillis()
        return (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    // Backward compat alias
    fun getDaysUntilNeet(): Int = getDaysUntilExam()

    /**
     * Increments daily attempt count with automatic daily reset.
     * Returns the new attempt number.
     */
    fun incrementDailyAttempt(): Int {
        val today = getTodayKey()
        if (lastAttemptDate != today) {
            // New day — reset
            dailyAttemptCount = 0
            currentEscalationLevel = 0
            lastAttemptDate = today
        }
        val newCount = dailyAttemptCount + 1
        dailyAttemptCount = newCount
        totalBlocksEver = totalBlocksEver + 1
        return newCount
    }

    companion object {
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_GUARD_ACTIVE = "guard_active"
        private const val KEY_LAST_ATTEMPT_DATE = "last_attempt_date"
        private const val KEY_DAILY_ATTEMPT_COUNT = "daily_attempt_count"
        private const val KEY_ESCALATION_LEVEL = "escalation_level"
        private const val KEY_TOTAL_BLOCKS = "total_blocks"
        private const val KEY_TOTAL_GIVE_UPS = "total_give_ups"
        private const val KEY_TARGET_EXAM = "target_exam"
        private const val KEY_EXAM_DATE = "exam_date"
        private const val KEY_PREFERRED_SUBJECTS = "preferred_subjects"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val KEY_DARK_THEME_ENABLED = "dark_theme_enabled"
        private const val KEY_USAGE_TRACKING_ENABLED = "usage_tracking_enabled"
        private const val KEY_TOTAL_FOCUS_XP = "total_focus_xp"
        private const val KEY_FOCUS_SESSION_START_TIME = "focus_session_start_time"
        private const val KEY_FOCUS_SESSION_MODE = "focus_session_mode"
        private const val KEY_FOCUS_SESSION_XP_AWARDED = "focus_session_xp_awarded"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_USER_PROFILE_CREATED_AT = "user_profile_created_at"
        private const val KEY_BLOCK_END_TIME = "block_end_time"
        private const val KEY_BLOCK_DURATION = "block_duration"
        private const val KEY_STRICT_EXIT_PROTECTION = "strict_exit_protection"
        private const val KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted"
        private const val KEY_BLACKLIST = "blacklisted_apps"
        private const val KEY_WHITELIST = "whitelisted_apps"
        private const val KEY_BLOCKED_CONTENT_SURFACES = "blocked_content_surfaces"
        private const val KEY_BYPASS_PENALTY = "bypass_penalty"
        private const val KEY_PERM_CHECK_FAILED = "perm_check_failed"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_START_HOUR = "schedule_start_hour"
        private const val KEY_SCHEDULE_START_MINUTE = "schedule_start_minute"
        private const val KEY_SCHEDULE_END_HOUR = "schedule_end_hour"
        private const val KEY_SCHEDULE_END_MINUTE = "schedule_end_minute"
        private const val KEY_SCHEDULE_DAYS = "schedule_days"

        val DEFAULT_BLACKLIST = setOf(
            "com.instagram.android",
            "com.instagram.lite",            // Instagram Lite
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",      // TikTok
            "com.snapchat.android",
            "com.twitter.android",
            "com.facebook.katana",
        )

        val DEFAULT_WHITELIST = setOf(
            "com.focusguard.app",   // Self
            "com.android.dialer",
            "com.android.contacts",
            "com.google.android.apps.messaging",
            "com.android.calculator2",
        )

        val DEFAULT_BLOCKED_CONTENT_SURFACES = emptySet<String>()

        const val SURFACE_INSTAGRAM_REELS = "instagram_reels"
    }
}
