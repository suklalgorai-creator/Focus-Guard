package com.focusguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.focusguard.app.persistence.FocusGuardPrefs

/**
 * Starts the foreground service after device reboot.
 * Listens for BOOT_COMPLETED, QUICKBOOT_POWERON, and LOCKED_BOOT_COMPLETED.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                Log.d(TAG, "Boot completed — starting GuardForegroundService")
                startGuardService(context)
            }
        }
    }

    private fun startGuardService(context: Context) {
        val prefs = FocusGuardPrefs(context)
        if (!prefs.hasAcceptedAccessibilityDisclosure || !prefs.isGuardActiveNow()) {
            Log.d(TAG, "Disclosure not accepted yet; skipping boot start")
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
            Log.e(TAG, "Failed to start service after boot: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
