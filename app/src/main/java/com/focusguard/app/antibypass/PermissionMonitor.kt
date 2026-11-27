package com.focusguard.app.antibypass

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
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
 * Uses [PermissionHealthEvaluator] as the single source of truth:
 * - Accessibility Service: required for core blocking
 * - Overlay & Usage Access: optional (degraded mode without them)
 *
 * Never wipes Accountability Lock or armed configuration when a permission is revoked.
 */
class PermissionMonitor(private val context: Context) {

    fun getPermissionSnapshot(): PermissionSnapshot {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryIgnored = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        val notificationsOk = NotificationManagerCompat.from(context).areNotificationsEnabled()

        return PermissionSnapshot(
            accessibilityEnabled = isAccessibilityEnabled(),
            overlayEnabled = isOverlayPermitted(),
            usageStatsEnabled = isUsageStatsPermitted(),
            notificationsEnabled = notificationsOk,
            batteryOptimizationIgnored = batteryIgnored
        )
    }

    fun getHealthSummary(): PermissionHealthSummary {
        return PermissionHealthEvaluator.evaluate(getPermissionSnapshot())
    }

    /**
     * Check permissions periodically. Preserves Accountability Lock and armed state
     * when permissions are revoked, and automatically resumes when restored.
     */
    fun checkAllPermissions() {
        val summary = getHealthSummary()
        val snapshot = summary.snapshot
        val prefs = FocusGuardApp.instance.prefs

        if (!summary.canEnforceCoreBlocking && prefs.isProtectionArmed) {
            Log.w(
                TAG,
                "Core permission missing: accessibility=${snapshot.accessibilityEnabled}, " +
                    "overlay=${snapshot.overlayEnabled}, usageStats=${snapshot.usageStatsEnabled}"
            )

            if (!prefs.lastPermissionCheckFailed) {
                prefs.lastPermissionCheckFailed = true
                FocusGuardApp.instance.antiBypassManager.recordPermissionLoss(
                    accessibilityOk = snapshot.accessibilityEnabled,
                    overlayOk = snapshot.overlayEnabled,
                    usageStatsOk = snapshot.usageStatsEnabled
                )
                showPermissionAlert(summary)
            }
            prefs.pauseProtectionForMissingPermission()
        } else if (summary.canEnforceCoreBlocking) {
            if (prefs.lastPermissionCheckFailed || prefs.isEnforcementPausedForMissingPermission) {
                prefs.lastPermissionCheckFailed = false
                prefs.restoreEnforcementAfterPermissionRecovery()
                NotificationManagerCompat.from(context).cancel(ALERT_NOTIFICATION_ID)
                Log.i(TAG, "Core permissions restored; enforcement resumed")
            }
        }
    }

    fun hasAllRequiredPermissions(): Boolean {
        return isAccessibilityEnabled()
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

    private fun showPermissionAlert(summary: PermissionHealthSummary) {
        val missing = (summary.missingRequired + summary.missingOptional).map { it.label }

        val notification = NotificationCompat.Builder(context, FocusGuardApp.CHANNEL_ALERTS)
            .setContentTitle("Focus Guard enforcement paused")
            .setContentText(summary.affectedFeaturesSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${summary.affectedFeaturesSummary}\n\n" +
                    "Missing:\n" +
                    missing.joinToString("\n") { "• $it" } +
                    "\n\nYour lock settings are preserved. Re-enable Accessibility in Focus Guard to resume."
            ))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "POST_NOTIFICATIONS permission not granted, cannot show alert")
                return
            }
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
