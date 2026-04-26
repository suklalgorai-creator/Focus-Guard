package com.focusguard.app.detection

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.blocking.DetectionSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ForegroundAppDetector(
    private val context: Context
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    private var pollingJob: Job? = null
    private var lastForegroundPackage: String? = null

    fun packageFrom(event: AccessibilityEvent): String? {
        return event.packageName?.toString()
            ?.takeUnless { it == context.packageName }
    }

    fun isForegroundWindowEvent(event: AccessibilityEvent): Boolean {
        return event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED
    }

    fun startUsageStatsPolling(
        scope: CoroutineScope,
        onBlockedAppDetected: (String) -> Unit
    ) {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.Default) {
            Log.d(TAG, "UsageStats foreground detector started (${POLL_INTERVAL_MS}ms)")
            while (isActive) {
                try {
                    pollOnce(onBlockedAppDetected)
                } catch (e: Exception) {
                    Log.e(TAG, "UsageStats detection error: ${e.message}", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        lastForegroundPackage = null
    }

    private fun pollOnce(onBlockedAppDetected: (String) -> Unit) {
        val blockingManager = FocusGuardApp.instance.blockingManager
        if (!blockingManager.canBlockNow(DetectionSource.USAGE_STATS)) {
            lastForegroundPackage = null
            return
        }

        val foregroundPackage = queryForegroundPackage() ?: return
        if (foregroundPackage == context.packageName) return
        if (foregroundPackage == lastForegroundPackage) return

        lastForegroundPackage = foregroundPackage

        if (blockingManager.isBlockedPackage(foregroundPackage)) {
            Log.w(TAG, "UsageStats fallback detected blocked app: $foregroundPackage")
            onBlockedAppDetected(foregroundPackage)
        }
    }

    private fun queryForegroundPackage(): String? {
        val now = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - LOOKBACK_MS,
            now
        )

        return stats
            ?.asSequence()
            ?.filter { it.lastTimeUsed > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    companion object {
        private const val TAG = "ForegroundDetector"
        private const val LOOKBACK_MS = 10_000L
        private const val POLL_INTERVAL_MS = 2_000L
    }
}
