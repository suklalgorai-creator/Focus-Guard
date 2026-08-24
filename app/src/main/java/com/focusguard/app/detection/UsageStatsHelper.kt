package com.focusguard.app.detection

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.blocking.DetectionSource
import kotlinx.coroutines.*

/**
 * FALLBACK DETECTION LAYER — UsageStatsManager polling.
 *
 * Polls every 500ms to detect foreground app changes as a backup
 * in case AccessibilityService misses an event or is delayed.
 *
 * This is less responsive than AccessibilityService (~500ms delay)
 * but provides redundancy for reliability.
 */
class UsageStatsHelper(private val context: Context) {

    private val usageStatsManager: UsageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private var pollingJob: Job? = null
    private var lastForegroundPackage: String? = null
    private var onBlockedAppDetected: ((String) -> Unit)? = null

    /**
     * Start polling for foreground app changes.
     * @param callback Called when a blacklisted app is detected in foreground
     */
    fun startPolling(
        scope: CoroutineScope,
        callback: (String) -> Unit
    ) {
        onBlockedAppDetected = callback
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.Default) {
            Log.d(TAG, "UsageStats polling started (500ms interval)")
            while (isActive) {
                try {
                    checkForegroundApp()
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        Log.d(TAG, "UsageStats polling stopped")
    }

    private fun checkForegroundApp() {
        val currentTime = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            currentTime - 5000, // Last 5 seconds
            currentTime
        )

        if (stats.isNullOrEmpty()) return

        // Find the most recently used app
        val foregroundApp = stats
            .filter { it.lastTimeUsed > 0 }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName ?: return

        // Skip if same as last check
        val blockingManager = FocusGuardApp.instance.blockingManager
        if (foregroundApp == lastForegroundPackage) return

        // Skip our own app
        if (foregroundApp == "com.focusguard.app") return

        val prefs = FocusGuardApp.instance.prefs

        if (!blockingManager.canBlockPackageNow(foregroundApp, DetectionSource.USAGE_STATS)) {
            lastForegroundPackage = null
            return
        }

        lastForegroundPackage = foregroundApp

        // Check whitelist first.
        if (prefs.whitelistedApps.contains(foregroundApp)) return

        // Check blacklist.
        if (blockingManager.isBlockedPackage(foregroundApp)) {
            // Only trigger if AccessibilityService hasn't already caught it
            run {
                Log.w(TAG, "🚫 FALLBACK DETECTION: $foregroundApp detected via UsageStats")
                onBlockedAppDetected?.invoke(foregroundApp)
            }
        }
    }

    /**
     * Check if UsageStats permission is granted.
     */
    fun hasPermission(): Boolean {
        return try {
            val currentTime = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                currentTime - 60000,
                currentTime
            )
            stats != null && stats.isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // ── Usage Analytics Query Methods ──

    /**
     * Get per-app usage stats for today.
     * Returns sorted list (most used first), minimum 1 minute usage.
     */
    fun getDailyUsageStats(): List<com.focusguard.app.domain.AppUsageData> {
        val prefs = FocusGuardApp.instance.prefs
        val pm = context.packageManager
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        return try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, startOfDay, now
            )
            stats?.filter { it.totalTimeInForeground > 60_000 } // Min 1 minute
                ?.map { stat ->
                    val appName = try {
                        pm.getApplicationLabel(
                            pm.getApplicationInfo(stat.packageName, 0)
                        ).toString()
                    } catch (e: Exception) { stat.packageName }

                    com.focusguard.app.domain.AppUsageData(
                        packageName = stat.packageName,
                        appName = appName,
                        usageTimeMs = stat.totalTimeInForeground,
                        isBlacklisted = isDistractingPackage(stat.packageName, prefs.blacklistedApps)
                    )
                }
                ?.sortedByDescending { it.usageTimeMs }
                ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get daily usage: ${e.message}")
            emptyList()
        }
    }

    /**
     * Get per-app usage for last 7 days, grouped by day key (yyyy-MM-dd).
     */
    fun getWeeklyUsageStats(): Map<String, List<com.focusguard.app.domain.AppUsageData>> {
        val prefs = FocusGuardApp.instance.prefs
        val pm = context.packageManager
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val result = mutableMapOf<String, List<com.focusguard.app.domain.AppUsageData>>()

        val calendar = java.util.Calendar.getInstance()

        for (daysAgo in 0..6) {
            calendar.timeInMillis = System.currentTimeMillis()
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -daysAgo)
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            val dayStart = calendar.timeInMillis
            val dayEnd = dayStart + 24 * 60 * 60 * 1000L
            val dayKey = dateFormat.format(java.util.Date(dayStart))

            try {
                val stats = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd.coerceAtMost(System.currentTimeMillis())
                )
                val dayStats = stats?.filter { it.totalTimeInForeground > 60_000 }
                    ?.map { stat ->
                        val appName = try {
                            pm.getApplicationLabel(
                                pm.getApplicationInfo(stat.packageName, 0)
                            ).toString()
                        } catch (e: Exception) { stat.packageName }

                        com.focusguard.app.domain.AppUsageData(
                            packageName = stat.packageName,
                            appName = appName,
                            usageTimeMs = stat.totalTimeInForeground,
                            isBlacklisted = isDistractingPackage(stat.packageName, prefs.blacklistedApps)
                        )
                    }
                    ?.sortedByDescending { it.usageTimeMs }
                    ?: emptyList()

                result[dayKey] = dayStats
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get stats for $dayKey: ${e.message}")
                result[dayKey] = emptyList()
            }
        }

        return result
    }

    /**
     * Get usage for blacklisted apps only (today).
     */
    fun getBlacklistedUsage(): List<com.focusguard.app.domain.AppUsageData> {
        return getDailyUsageStats().filter { it.isBlacklisted }
    }

    private fun isDistractingPackage(packageName: String, blacklistedApps: Set<String>): Boolean {
        return packageName in blacklistedApps
    }

    companion object {
        private const val TAG = "UsageStatsHelper"
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
