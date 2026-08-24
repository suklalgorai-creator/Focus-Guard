package com.focusguard.app.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.focusguard.app.persistence.FocusGuardPrefs

class ServiceHealthWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {

    override fun doWork(): Result {
        val prefs = FocusGuardPrefs(context)
        if (!prefs.hasAcceptedAccessibilityDisclosure || !prefs.isGuardActiveNow()) {
            return Result.success()
        }

        if (GuardForegroundService.instance == null) {
            Log.w(TAG, "WorkManager watchdog: service dead, restarting")
            try {
                val intent = Intent(context, GuardForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "WorkManager watchdog restart failed: ${e.message}", e)
            }
        }

        WatchdogReceiver.scheduleWatchdog(context)
        return Result.success()
    }

    companion object {
        private const val TAG = "ServiceHealthWorker"
        const val UNIQUE_WORK_NAME = "focus_guard_service_health"
    }
}
