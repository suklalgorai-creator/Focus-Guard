package com.focusguard.app.data.usage

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.persistence.FocusGuardPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class TrackingManager(
    context: Context,
    private val prefs: FocusGuardPrefs,
    private val usageRepository: UsageRepository
) {
    private val appPackage = context.packageName
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionManager = ForegroundSessionManager(
        isDistractingPackage = { packageName -> isDistractingPackage(packageName) },
        onSessionClosed = { session -> usageRepository.recordSession(session) }
    )
    private val sessionMutex = Mutex()

    private var lastBlockedPackage: String? = null
    private var lastBlockedAtElapsedMs: Long = 0L

    fun onForegroundAppChanged(packageName: String) {
        scope.launch {
            sessionMutex.withLock {
                if (packageName == appPackage) {
                    Log.d(TAG, "FocusGuard foreground detected; closing previous usage session")
                    sessionManager.stop()
                    return@withLock
                }

                if (!shouldTrackNow()) {
                    Log.d(TAG, "Usage tracking inactive; closing current session")
                    sessionManager.stop()
                    return@withLock
                }

                Log.d(TAG, "Foreground app changed: $packageName")
                sessionManager.onForegroundAppChanged(packageName)
            }
        }
    }

    @Synchronized
    fun onBlockedApp(packageName: String) {
        if (!isDistractingPackage(packageName)) return

        val now = SystemClock.elapsedRealtime()
        if (packageName == lastBlockedPackage && now - lastBlockedAtElapsedMs < BLOCK_EVENT_COOLDOWN_MS) {
            return
        }

        lastBlockedPackage = packageName
        lastBlockedAtElapsedMs = now
        Log.d(TAG, "Blocked session recorded trigger: $packageName")

        scope.launch {
            usageRepository.recordBlockedSession(packageName)
        }
    }

    fun flushCurrentSession() {
        scope.launch {
            sessionMutex.withLock {
                Log.d(TAG, "Flushing active usage session")
                sessionManager.stop()
            }
        }
    }

    private fun shouldTrackNow(): Boolean {
        return prefs.hasAcceptedAccessibilityDisclosure &&
            prefs.isServiceEnabled &&
            (prefs.isGuardActiveNow() || prefs.isUsageTrackingEnabled)
    }

    private fun isDistractingPackage(packageName: String): Boolean {
        return packageName in prefs.blacklistedApps
    }

    companion object {
        private const val TAG = "TrackingManager"
        private const val BLOCK_EVENT_COOLDOWN_MS = 2_000L
    }
}
