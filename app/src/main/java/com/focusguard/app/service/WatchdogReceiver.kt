package com.focusguard.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.persistence.FocusGuardPrefs

/**
 * Watchdog alarm receiver.
 * Fires every 5 minutes to check if the foreground service is still alive.
 * If not, restarts it.
 *
 * This is the last line of defense against the system killing our service.
 */
class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Watchdog fired — checking service health")

        val prefs = FocusGuardPrefs(context)
        if (!prefs.hasAcceptedAccessibilityDisclosure || !prefs.isGuardActiveNow()) {
            Log.d(TAG, "Disclosure not accepted; watchdog will not restart service")
            return
        }

        if (GuardForegroundService.instance == null) {
            Log.w(TAG, "⚠ Service is dead! Restarting...")
            runCatching {
                FocusGuardApp.instance.antiBypassManager.recordServiceRestart("watchdog_service_dead")
            }
            val serviceIntent = Intent(context, GuardForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                Log.d(TAG, "Service restarted by watchdog")
            } catch (e: Exception) {
                Log.e(TAG, "Watchdog failed to restart service: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Service is alive ✓")
        }

        // Re-schedule next watchdog
        scheduleWatchdog(context)
    }

    companion object {
        private const val TAG = "WatchdogReceiver"
        private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val REQUEST_CODE = 9999

        fun scheduleWatchdog(context: Context) {
            if (!FocusGuardPrefs(context).isGuardActiveNow()) return

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent = Intent(context, WatchdogReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerAt = SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                Log.d(TAG, "Watchdog scheduled for ${WATCHDOG_INTERVAL_MS / 1000}s from now")
            } catch (e: SecurityException) {
                // Fallback to inexact alarm if exact alarm permission not granted
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
                Log.d(TAG, "Watchdog scheduled (inexact) for ${WATCHDOG_INTERVAL_MS / 1000}s")
            }
        }
    }
}
