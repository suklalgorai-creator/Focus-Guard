package com.focusguard.app.data.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.focusguard.app.domain.usage.AppUsageSession
import com.focusguard.app.persistence.FocusGuardPrefs
import java.util.Calendar

class UsageStatsReconciler(
    context: Context,
    private val prefs: FocusGuardPrefs,
    private val usageRepository: UsageRepository
) {
    private val appContext = context.applicationContext
    private val usageStatsManager =
        appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    @Volatile
    private var lastReconciledAtMs: Long = 0L

    @Volatile
    private var lastReconciledDayKey: String = ""

    suspend fun reconcileToday(force: Boolean = false) {
        if (!prefs.isUsageTrackingEnabled) return

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val now = System.currentTimeMillis()
        val todayKey = usageRepository.dateKey(now)

        if (!force &&
            lastReconciledDayKey == todayKey &&
            (now - lastReconciledAtMs) < RECONCILE_CACHE_TTL_MS
        ) {
            return
        }

        reconcileRange(startOfDay, now)
        lastReconciledDayKey = todayKey
        lastReconciledAtMs = now
    }

    suspend fun reconcileRange(startTime: Long, endTime: Long) {
        if (endTime <= startTime) return

        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var activePackage: String? = null
            var activeStart: Long = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val packageName = event.packageName ?: continue
                if (packageName == appContext.packageName) continue

                when {
                    event.isForegroundEvent() -> {
                        if (activePackage != null && activePackage != packageName) {
                            recordSession(activePackage, activeStart, event.timeStamp)
                        }
                        activePackage = packageName
                        activeStart = event.timeStamp
                    }
                    event.isBackgroundEvent() && activePackage == packageName -> {
                        recordSession(packageName, activeStart, event.timeStamp)
                        activePackage = null
                        activeStart = 0L
                    }
                }
            }

            if (activePackage != null) {
                recordSession(activePackage, activeStart, endTime)
            }
        } catch (e: Exception) {
            Log.e(TAG, "UsageStats reconciliation failed: ${e.message}", e)
        }
    }

    private suspend fun recordSession(packageName: String?, startTime: Long, endTime: Long) {
        if (packageName.isNullOrBlank()) return
        val duration = endTime - startTime
        if (duration < MIN_RECONCILED_SESSION_MS) return

        usageRepository.recordReconciledSession(
            AppUsageSession(
                packageName = packageName,
                startTime = startTime,
                endTime = endTime,
                durationMs = duration,
                isDistracting = packageName in prefs.blacklistedApps
            )
        )
    }

    @Suppress("DEPRECATION")
    private fun UsageEvents.Event.isForegroundEvent(): Boolean {
        return eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                eventType == UsageEvents.Event.ACTIVITY_RESUMED)
    }

    @Suppress("DEPRECATION")
    private fun UsageEvents.Event.isBackgroundEvent(): Boolean {
        return eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                eventType == UsageEvents.Event.ACTIVITY_PAUSED)
    }

    companion object {
        private const val TAG = "UsageStatsReconciler"
        private const val MIN_RECONCILED_SESSION_MS = 1_000L
        private const val RECONCILE_CACHE_TTL_MS = 15_000L
    }
}
