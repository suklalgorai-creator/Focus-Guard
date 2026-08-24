package com.focusguard.app.persistence

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.focusguard.app.integration.studyflow.StudyFlowDaySnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

data class DistractionRecoverySnapshot(
    val sourceKey: String,
    val sourceTitle: String,
    val occurredAtMs: Long,
    val pyqSubject: String?,
    val pyqWasCorrect: Boolean?,
    val studyTaskTitle: String?,
    val studyTaskSubject: String?
)

data class StudyBlockSchedule(
    val id: String,
    val title: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val days: Set<Int>,
    val enabled: Boolean = true
)

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
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    /**
     * Set only after the user has completed onboarding and granted every
     * permission needed to show a real block. This prevents a restored or
     * half-configured install from looking armed while it cannot block.
     */
    var isProtectionArmed: Boolean
        get() = prefs.getBoolean(KEY_PROTECTION_ARMED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROTECTION_ARMED, value).apply()

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

    var focusStreakDays: Int
        get() = prefs.getInt(KEY_FOCUS_STREAK_DAYS, 0)
        set(value) = prefs.edit().putInt(KEY_FOCUS_STREAK_DAYS, value.coerceAtLeast(0)).apply()

    var lastCleanDate: String
        get() = prefs.getString(KEY_LAST_CLEAN_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_CLEAN_DATE, value).apply()

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
        val startElapsed = strictModeStartElapsed
        if (startElapsed > 0L) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - startElapsed
            if (elapsed >= 0L && elapsed < strictModeDurationMs) return true
        }
        return System.currentTimeMillis() < blockEndTime
    }

    val isStrictModeEnabled: Boolean
        get() = isStrictBlockActive()

    var blockDurationMinutes: Int
        get() = prefs.getInt(KEY_BLOCK_DURATION, 60)
        set(value) = prefs.edit().putInt(KEY_BLOCK_DURATION, value).apply()

    var strictModeStartElapsed: Long
        get() = prefs.getLong(KEY_STRICT_START_ELAPSED, 0L)
        set(value) = prefs.edit().putLong(KEY_STRICT_START_ELAPSED, value).apply()

    var strictModeDurationMs: Long
        get() = prefs.getLong(KEY_STRICT_DURATION_MS, 0L)
        set(value) = prefs.edit().putLong(KEY_STRICT_DURATION_MS, value).apply()

    var isStrictModeExitProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_STRICT_EXIT_PROTECTION, false)
        set(value) = prefs.edit().putBoolean(KEY_STRICT_EXIT_PROTECTION, value).apply()

    var hasAcceptedAccessibilityDisclosure: Boolean
        get() = prefs.getBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED, value).apply()

    // ── Accountability Lock ──

    val isAccountabilityLockActive: Boolean
        get() {
            val untilElapsed = prefs.getLong(KEY_ACCOUNTABILITY_LOCK_UNTIL_ELAPSED, 0L)
            if (untilElapsed <= 0L) return false
            val active = SystemClock.elapsedRealtime() < untilElapsed
            if (!active) clearAccountabilityLock()
            return active
        }

    /**
     * Stores a salted SHA-256 verifier only. The PIN is intentionally not
     * recoverable from the app; give it to an accountability partner.
     */
    fun startAccountabilityLock(pin: String, durationMs: Long): Boolean {
        if (!pin.matches(Regex("\\d{6,12}")) || durationMs <= 0L) return false

        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val verifier = hashPin(salt, pin)
        prefs.edit()
            .putString(KEY_ACCOUNTABILITY_PIN_SALT, Base64.getEncoder().encodeToString(salt))
            .putString(KEY_ACCOUNTABILITY_PIN_HASH, Base64.getEncoder().encodeToString(verifier))
            .putLong(
                KEY_ACCOUNTABILITY_LOCK_UNTIL_ELAPSED,
                SystemClock.elapsedRealtime() + durationMs
            )
            .apply()
        return true
    }

    fun verifyAccountabilityPin(pin: String): Boolean {
        val encodedSalt = prefs.getString(KEY_ACCOUNTABILITY_PIN_SALT, null) ?: return false
        val encodedVerifier = prefs.getString(KEY_ACCOUNTABILITY_PIN_HASH, null) ?: return false
        return runCatching {
            val expected = Base64.getDecoder().decode(encodedVerifier)
            val actual = hashPin(Base64.getDecoder().decode(encodedSalt), pin)
            MessageDigest.isEqual(expected, actual)
        }.getOrDefault(false)
    }

    fun clearAccountabilityLock() {
        prefs.edit()
            .remove(KEY_ACCOUNTABILITY_PIN_SALT)
            .remove(KEY_ACCOUNTABILITY_PIN_HASH)
            .remove(KEY_ACCOUNTABILITY_LOCK_UNTIL_ELAPSED)
            .apply()
    }

    /** Stops a zombie "armed" state if a required permission disappears. */
    fun pauseProtectionForMissingPermission() {
        isProtectionArmed = false
        isServiceEnabled = false
        isGuardActive = false
        blockEndTime = 0L
        strictModeStartElapsed = 0L
        strictModeDurationMs = 0L
        isStrictModeExitProtectionEnabled = false
        clearFocusSessionMetadata()
        clearAccountabilityLock()
    }

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

    var studyBlocks: List<StudyBlockSchedule>
        get() = readStudyBlocks()
        set(value) = writeStudyBlocks(value)

    /**
     * Check if current time is within the user-defined schedule.
     * Supports overnight schedules (e.g., 10PM → 6AM).
     * Returns true if schedule is enabled AND current time/day falls within the window.
     */
    fun isWithinSchedule(): Boolean {
        if (!isScheduleEnabled) return false

        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val hasActiveStudyBlock = studyBlocks.any { block ->
            block.enabled && isBlockActiveNow(block, currentDay, currentMinutes)
        }
        if (prefs.contains(KEY_STUDY_BLOCKS_JSON)) return hasActiveStudyBlock
        if (hasActiveStudyBlock) return true
        val startMinutes = scheduleStartHour * 60 + scheduleStartMinute
        val endMinutes = scheduleEndHour * 60 + scheduleEndMinute

        return if (startMinutes == endMinutes) {
            currentDay in scheduleDays
        } else if (startMinutes < endMinutes) {
            // Normal schedule: e.g., 9:00 AM (540) → 9:00 PM (1260)
            currentDay in scheduleDays && currentMinutes in startMinutes until endMinutes
        } else {
            // Overnight schedule: e.g., 10:00 PM (1320) → 6:00 AM (360)
            val previousDay = if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY else currentDay - 1
            (currentDay in scheduleDays && currentMinutes >= startMinutes) ||
                (previousDay in scheduleDays && currentMinutes < endMinutes)
        }
    }

    /**
     * Combined check: is the guard active right now?
     * True if EITHER schedule is active OR strict mode timer is running.
     */
    fun isGuardActiveNow(): Boolean {
        return isProtectionArmed &&
            (isGuardActive || isWithinSchedule() || isStrictBlockActive())
    }

    fun getActiveStudyBlock(): StudyBlockSchedule? {
        if (!isScheduleEnabled) return null

        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK)
        val currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        return studyBlocks.firstOrNull { block ->
            block.enabled && isBlockActiveNow(block, currentDay, currentMinutes)
        }
    }

    private fun isBlockActiveNow(
        block: StudyBlockSchedule,
        currentDay: Int,
        currentMinutes: Int
    ): Boolean {
        val startMinutes = block.startHour * 60 + block.startMinute
        val endMinutes = block.endHour * 60 + block.endMinute

        return if (startMinutes == endMinutes) {
            currentDay in block.days
        } else if (startMinutes < endMinutes) {
            currentDay in block.days && currentMinutes in startMinutes until endMinutes
        } else {
            val previousDay = if (currentDay == Calendar.SUNDAY) Calendar.SATURDAY else currentDay - 1
            (currentDay in block.days && currentMinutes >= startMinutes) ||
                (previousDay in block.days && currentMinutes < endMinutes)
        }
    }

    private fun readStudyBlocks(): List<StudyBlockSchedule> {
        val raw = prefs.getString(KEY_STUDY_BLOCKS_JSON, null)
        val parsed = raw?.let(::parseStudyBlocks).orEmpty()
        return parsed.ifEmpty { listOf(legacyScheduleBlock()) }
    }

    private fun writeStudyBlocks(blocks: List<StudyBlockSchedule>) {
        val sanitized = blocks.mapIndexed { index, block ->
            block.copy(
                id = block.id.ifBlank { UUID.randomUUID().toString() },
                title = block.title.trim().ifBlank { "Study Block ${index + 1}" },
                startHour = block.startHour.coerceIn(0, 23),
                startMinute = block.startMinute.coerceIn(0, 59),
                endHour = block.endHour.coerceIn(0, 23),
                endMinute = block.endMinute.coerceIn(0, 59),
                days = block.days.filter { it in 1..7 }.toSet().ifEmpty { defaultScheduleDays() }
            )
        }

        val editor = prefs.edit()
            .putString(KEY_STUDY_BLOCKS_JSON, studyBlocksToJson(sanitized))

        sanitized.firstOrNull()?.let { first ->
            editor
                .putInt(KEY_SCHEDULE_START_HOUR, first.startHour)
                .putInt(KEY_SCHEDULE_START_MINUTE, first.startMinute)
                .putInt(KEY_SCHEDULE_END_HOUR, first.endHour)
                .putInt(KEY_SCHEDULE_END_MINUTE, first.endMinute)
                .putString(KEY_SCHEDULE_DAYS, first.days.joinToString(","))
        }

        editor.apply()
    }

    private fun parseStudyBlocks(raw: String): List<StudyBlockSchedule> {
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val daysArray = item.optJSONArray("days") ?: JSONArray()
                    val days = buildSet {
                        for (dayIndex in 0 until daysArray.length()) {
                            val day = daysArray.optInt(dayIndex, -1)
                            if (day in 1..7) add(day)
                        }
                    }

                    add(
                        StudyBlockSchedule(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            title = item.optString("title").ifBlank { "Study Block ${index + 1}" },
                            startHour = item.optInt("startHour", 9).coerceIn(0, 23),
                            startMinute = item.optInt("startMinute", 0).coerceIn(0, 59),
                            endHour = item.optInt("endHour", 12).coerceIn(0, 23),
                            endMinute = item.optInt("endMinute", 0).coerceIn(0, 59),
                            days = days.ifEmpty { defaultScheduleDays() },
                            enabled = item.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun studyBlocksToJson(blocks: List<StudyBlockSchedule>): String {
        val array = JSONArray()
        blocks.forEach { block ->
            array.put(
                JSONObject()
                    .put("id", block.id)
                    .put("title", block.title)
                    .put("startHour", block.startHour)
                    .put("startMinute", block.startMinute)
                    .put("endHour", block.endHour)
                    .put("endMinute", block.endMinute)
                    .put("days", JSONArray(block.days.sorted()))
                    .put("enabled", block.enabled)
            )
        }
        return array.toString()
    }

    private fun legacyScheduleBlock(): StudyBlockSchedule {
        return StudyBlockSchedule(
            id = "legacy-main",
            title = "Study Block 1",
            startHour = scheduleStartHour,
            startMinute = scheduleStartMinute,
            endHour = scheduleEndHour,
            endMinute = scheduleEndMinute,
            days = scheduleDays.ifEmpty { defaultScheduleDays() },
            enabled = true
        )
    }

    private fun defaultScheduleDays(): Set<Int> = setOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY
    )

    val canBlockCriticalActions: Boolean
        get() {
            val isFocusActive = isGuardActiveNow()
            return isStrictModeEnabled &&
                isStrictModeExitProtectionEnabled &&
                isFocusActive &&
                hasAcceptedAccessibilityDisclosure
        }

    val canBlockSettingsBypassNow: Boolean
        get() = isProtectionArmed &&
            (isStrictBlockActive() || isAccountabilityLockActive) &&
            isStrictModeExitProtectionEnabled &&
            hasAcceptedAccessibilityDisclosure

    fun allowPermissionSetupWindow(durationMs: Long = PERMISSION_SETUP_WINDOW_MS) {
        prefs.edit()
            .putLong(
                KEY_PERMISSION_SETUP_ALLOWED_UNTIL_ELAPSED,
                SystemClock.elapsedRealtime() + durationMs
            )
            .apply()
    }

    fun isPermissionSetupWindowActive(): Boolean {
        val allowedUntil = prefs.getLong(KEY_PERMISSION_SETUP_ALLOWED_UNTIL_ELAPSED, 0L)
        if (allowedUntil <= 0L) return false

        val isActive = SystemClock.elapsedRealtime() <= allowedUntil
        if (!isActive) {
            prefs.edit().remove(KEY_PERMISSION_SETUP_ALLOWED_UNTIL_ELAPSED).apply()
        }
        return isActive
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

    var youtubeProductiveChannels: Set<String>
        get() = prefs.getStringSet(
            KEY_YOUTUBE_PRODUCTIVE_CHANNELS,
            DEFAULT_PRODUCTIVE_YOUTUBE_CHANNELS
        )?.sanitizeChannelSet() ?: DEFAULT_PRODUCTIVE_YOUTUBE_CHANNELS
        set(value) = prefs.edit()
            .putStringSet(KEY_YOUTUBE_PRODUCTIVE_CHANNELS, value.sanitizeChannelSet())
            .apply()

    var youtubeDistractingChannels: Set<String>
        get() = prefs.getStringSet(
            KEY_YOUTUBE_DISTRACTING_CHANNELS,
            DEFAULT_DISTRACTING_YOUTUBE_CHANNELS
        )?.sanitizeChannelSet() ?: DEFAULT_DISTRACTING_YOUTUBE_CHANNELS
        set(value) = prefs.edit()
            .putStringSet(KEY_YOUTUBE_DISTRACTING_CHANNELS, value.sanitizeChannelSet())
            .apply()

    // ── Anti-Bypass ──

    var studyFlowLastSyncedAt: Long
        get() = prefs.getLong(KEY_STUDYFLOW_LAST_SYNCED_AT, 0L)
        private set(value) = prefs.edit().putLong(KEY_STUDYFLOW_LAST_SYNCED_AT, value).apply()

    var bypassPenalty: Int
        get() = prefs.getInt(KEY_BYPASS_PENALTY, 0)
        set(value) = prefs.edit().putInt(KEY_BYPASS_PENALTY, value).apply()

    var timeoutExploitCount: Int
        get() = prefs.getInt(KEY_TIMEOUT_EXPLOIT_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TIMEOUT_EXPLOIT_COUNT, value).apply()

    var lastPermissionCheckFailed: Boolean
        get() = prefs.getBoolean(KEY_PERM_CHECK_FAILED, false)
        set(value) = prefs.edit().putBoolean(KEY_PERM_CHECK_FAILED, value).apply()

    var bypassEventCount: Int
        get() = prefs.getInt(KEY_BYPASS_EVENT_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_BYPASS_EVENT_COUNT, value.coerceAtLeast(0)).apply()

    var lastBypassEventType: String
        get() = prefs.getString(KEY_LAST_BYPASS_EVENT_TYPE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_BYPASS_EVENT_TYPE, value).apply()

    var lastBypassEventAt: Long
        get() = prefs.getLong(KEY_LAST_BYPASS_EVENT_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_BYPASS_EVENT_AT, value).apply()

    // ── Helper Methods ──

    fun recordDistractionRecovery(
        sourceKey: String,
        sourceTitle: String? = null,
        pyqSubject: String? = null,
        pyqWasCorrect: Boolean? = null
    ) {
        val nextStudyItem = getStudyFlowDaySnapshot()?.pendingItems?.firstOrNull()
        val editor = prefs.edit()
            .putString(KEY_RECOVERY_SOURCE_KEY, sourceKey)
            .putString(
                KEY_RECOVERY_SOURCE_TITLE,
                sourceTitle?.takeIf { it.isNotBlank() } ?: recoveryTitleForSource(sourceKey)
            )
            .putLong(KEY_RECOVERY_OCCURRED_AT, System.currentTimeMillis())

        if (pyqSubject.isNullOrBlank()) {
            editor.remove(KEY_RECOVERY_PYQ_SUBJECT)
        } else {
            editor.putString(KEY_RECOVERY_PYQ_SUBJECT, pyqSubject)
        }

        if (pyqWasCorrect == null) {
            editor
                .putBoolean(KEY_RECOVERY_HAS_PYQ_RESULT, false)
                .remove(KEY_RECOVERY_PYQ_CORRECT)
        } else {
            editor
                .putBoolean(KEY_RECOVERY_HAS_PYQ_RESULT, true)
                .putBoolean(KEY_RECOVERY_PYQ_CORRECT, pyqWasCorrect)
        }

        if (nextStudyItem == null) {
            editor
                .remove(KEY_RECOVERY_TASK_TITLE)
                .remove(KEY_RECOVERY_TASK_SUBJECT)
        } else {
            editor.putString(KEY_RECOVERY_TASK_TITLE, nextStudyItem.title)
            if (nextStudyItem.subject.isNullOrBlank()) {
                editor.remove(KEY_RECOVERY_TASK_SUBJECT)
            } else {
                editor.putString(KEY_RECOVERY_TASK_SUBJECT, nextStudyItem.subject)
            }
        }

        editor.apply()
    }

    fun getDistractionRecoverySnapshot(
        maxAgeMs: Long = RECOVERY_PROMPT_MAX_AGE_MS
    ): DistractionRecoverySnapshot? {
        val occurredAt = prefs.getLong(KEY_RECOVERY_OCCURRED_AT, 0L)
        val now = System.currentTimeMillis()
        if (occurredAt <= 0L || occurredAt > now + 60_000L || now - occurredAt > maxAgeMs) {
            return null
        }

        val sourceKey = prefs.getString(KEY_RECOVERY_SOURCE_KEY, null)?.takeIf { it.isNotBlank() }
            ?: return null
        val hasPyqResult = prefs.getBoolean(KEY_RECOVERY_HAS_PYQ_RESULT, false)

        return DistractionRecoverySnapshot(
            sourceKey = sourceKey,
            sourceTitle = prefs.getString(KEY_RECOVERY_SOURCE_TITLE, null)
                ?.takeIf { it.isNotBlank() }
                ?: recoveryTitleForSource(sourceKey),
            occurredAtMs = occurredAt,
            pyqSubject = prefs.getString(KEY_RECOVERY_PYQ_SUBJECT, null)
                ?.takeIf { it.isNotBlank() },
            pyqWasCorrect = if (hasPyqResult) {
                prefs.getBoolean(KEY_RECOVERY_PYQ_CORRECT, false)
            } else {
                null
            },
            studyTaskTitle = prefs.getString(KEY_RECOVERY_TASK_TITLE, null)
                ?.takeIf { it.isNotBlank() },
            studyTaskSubject = prefs.getString(KEY_RECOVERY_TASK_SUBJECT, null)
                ?.takeIf { it.isNotBlank() }
        )
    }

    fun clearDistractionRecoverySnapshot() {
        prefs.edit()
            .remove(KEY_RECOVERY_SOURCE_KEY)
            .remove(KEY_RECOVERY_SOURCE_TITLE)
            .remove(KEY_RECOVERY_OCCURRED_AT)
            .remove(KEY_RECOVERY_PYQ_SUBJECT)
            .remove(KEY_RECOVERY_HAS_PYQ_RESULT)
            .remove(KEY_RECOVERY_PYQ_CORRECT)
            .remove(KEY_RECOVERY_TASK_TITLE)
            .remove(KEY_RECOVERY_TASK_SUBJECT)
            .apply()
    }

    fun getTodayKey(): String = dateFormat.format(Date())

    /**
     * Processes the previous tracked day once. A clean day means one or fewer
     * distraction attempts.
     */
    fun updateStreakOnNewDay() {
        val today = getTodayKey()
        if (lastCleanDate == today) return

        val previousDate = lastAttemptDate
            .takeIf { it.isNotBlank() && it != today }
            ?: lastCleanDate.takeIf { it.isNotBlank() && it != today }

        if (!previousDate.isNullOrBlank()) {
            val fallbackAttempts = if (previousDate == lastAttemptDate) dailyAttemptCount else 0
            val previousAttempts = prefs.getInt(attemptCountKey(previousDate), fallbackAttempts)
            focusStreakDays = if (previousAttempts <= 1) {
                focusStreakDays + 1
            } else {
                0
            }
        }

        lastCleanDate = today
    }

    private fun attemptCountKey(date: String): String = "attempts_$date"

    fun getDaysUntilExam(): Int {
        val date = examDate
        if (date == 0L) return -1
        val diff = date - System.currentTimeMillis()
        return (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
    }

    // Backward compat alias
    fun getDaysUntilNeet(): Int = getDaysUntilExam()

    fun migrateFocusedSurfaceDefaultsIfNeeded() {
        if (!prefs.getBoolean(KEY_FOCUSED_SURFACE_MIGRATION_DONE, false)) {
            val storedBlacklist = prefs.getStringSet(KEY_BLACKLIST, null)?.toSet()
            val storedSurfaces = prefs.getStringSet(KEY_BLOCKED_CONTENT_SURFACES, null)?.toSet()

            if (storedBlacklist == LEGACY_DEFAULT_BLACKLIST && storedSurfaces.isNullOrEmpty()) {
                prefs.edit()
                    .putStringSet(KEY_BLACKLIST, DEFAULT_BLACKLIST)
                    .putStringSet(KEY_BLOCKED_CONTENT_SURFACES, DEFAULT_BLOCKED_CONTENT_SURFACES)
                    .putBoolean(KEY_FOCUSED_SURFACE_MIGRATION_DONE, true)
                    .apply()
            } else {
                prefs.edit()
                    .putBoolean(KEY_FOCUSED_SURFACE_MIGRATION_DONE, true)
                    .apply()
            }
        }

        migrateInstagramToReelsOnlyDefaultIfNeeded()
    }

    private fun migrateInstagramToReelsOnlyDefaultIfNeeded() {
        if (prefs.getBoolean(KEY_INSTAGRAM_REELS_ONLY_MIGRATION_DONE, false)) return

        val storedBlacklist = prefs.getStringSet(KEY_BLACKLIST, null)?.toSet()
        val storedSurfaces = prefs.getStringSet(KEY_BLOCKED_CONTENT_SURFACES, null)?.toSet()
        val shouldRemoveInstagramFullBlock = storedBlacklist == null ||
            storedBlacklist.contains(INSTAGRAM_PACKAGE) ||
            storedBlacklist.contains(INSTAGRAM_LITE_PACKAGE) ||
            storedBlacklist == LEGACY_DEFAULT_BLACKLIST ||
            storedBlacklist == OLD_DEFAULT_BLACKLIST_WITH_INSTAGRAM

        val editor = prefs.edit()
        if (shouldRemoveInstagramFullBlock) {
            val updatedBlacklist = (storedBlacklist ?: OLD_DEFAULT_BLACKLIST_WITH_INSTAGRAM)
                .minus(INSTAGRAM_PACKAGE)
                .minus(INSTAGRAM_LITE_PACKAGE)
            val updatedSurfaces = (storedSurfaces ?: DEFAULT_BLOCKED_CONTENT_SURFACES)
                .plus(SURFACE_INSTAGRAM_REELS)

            editor
                .putStringSet(KEY_BLACKLIST, updatedBlacklist)
                .putStringSet(KEY_BLOCKED_CONTENT_SURFACES, updatedSurfaces)
        }

        editor
            .putBoolean(KEY_INSTAGRAM_REELS_ONLY_MIGRATION_DONE, true)
            .apply()
    }

    /**
     * Increments daily attempt count with automatic daily reset.
     * Returns the new attempt number.
     */
    fun incrementDailyAttempt(): Int {
        val today = getTodayKey()
        if (lastAttemptDate != today) {
            updateStreakOnNewDay()
            // New day — reset
            dailyAttemptCount = 0
            currentEscalationLevel = 0
            lastAttemptDate = today
        }
        val newCount = dailyAttemptCount + 1
        dailyAttemptCount = newCount
        prefs.edit().putInt(attemptCountKey(today), newCount).apply()
        totalBlocksEver = totalBlocksEver + 1
        return newCount
    }

    fun storeStudyFlowDaySnapshot(snapshot: StudyFlowDaySnapshot) {
        prefs.edit()
            .putString(KEY_STUDYFLOW_DAY_SNAPSHOT_JSON, snapshot.toJsonString())
            .putLong(KEY_STUDYFLOW_LAST_SYNCED_AT, snapshot.syncedAtEpochMs)
            .apply()
    }

    fun getStudyFlowDaySnapshot(): StudyFlowDaySnapshot? {
        val snapshot = StudyFlowDaySnapshot.fromJsonString(
            prefs.getString(KEY_STUDYFLOW_DAY_SNAPSHOT_JSON, null)
        ) ?: return null

        return snapshot.takeIf { it.isFreshFor(getTodayKey()) }
    }

    private fun Set<String>.sanitizeChannelSet(): Set<String> {
        return asSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .toCollection(linkedSetOf())
    }

    private fun hashPin(salt: ByteArray, pin: String): ByteArray {
        return MessageDigest.getInstance("SHA-256")
            .digest(salt + pin.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_PROTECTION_ARMED = "protection_armed"
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
        private const val KEY_FOCUS_STREAK_DAYS = "focus_streak_days"
        private const val KEY_LAST_CLEAN_DATE = "last_clean_date"
        private const val KEY_FOCUS_SESSION_START_TIME = "focus_session_start_time"
        private const val KEY_FOCUS_SESSION_MODE = "focus_session_mode"
        private const val KEY_FOCUS_SESSION_XP_AWARDED = "focus_session_xp_awarded"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_USER_PROFILE_CREATED_AT = "user_profile_created_at"
        private const val KEY_BLOCK_END_TIME = "block_end_time"
        private const val KEY_BLOCK_DURATION = "block_duration"
        private const val KEY_STRICT_START_ELAPSED = "strict_start_elapsed"
        private const val KEY_STRICT_DURATION_MS = "strict_duration_ms"
        private const val KEY_STRICT_EXIT_PROTECTION = "strict_exit_protection"
        private const val KEY_ACCESSIBILITY_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted"
        private const val KEY_ACCOUNTABILITY_PIN_SALT = "accountability_pin_salt"
        private const val KEY_ACCOUNTABILITY_PIN_HASH = "accountability_pin_hash"
        private const val KEY_ACCOUNTABILITY_LOCK_UNTIL_ELAPSED = "accountability_lock_until_elapsed"
        private const val KEY_PERMISSION_SETUP_ALLOWED_UNTIL_ELAPSED = "permission_setup_allowed_until_elapsed"
        private const val KEY_BLACKLIST = "blacklisted_apps"
        private const val KEY_WHITELIST = "whitelisted_apps"
        private const val KEY_BLOCKED_CONTENT_SURFACES = "blocked_content_surfaces"
        private const val KEY_YOUTUBE_PRODUCTIVE_CHANNELS = "youtube_productive_channels"
        private const val KEY_YOUTUBE_DISTRACTING_CHANNELS = "youtube_distracting_channels"
        private const val KEY_STUDYFLOW_DAY_SNAPSHOT_JSON = "studyflow_day_snapshot_json"
        private const val KEY_STUDYFLOW_LAST_SYNCED_AT = "studyflow_last_synced_at"
        private const val KEY_BYPASS_PENALTY = "bypass_penalty"
        private const val KEY_TIMEOUT_EXPLOIT_COUNT = "timeout_exploit_count"
        private const val KEY_PERM_CHECK_FAILED = "perm_check_failed"
        private const val KEY_BYPASS_EVENT_COUNT = "bypass_event_count"
        private const val KEY_LAST_BYPASS_EVENT_TYPE = "last_bypass_event_type"
        private const val KEY_LAST_BYPASS_EVENT_AT = "last_bypass_event_at"
        private const val KEY_FOCUSED_SURFACE_MIGRATION_DONE = "focused_surface_migration_done"
        private const val KEY_INSTAGRAM_REELS_ONLY_MIGRATION_DONE = "instagram_reels_only_migration_done"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_START_HOUR = "schedule_start_hour"
        private const val KEY_SCHEDULE_START_MINUTE = "schedule_start_minute"
        private const val KEY_SCHEDULE_END_HOUR = "schedule_end_hour"
        private const val KEY_SCHEDULE_END_MINUTE = "schedule_end_minute"
        private const val KEY_SCHEDULE_DAYS = "schedule_days"
        private const val KEY_STUDY_BLOCKS_JSON = "study_blocks_json"
        private const val KEY_RECOVERY_SOURCE_KEY = "recovery_source_key"
        private const val KEY_RECOVERY_SOURCE_TITLE = "recovery_source_title"
        private const val KEY_RECOVERY_OCCURRED_AT = "recovery_occurred_at"
        private const val KEY_RECOVERY_PYQ_SUBJECT = "recovery_pyq_subject"
        private const val KEY_RECOVERY_HAS_PYQ_RESULT = "recovery_has_pyq_result"
        private const val KEY_RECOVERY_PYQ_CORRECT = "recovery_pyq_correct"
        private const val KEY_RECOVERY_TASK_TITLE = "recovery_task_title"
        private const val KEY_RECOVERY_TASK_SUBJECT = "recovery_task_subject"
        private const val RECOVERY_PROMPT_MAX_AGE_MS = 6 * 60 * 60 * 1000L
        private const val PERMISSION_SETUP_WINDOW_MS = 2 * 60 * 1000L

        const val INSTAGRAM_PACKAGE = "com.instagram.android"
        const val INSTAGRAM_LITE_PACKAGE = "com.instagram.lite"

        val DEFAULT_BLACKLIST = setOf(
            "com.zhiliaoapp.musically",      // TikTok
            "com.snapchat.android",
            "com.twitter.android",
            "com.facebook.katana",
        )

        val OLD_DEFAULT_BLACKLIST_WITH_INSTAGRAM = setOf(
            INSTAGRAM_PACKAGE,
            INSTAGRAM_LITE_PACKAGE,
            "com.zhiliaoapp.musically",
            "com.snapchat.android",
            "com.twitter.android",
            "com.facebook.katana",
        )

        val LEGACY_DEFAULT_BLACKLIST = setOf(
            "com.instagram.android",
            "com.instagram.lite",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
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

        const val SURFACE_INSTAGRAM_REELS = "instagram_reels"
        const val SURFACE_YOUTUBE_SHORTS = "youtube_shorts"

        val DEFAULT_BLOCKED_CONTENT_SURFACES = setOf(
            SURFACE_INSTAGRAM_REELS,
            SURFACE_YOUTUBE_SHORTS
        )

        val DEFAULT_PRODUCTIVE_YOUTUBE_CHANNELS = setOf(
            "Physics Wallah",
            "Khan Academy",
            "Mohit Tyagi",
            "Neso Academy",
            "Unacademy NEET"
        )

        val DEFAULT_DISTRACTING_YOUTUBE_CHANNELS = setOf(
            "MrBeast",
            "CarryMinati",
            "Triggered Insaan"
        )

        fun recoveryTitleForSource(sourceKey: String): String {
            return when {
                sourceKey.contains(SURFACE_INSTAGRAM_REELS) -> "Instagram Reels"
                sourceKey.contains(SURFACE_YOUTUBE_SHORTS) -> "YouTube Shorts"
                sourceKey == INSTAGRAM_PACKAGE -> "Instagram"
                sourceKey == INSTAGRAM_LITE_PACKAGE -> "Instagram Lite"
                sourceKey == "com.google.android.youtube" -> "YouTube"
                sourceKey == "com.zhiliaoapp.musically" -> "TikTok"
                sourceKey == "com.snapchat.android" -> "Snapchat"
                sourceKey == "com.twitter.android" -> "X"
                sourceKey == "com.facebook.katana" -> "Facebook"
                else -> sourceKey.substringBefore(":").substringAfterLast(".")
                    .replace('_', ' ')
                    .replace('-', ' ')
                    .ifBlank { "Distracting app" }
            }
        }
    }
}
