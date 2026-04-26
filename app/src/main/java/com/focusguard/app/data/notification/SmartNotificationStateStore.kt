package com.focusguard.app.data.notification

import android.content.Context
import com.focusguard.app.domain.notification.NotificationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmartNotificationStateStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun canSend(
        type: NotificationType,
        message: String,
        nowMs: Long = System.currentTimeMillis(),
        cooldownMs: Long = DEFAULT_COOLDOWN_MS
    ): Boolean {
        resetDailyCountIfNeeded(nowMs)

        if (prefs.getInt(KEY_DAILY_COUNT, 0) >= MAX_DAILY_NOTIFICATIONS) return false
        if (message == prefs.getString(KEY_LAST_MESSAGE, null)) return false

        val lastSentAt = prefs.getLong(KEY_LAST_SENT_AT, 0L)
        if (lastSentAt > 0 && nowMs - lastSentAt < cooldownMs) return false

        val lastType = prefs.getString(KEY_LAST_TYPE, null)
        val lastTypeSentAt = prefs.getLong(KEY_LAST_TYPE_SENT_AT, 0L)
        if (lastType == type.name && lastTypeSentAt > 0 && nowMs - lastTypeSentAt < TYPE_COOLDOWN_MS) {
            return false
        }

        return true
    }

    fun recordSent(
        type: NotificationType,
        message: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        resetDailyCountIfNeeded(nowMs)
        prefs.edit()
            .putInt(KEY_DAILY_COUNT, prefs.getInt(KEY_DAILY_COUNT, 0) + 1)
            .putLong(KEY_LAST_SENT_AT, nowMs)
            .putString(KEY_LAST_TYPE, type.name)
            .putLong(KEY_LAST_TYPE_SENT_AT, nowMs)
            .putString(KEY_LAST_MESSAGE, message)
            .apply()
    }

    fun getLastMessage(): String? {
        return prefs.getString(KEY_LAST_MESSAGE, null)
    }

    private fun resetDailyCountIfNeeded(nowMs: Long) {
        val today = dateFormat.format(Date(nowMs))
        if (prefs.getString(KEY_DAY, null) != today) {
            prefs.edit()
                .putString(KEY_DAY, today)
                .putInt(KEY_DAILY_COUNT, 0)
                .apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "smart_notification_state"
        private const val KEY_DAY = "day"
        private const val KEY_DAILY_COUNT = "daily_count"
        private const val KEY_LAST_SENT_AT = "last_sent_at"
        private const val KEY_LAST_TYPE = "last_type"
        private const val KEY_LAST_TYPE_SENT_AT = "last_type_sent_at"
        private const val KEY_LAST_MESSAGE = "last_message"

        private const val MAX_DAILY_NOTIFICATIONS = 3
        private const val DEFAULT_COOLDOWN_MS = 4L * 60L * 60L * 1000L
        private const val TYPE_COOLDOWN_MS = 8L * 60L * 60L * 1000L
    }
}
