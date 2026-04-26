package com.focusguard.app.antibypass

import android.content.Context
import android.util.Log
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.R
import com.focusguard.app.friction.EscalationEngine
import com.focusguard.app.overlay.OverlayManager
import com.focusguard.app.ui.theme.FrictionColors
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.*

/**
 * Detects when the user tries to access Settings during opted-in Strict Mode.
 *
 * When Strict Mode exit protection is enabled and a bypass surface is detected:
 * 1. Shows a blocking overlay on top of Settings
 * 2. Displays a clear protection message
 * 3. Adds +2 to escalation penalty for the next friction session
 *
 * Monitors for:
 * - Opening Settings app
 * - Navigating to Accessibility settings
 * - Navigating to App Info for Focus Guard
 * - Navigating to Display over other apps settings
 */
class SettingsBlocker(
    private val context: Context,
    private val overlayManager: OverlayManager
) {

    private val escalationEngine = EscalationEngine()
    private var isBlockingSettings = false
    private var settingsBlockJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Suspicious Settings paths
    private val suspiciousKeywords = listOf(
        "accessibility",
        "focusguard",
        "focus guard",
        "focusguard",
        "friction guard",
        "display over",
        "draw over",
        "appear on top",
        "overlay",
        "usage access",
        "usage data",
        "app info",
        "force stop",
        "disable",
        "uninstall",
        "permissions",
        "special app access",
        "special access",
        "device admin",
        "device administrator",
        "battery optimization",
        "battery saver",
        "clear data",
        "clear storage",
        "clear cache",
        "manage apps",
        "installed apps",
        // Launcher context menu keywords (long-press app icon)
        "remove",
        "delete",
        "卸载" // Chinese uninstall (ColorOS sometimes uses it)
    )

    /**
     * Called when Settings app window is detected.
     */
    fun onSettingsOpened() {
        if (isBlockingSettings) return

        Log.w(TAG, "Settings opened during opted-in Strict Mode protection")
        // Don't block immediately — only block if they navigate to suspicious areas
        // The content change handler will catch that
    }

    /**
     * Called on Settings content changes to detect navigation to
     * Accessibility, App Info, or overlay permission screens.
     */
    fun onSettingsContentChanged(event: AccessibilityEvent) {
        val text = extractText(event).lowercase()

        // Check if user is navigating to a suspicious settings page
        val isSuspicious = suspiciousKeywords.any { keyword ->
            text.contains(keyword)
        }

        if (isSuspicious && !isBlockingSettings) {
            Log.w(TAG, "🚨 BYPASS ATTEMPT DETECTED! Text: $text")
            blockSettings()
        }
    }

    fun onBypassAttempt() {
        blockSettings()
    }

    private fun blockSettings() {
        isBlockingSettings = true

        // Apply penalty
        escalationEngine.applyBypassPenalty(2)
        Log.w(TAG, "Exit protection penalty applied: +2 escalation levels")

        // Opted-in Strict Mode exit protection: move the user away from system settings.
        com.focusguard.app.detection.AppDetectorService.instance?.forceHome()

        // Show blocking overlay as secondary defense
        val overlayShown = overlayManager.show()
        if (!overlayShown) {
            isBlockingSettings = false
            return
        }

        settingsBlockJob = scope.launch {
            withContext(Dispatchers.Main) {
                overlayManager.resetUI()

                overlayManager.getPrimaryMessage()?.apply {
                    text = "Strict Mode is active"
                    setTextColor(FrictionColors.Error.toArgb())
                    textSize = 26f
                }
                overlayManager.getSecondaryMessage()?.text =
                    "You enabled Exit Protection for this Strict Mode session.\n\n" +
                    "Focus Guard will return you home until the timer ends.\n\n" +
                    "Penalty applied: +2 escalation levels for your next distraction attempt."

                overlayManager.getAttemptInfo()?.apply {
                    text = "Total exit delay penalty: +${FocusGuardApp.instance.prefs.bypassPenalty} levels"
                    setTextColor(FrictionColors.Warning.toArgb())
                }
            }

            // Keep overlay showing for 5 seconds (user is already on Home screen)
            // Then hide. If they go back to Settings, onSettingsOpened fires again.
            delay(5000)

            withContext(Dispatchers.Main) {
                overlayManager.hide()
                isBlockingSettings = false
            }
        }
    }

    private fun extractText(event: AccessibilityEvent): String {
        val texts = mutableListOf<String>()
        event.text?.forEach { texts.add(it.toString()) }
        event.contentDescription?.let { texts.add(it.toString()) }
        return texts.joinToString(" ")
    }

    fun destroy() {
        settingsBlockJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SettingsBlocker"
    }
}
