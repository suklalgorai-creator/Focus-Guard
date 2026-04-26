package com.focusguard.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.MainActivity
import com.focusguard.app.R
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.blocking.DetectionSource
import com.focusguard.app.detection.AppDetectorService
import com.focusguard.app.detection.ForegroundAppDetector
import com.focusguard.app.overlay.OverlayManager
import kotlinx.coroutines.*

/**
 * Persistent foreground service that:
 * 1. Keeps the app alive in background (prevents system kill)
 * 2. Runs UsageStats fallback polling for secondary detection
 * 3. Monitors permission state for anti-bypass
 * 4. Schedules watchdog alarms for self-recovery
 *
 * Uses START_STICKY to auto-restart if killed by system.
 */
class GuardForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var foregroundAppDetector: ForegroundAppDetector
    private lateinit var fallbackOverlayManager: OverlayManager
    private lateinit var permissionMonitor: PermissionMonitor
    private var fallbackBlockJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "GuardForegroundService created")

        if (!FocusGuardApp.instance.prefs.hasAcceptedAccessibilityDisclosure) {
            Log.d(TAG, "Disclosure not accepted; stopping guard service")
            stopSelf()
            return
        }

        foregroundAppDetector = ForegroundAppDetector(this)
        fallbackOverlayManager = OverlayManager(this).also { it.preInitialize() }
        permissionMonitor = PermissionMonitor(this)

        // Start as foreground service immediately
        startForeground(NOTIFICATION_ID, buildNotification())

        // Schedule watchdog
        if (FocusGuardApp.instance.prefs.isGuardActiveNow()) {
            WatchdogReceiver.scheduleWatchdog(this)
        }

        // Start fallback detection polling — actually trigger blocking if accessibility missed it
        foregroundAppDetector.startUsageStatsPolling(serviceScope) { packageName ->
            Log.w(TAG, "Fallback detection triggered for: $packageName")
            serviceScope.launch(Dispatchers.Main) {
                val detector = AppDetectorService.instance
                if (detector != null) {
                    // AccessibilityService is alive but missed this — force-home as safe fallback
                    detector.onUsageStatsBlockedAppDetected(packageName)
                } else {
                    // AccessibilityService is dead — we can't show overlay without it
                    // but we CAN send a high-priority notification
                    blockWithServiceFallback(packageName)
                }
            }
        }

        // Periodic permission check
        serviceScope.launch {
            while (isActive) {
                permissionMonitor.checkAllPermissions()
                delay(30_000) // Check every 30 seconds
            }
        }

        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand — ensuring foreground")

        if (!FocusGuardApp.instance.prefs.hasAcceptedAccessibilityDisclosure) {
            Log.d(TAG, "Disclosure not accepted; ignoring service start")
            stopSelf()
            return START_NOT_STICKY
        }

        // Re-show notification in case it was cleared
        startForeground(NOTIFICATION_ID, buildNotification())

        // Re-schedule watchdog only while protection can actually be active.
        if (FocusGuardApp.instance.prefs.isGuardActiveNow()) {
            WatchdogReceiver.scheduleWatchdog(this)
        }

        return START_STICKY // Auto-restart if killed
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.w(TAG, "GuardForegroundService DESTROYED — this should NOT happen!")
        instance = null
        serviceScope.cancel()
        fallbackBlockJob?.cancel()
        if (::foregroundAppDetector.isInitialized) {
            foregroundAppDetector.stop()
        }
        if (::fallbackOverlayManager.isInitialized) {
            fallbackOverlayManager.destroy()
        }

        if (FocusGuardApp.instance.prefs.hasAcceptedAccessibilityDisclosure &&
            FocusGuardApp.instance.prefs.isGuardActiveNow()) {
            val restartIntent = Intent(this, GuardForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(restartIntent)
                } else {
                    startService(restartIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to restart foreground service: ${e.message}", e)
            }
        }

        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.w(TAG, "Task removed — scheduling restart")
        if (FocusGuardApp.instance.prefs.isGuardActiveNow()) {
            WatchdogReceiver.scheduleWatchdog(this)
        }
        super.onTaskRemoved(rootIntent)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prefs = FocusGuardApp.instance.prefs
        val blockedToday = prefs.dailyAttemptCount
        val daysUntilExam = prefs.getDaysUntilExam()

        val contentText = buildString {
            append("Blocked $blockedToday distraction(s) today")
            if (daysUntilExam >= 0) {
                val examName = prefs.targetExam.uppercase()
                append(" • $examName in $daysUntilExam days")
            }
        }

        return NotificationCompat.Builder(this, FocusGuardApp.CHANNEL_SERVICE)
            .setContentTitle("Focus Guard Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun blockWithServiceFallback(packageName: String) {
        val blockingManager = FocusGuardApp.instance.blockingManager
        if (!blockingManager.tryStartBlocking(packageName, DetectionSource.USAGE_STATS)) return
        FocusGuardApp.instance.trackingManager.onBlockedApp(packageName)

        fallbackBlockJob?.cancel()
        val overlayShown = fallbackOverlayManager.show()
        if (!overlayShown) {
            Log.e(TAG, "Service fallback overlay failed; showing interruption notification")
            showFallbackInterruption(packageName)
            blockingManager.finishBlocking(packageName)
            return
        }

        blockingManager.markFrictionActive(packageName)
        fallbackBlockJob = serviceScope.launch(Dispatchers.Main) {
            fallbackOverlayManager.resetUI()
            fallbackOverlayManager.getPrimaryMessage()?.apply {
                text = "Blocked by Focus Guard"
                setTextColor(0xFFFF4D6D.toInt())
                textSize = 24f
            }
            fallbackOverlayManager.getSecondaryMessage()?.text =
                "Accessibility is unavailable, so Focus Guard is using fallback blocking.\n\n" +
                    "Re-enable Accessibility for the full PYQ challenge flow."
            fallbackOverlayManager.getAttemptInfo()?.text = "Fallback block active"

            delay(FALLBACK_BLOCK_MS)
            fallbackOverlayManager.hide()
            blockingManager.finishBlocking(packageName)
        }
    }

    /**
     * Last-resort visible interruption when AccessibilityService is dead and
     * overlay cannot be shown. This uses notification delivery instead of
     * background startActivity(), which Android 10+ can block.
     */
    private fun showFallbackInterruption(packageName: String): Boolean {
        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) { packageName }

        val intent = Intent(this, FallbackBlockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(FallbackBlockActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(FallbackBlockActivity.EXTRA_APP_LABEL, appLabel)
        }
        val fullScreenIntent = PendingIntent.getActivity(
            this,
            FALLBACK_ALERT_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, FocusGuardApp.CHANNEL_ALERTS)
            .setContentTitle("$appLabel detected")
            .setContentText("Focus Guard needs your attention to keep this distraction blocked.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(FALLBACK_ALERT_ID, notification)
            Log.w(TAG, "Fallback interruption notification posted for $packageName")
            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot show fallback alert: ${e.message}")
        }
        return false
    }

    companion object {
        private const val TAG = "GuardService"
        private const val NOTIFICATION_ID = 1001
        private const val FALLBACK_ALERT_ID = 2002
        private const val FALLBACK_BLOCK_MS = 45_000L
        var instance: GuardForegroundService? = null
            private set
    }
}
