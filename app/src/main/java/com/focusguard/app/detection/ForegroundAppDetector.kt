package com.focusguard.app.detection

import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.SystemClock
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
    private var lastBlockedDetectionAtMs = 0L

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
        lastBlockedDetectionAtMs = 0L
    }

    private fun pollOnce(onBlockedAppDetected: (String) -> Unit) {
        val blockingManager = FocusGuardApp.instance.blockingManager
        val foregroundPackage = queryForegroundPackage() ?: return
        if (foregroundPackage == context.packageName) return

        if (!blockingManager.isBlockedPackage(foregroundPackage)) {
            lastForegroundPackage = foregroundPackage
            lastBlockedDetectionAtMs = 0L
            return
        }

        if (!blockingManager.canBlockPackageNow(foregroundPackage, DetectionSource.USAGE_STATS)) {
            lastForegroundPackage = null
            lastBlockedDetectionAtMs = 0L
            return
        }

        val now = SystemClock.elapsedRealtime()
        val activeSamePackage = blockingManager.currentBlockedPackage == foregroundPackage
        if (foregroundPackage == lastForegroundPackage && activeSamePackage) return
        if (foregroundPackage == lastForegroundPackage && now - lastBlockedDetectionAtMs < REBLOCK_INTERVAL_MS) return

        lastForegroundPackage = foregroundPackage
        lastBlockedDetectionAtMs = now

        Log.w(TAG, "UsageStats fallback detected blocked app: $foregroundPackage")
        onBlockedAppDetected(foregroundPackage)
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
        private const val REBLOCK_INTERVAL_MS = 2_000L
    }
}
