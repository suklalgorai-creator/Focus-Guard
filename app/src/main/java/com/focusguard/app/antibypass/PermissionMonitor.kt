package com.focusguard.app.antibypass

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focusguard.app.FocusGuardApp

/**
 * Monitors critical permissions and alerts when they're disabled.
 *
 * Checks:
 * 1. Accessibility Service enabled
 * 2. Overlay (SYSTEM_ALERT_WINDOW) permission
 * 3. Usage Stats access
 *
 * If any permission is missing:
 * - Shows a persistent high-priority notification
 * - Increases next friction session difficulty
 */
class PermissionMonitor(private val context: Context) {

    /**
     * Check all required permissions. Called periodically by foreground service.
     */
    fun checkAllPermissions() {
        val accessibilityOk = isAccessibilityEnabled()
        val overlayOk = isOverlayPermitted()
        val usageStatsOk = isUsageStatsPermitted()

        val allOk = accessibilityOk && overlayOk && usageStatsOk

        val prefs = FocusGuardApp.instance.prefs

        if (!allOk && prefs.isProtectionArmed) {
            Log.w(TAG, "Permission check FAILED: " +
                    "accessibility=$accessibilityOk, overlay=$overlayOk, usageStats=$usageStatsOk")

            if (!prefs.lastPermissionCheckFailed) {
                // First failure — apply penalty and notify
                prefs.lastPermissionCheckFailed = true
                FocusGuardApp.instance.antiBypassManager.recordPermissionLoss(
                    accessibilityOk = accessibilityOk,
                    overlayOk = overlayOk,
                    usageStatsOk = usageStatsOk
                )
                showPermissionAlert(accessibilityOk, overlayOk, usageStatsOk)
            }
            // Do not leave a session marked active when no visible block can
            // be delivered. The setup gate will require all access again.
            prefs.pauseProtectionForMissingPermission()
        } else {
            prefs.lastPermissionCheckFailed = false
        }
    }

    fun hasAllRequiredPermissions(): Boolean {
        return isAccessibilityEnabled() &&
            isOverlayPermitted() &&
            isUsageStatsPermitted()
    }

    fun isAccessibilityEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC
        )
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    fun isOverlayPermitted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun isUsageStatsPermitted(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun showPermissionAlert(
        accessibilityOk: Boolean,
        overlayOk: Boolean,
        usageStatsOk: Boolean
    ) {
        val missing = mutableListOf<String>()
        if (!accessibilityOk) missing.add("Accessibility Service")
        if (!overlayOk) missing.add("Display Over Other Apps")
        if (!usageStatsOk) missing.add("Usage Data Access")

        val notification = NotificationCompat.Builder(context, FocusGuardApp.CHANNEL_ALERTS)
            .setContentTitle("Focus Guard protection disabled")
            .setContentText("Missing: ${missing.joinToString(", ")}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "The following permissions have been disabled:\n" +
                missing.joinToString("\n") { "• $it" } +
                "\n\nNext focus challenge difficulty has been increased by +1 level.\n" +
                "Re-enable permissions to restore normal operation."
            ))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot show notification: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PermissionMonitor"
        private const val ALERT_NOTIFICATION_ID = 2001
    }
}
