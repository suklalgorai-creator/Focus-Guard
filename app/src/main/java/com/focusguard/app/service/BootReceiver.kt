package com.focusguard.app.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserManager
import android.util.Log
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.persistence.FocusGuardPrefs

/**
 * Restores Focus Guard state and foreground service after device reboot (post-unlock).
 * Listens for BOOT_COMPLETED and QUICKBOOT_POWERON.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                if (!isUserUnlocked(context)) {
                    Log.w(TAG, "User storage still locked; skipping boot restore until unlock")
                    return
                }
                Log.d(TAG, "Boot completed — reconciling deadlines and checking service startup")
                startGuardService(context)
            }
        }
    }

    private fun isUserUnlocked(context: Context): Boolean {
        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        return userManager?.isUserUnlocked ?: true
    }

    private fun startGuardService(context: Context) {
        val prefs = FocusGuardPrefs(context)
        prefs.reconcileDeadlinesAfterReboot()

        if (!prefs.hasAcceptedAccessibilityDisclosure ||
            !prefs.isProtectionArmed ||
            !prefs.isServiceEnabled ||
            !prefs.isGuardActiveNow()
        ) {
            Log.d(TAG, "Guard inactive or setup incomplete; skipping boot start")
            return
        }

        val pm = PermissionMonitor(context)
        if (!pm.isAccessibilityEnabled() && !pm.isUsageStatsPermitted()) {
            Log.w(TAG, "Missing core permissions after boot; pausing enforcement without clearing lock")
            prefs.pauseProtectionForMissingPermission()
            return
        }

        if (GuardForegroundService.instance != null) {
            Log.d(TAG, "GuardForegroundService already running; skipping duplicate boot start")
            WatchdogReceiver.scheduleWatchdog(context)
            return
        }

        val serviceIntent = Intent(context, GuardForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Log.d(TAG, "GuardForegroundService started after boot")
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException
            ) {
                Log.e(TAG, "Foreground service start not allowed after boot: ${e.message}", e)
            } else {
                Log.e(TAG, "Failed to start service after boot: ${e.message}", e)
            }
        }

        WatchdogReceiver.scheduleWatchdog(context)
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
