package com.focusguard.app.detection

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.antibypass.SettingsBlocker
import com.focusguard.app.blocking.DetectionSource
import com.focusguard.app.friction.FrictionOrchestrator
import com.focusguard.app.overlay.OverlayManager

/**
 * Primary detection layer backed by AccessibilityService.
 *
 * It handles:
 * 1. Full app blocking for blacklisted packages
 * 2. Settings / uninstall anti-bypass monitoring
 * 3. Focused surface blocking such as Instagram Reels
 */
class AppDetectorService : AccessibilityService() {

    private lateinit var overlayManager: OverlayManager
    private lateinit var orchestrator: FrictionOrchestrator
    private lateinit var settingsBlocker: SettingsBlocker
    private lateinit var surfaceDetector: BlockedSurfaceDetector
    private lateinit var focusedSurfaceBlocker: FocusedSurfaceBlocker
    private lateinit var foregroundAppDetector: ForegroundAppDetector

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService connected - FocusGuard active")

        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 50
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        } ?: return

        overlayManager = OverlayManager(this).also { it.preInitialize() }
        orchestrator = FrictionOrchestrator(this, overlayManager) { packageName ->
            FocusGuardApp.instance.blockingManager.finishBlocking(packageName)
        }
        settingsBlocker = SettingsBlocker(this, overlayManager)
        surfaceDetector = BlockedSurfaceDetector(FocusGuardApp.instance.prefs)
        focusedSurfaceBlocker = FocusedSurfaceBlocker(overlayManager)
        foregroundAppDetector = ForegroundAppDetector(this)

        instance = this
        Log.d(TAG, "All components initialized and ready")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val safeEvent = event ?: return
        val packageName = foregroundAppDetector.packageFrom(safeEvent) ?: return

        if (!FocusGuardApp.instance.prefs.hasAcceptedAccessibilityDisclosure) return

        when (safeEvent.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> handleWindowChange(packageName)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> handleContentChange(packageName, safeEvent)
        }
    }

    private fun handleContentChange(
        packageName: String,
        event: AccessibilityEvent
    ) {
        val prefs = FocusGuardApp.instance.prefs

        if (isSettingsOrInstallerPackage(packageName) && prefs.canBlockCriticalActions) {
            settingsBlocker.onSettingsContentChanged(event)
            return
        }

        if (!FocusGuardApp.instance.blockingManager.canBlockNow(DetectionSource.ACCESSIBILITY) ||
            FocusGuardApp.instance.blockingManager.currentBlockedPackage != null) {
            return
        }

        val surfaceMatch = surfaceDetector.detect(event, rootInActiveWindow) ?: return
        Log.w(TAG, "Focused surface blocked: ${surfaceMatch.blockKey}")
        focusedSurfaceBlocker.onSurfaceDetected(surfaceMatch)
    }

    private fun handleWindowChange(packageName: String) {
        val prefs = FocusGuardApp.instance.prefs
        FocusGuardApp.instance.trackingManager.onForegroundAppChanged(packageName)

        val blockingManager = FocusGuardApp.instance.blockingManager

        if (isSettingsOrInstallerPackage(packageName)) {
            if (prefs.canBlockCriticalActions) {
                if (packageName.contains("packageinstaller") || packageName.contains("uninstaller")) {
                    Log.w(TAG, "Uninstall bypass attempt detected")
                    forceHome()
                    settingsBlocker.onBypassAttempt()
                } else {
                    settingsBlocker.onSettingsOpened()
                }
            }
            return
        }

        if (prefs.whitelistedApps.contains(packageName)) {
            if (blockingManager.shouldEndCurrentBlockFor(packageName)) onBlockedAppClosed()
            return
        }

        if (!blockingManager.canBlockNow(DetectionSource.ACCESSIBILITY)) {
            if (blockingManager.currentBlockedPackage != null) onBlockedAppClosed()
            return
        }

        if (prefs.blacklistedApps.contains(packageName)) {
            startBlocking(packageName, DetectionSource.ACCESSIBILITY)
            return
        }

        if (blockingManager.shouldEndCurrentBlockFor(packageName)) {
            onBlockedAppClosed()
        }
    }

    private fun onBlockedAppClosed() {
        Log.d(TAG, "User left blocked app - hiding overlay")
        FocusGuardApp.instance.blockingManager.finishBlocking()
        orchestrator.onBlockedAppClosed()
        overlayManager.hide()
    }

    fun onUsageStatsBlockedAppDetected(packageName: String) {
        startBlocking(packageName, DetectionSource.USAGE_STATS)
    }

    private fun startBlocking(packageName: String, source: DetectionSource) {
        val blockingManager = FocusGuardApp.instance.blockingManager
        if (!blockingManager.tryStartBlocking(packageName, source)) return

        Log.w(TAG, "Blocked app detected via $source: $packageName")
        FocusGuardApp.instance.trackingManager.onBlockedApp(packageName)
        val overlayShown = overlayManager.show()
        if (!overlayShown) {
            Log.e(TAG, "Overlay failed; forcing home and resetting blocking state")
            forceHome()
            blockingManager.finishBlocking(packageName)
            overlayManager.hide()
            return
        }

        blockingManager.markFrictionActive(packageName)
        orchestrator.onBlockedAppDetected(packageName)
    }

    fun forceHome() {
        Log.d(TAG, "Force home")
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun forceBack() {
        Log.d(TAG, "Force back")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        Log.w(TAG, "AccessibilityService destroyed")
        if (::focusedSurfaceBlocker.isInitialized) focusedSurfaceBlocker.destroy()
        if (::settingsBlocker.isInitialized) settingsBlocker.destroy()
        if (::overlayManager.isInitialized) overlayManager.destroy()
        FocusGuardApp.instance.trackingManager.flushCurrentSession()
        FocusGuardApp.instance.blockingManager.reset()
        instance = null
        super.onDestroy()
    }

    private fun isSettingsOrInstallerPackage(packageName: String): Boolean {
        // Exact matches for known system packages
        if (packageName in SETTINGS_PACKAGES) return true

        // Prefix matches for installer/uninstaller patterns
        // These are always system components, safe to block
        if (packageName.startsWith("com.android.packageinstaller") ||
            packageName.startsWith("com.google.android.packageinstaller") ||
            packageName.startsWith("com.android.vending")) {
            return true
        }

        // OEM-specific settings/manager packages (prefix match)
        val oemPrefixes = listOf(
            "com.coloros.",      // Oppo/ColorOS
            "com.heytap.",      // Oppo/Realme
            "com.oppo.",        // Oppo legacy
            "com.miui.",        // Xiaomi
            "com.samsung.android.sm",   // Samsung Device Care
            "com.samsung.android.lool"  // Samsung Device Maintenance
        )
        if (oemPrefixes.any { packageName.startsWith(it) }) return true

        return false
    }

    companion object {
        private const val TAG = "AppDetector"

        var instance: AppDetectorService? = null
            private set

        /** Exact package names to treat as Settings/installer */
        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.vending",           // Play Store
        )
    }
}
