package com.focusguard.app.data.usage

import android.util.Log
import com.focusguard.app.domain.usage.AppUsageSession

class ForegroundSessionManager(
    private val isDistractingPackage: (String) -> Boolean,
    private val onSessionClosed: suspend (AppUsageSession) -> Unit
) {
    private var currentPackage: String? = null
    private var currentStartTime: Long = 0L

    suspend fun onForegroundAppChanged(packageName: String, timestamp: Long = System.currentTimeMillis()) {
        val activePackage = currentPackage
        if (activePackage == packageName) return

        closeCurrent(timestamp)
        currentPackage = packageName
        currentStartTime = timestamp
        Log.d(TAG, "Usage session started: $packageName at $timestamp")
    }

    suspend fun stop(timestamp: Long = System.currentTimeMillis()) {
        closeCurrent(timestamp)
        currentPackage = null
        currentStartTime = 0L
    }

    private suspend fun closeCurrent(endTime: Long) {
        val packageName = currentPackage ?: return
        val startTime = currentStartTime
        val duration = endTime - startTime
        if (duration >= MIN_SESSION_MS) {
            Log.d(TAG, "Usage session ended: $packageName duration=${duration}ms")
            onSessionClosed(
                AppUsageSession(
                    packageName = packageName,
                    startTime = startTime,
                    endTime = endTime,
                    durationMs = duration,
                    isDistracting = isDistractingPackage(packageName)
                )
            )
        }
    }

    companion object {
        private const val TAG = "ForegroundSessionMgr"
        private const val MIN_SESSION_MS = 1_000L
    }
}
