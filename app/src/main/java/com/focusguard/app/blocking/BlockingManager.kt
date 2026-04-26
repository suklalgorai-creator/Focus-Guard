package com.focusguard.app.blocking

import android.content.Context
import android.provider.Settings
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.persistence.FocusGuardPrefs

class BlockingManager(
    context: Context,
    private val prefs: FocusGuardPrefs
) {
    private val appContext = context.applicationContext
    private val permissionMonitor = PermissionMonitor(appContext)

    @Volatile
    var state: BlockingState = BlockingState.IDLE
        private set

    @Volatile
    var currentBlockedPackage: String? = null
        private set

    private var lastBlockedPackage: String? = null
    private var lastBlockedAtMs: Long = 0L

    @Synchronized
    fun canBlockNow(source: DetectionSource = DetectionSource.ACCESSIBILITY): Boolean {
        val baseAllowed =
            prefs.hasAcceptedAccessibilityDisclosure &&
                prefs.isServiceEnabled &&
                prefs.isGuardActiveNow()

        if (!baseAllowed) {
            if (state != BlockingState.FRICTION_ACTIVE && state != BlockingState.BLOCKING) {
                state = BlockingState.IDLE
            }
            return false
        }

        val canDetect = when (source) {
            DetectionSource.ACCESSIBILITY -> permissionMonitor.isAccessibilityEnabled()
            DetectionSource.USAGE_STATS -> permissionMonitor.isUsageStatsPermitted()
        }

        val canTakeBlockingAction =
            permissionMonitor.isAccessibilityEnabled() || Settings.canDrawOverlays(appContext)

        if (!canDetect || !canTakeBlockingAction) return false

        if (state == BlockingState.IDLE) {
            state = BlockingState.MONITORING
        }
        return true
    }

    fun isBlockedPackage(packageName: String): Boolean {
        return packageName != appContext.packageName &&
            packageName !in prefs.whitelistedApps &&
            packageName in prefs.blacklistedApps
    }

    @Synchronized
    fun tryStartBlocking(
        packageName: String,
        source: DetectionSource
    ): Boolean {
        if (!canBlockNow(source)) return false
        if (!isBlockedPackage(packageName)) return false

        val now = SystemClock.elapsedRealtime()
        if (
            (state == BlockingState.BLOCKING || state == BlockingState.FRICTION_ACTIVE) &&
            currentBlockedPackage == packageName
        ) {
            Log.d(TAG, "Duplicate block UI suppressed for active package: $packageName")
            return false
        }

        if (
            state == BlockingState.COOLDOWN &&
            packageName == lastBlockedPackage &&
            now - lastBlockedAtMs < BLOCK_COOLDOWN_MS
        ) {
            Log.d(TAG, "Cooldown observed, but block decision continues for foreground app: $packageName")
        }

        currentBlockedPackage = packageName
        lastBlockedPackage = packageName
        lastBlockedAtMs = now
        state = BlockingState.BLOCKING
        Log.d(TAG, "Blocking started: package=$packageName source=$source")
        return true
    }

    @Synchronized
    fun markFrictionActive(packageName: String) {
        if (currentBlockedPackage == packageName && state == BlockingState.BLOCKING) {
            state = BlockingState.FRICTION_ACTIVE
        }
    }

    @Synchronized
    fun shouldEndCurrentBlockFor(observedPackage: String): Boolean {
        val activePackage = currentBlockedPackage ?: return false
        return (state == BlockingState.BLOCKING || state == BlockingState.FRICTION_ACTIVE) &&
            observedPackage != activePackage
    }

    @Synchronized
    fun finishBlocking(packageName: String? = currentBlockedPackage) {
        if (packageName != null && currentBlockedPackage != null && packageName != currentBlockedPackage) {
            return
        }

        lastBlockedPackage = currentBlockedPackage ?: packageName
        lastBlockedAtMs = SystemClock.elapsedRealtime()
        currentBlockedPackage = null
        state = BlockingState.COOLDOWN
        Log.d(TAG, "Blocking finished; entering UI cooldown")
    }

    @Synchronized
    fun reset() {
        currentBlockedPackage = null
        state = if (prefs.hasAcceptedAccessibilityDisclosure && prefs.isGuardActiveNow()) {
            BlockingState.MONITORING
        } else {
            BlockingState.IDLE
        }
    }

    companion object {
        private const val TAG = "BlockingManager"
        private const val BLOCK_COOLDOWN_MS = 1_500L
    }
}
