package com.focusguard.app.antibypass

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.graphics.toArgb
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.overlay.OverlayManager
import com.focusguard.app.ui.theme.FrictionColors
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Protects Focus Guard's own system settings/removal path during an active block.
 *
 * Other apps can still be uninstalled and normal file delete screens are ignored.
 */
class SettingsBlocker(
    private val context: Context,
    private val overlayManager: OverlayManager
) {

    private var isBlockingSettings = false
    private var accessibilityWatchUntilMs = 0L
    private var settingsBlockJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val protectedTargetKeywords by lazy {
        buildSet {
            add(context.packageName.lowercase(Locale.US))
            add("focus guard")
            add("focusguard")
            add("friction guard")
            runCatching {
                val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
                context.packageManager.getApplicationLabel(appInfo).toString()
            }.getOrNull()
                ?.lowercase(Locale.US)
                ?.takeIf { it.isNotBlank() }
                ?.let(::add)
        }
    }

    private val selfRemovalKeywords = listOf(
        "uninstall",
        "remove app",
        "delete app",
        "delete this app",
        "remove from device",
        "clear data",
        "clear storage",
        "manage storage",
        "clear all data",
        "reset app",
        "clear cache and data",
        "force stop",
        "force close",
        "disable app",
        "disable this app"
    )

    private val selfAdminRemovalKeywords = listOf(
        "deactivate this device admin app",
        "deactivate this device administrator",
        "remove device admin",
        "deactivate"
    )

    private val accessibilityDisableKeywords = listOf(
        "accessibility",
        "installed services",
        "downloaded services",
        "downloaded apps",
        "accessibility service",
        "turn off service",
        "stop service",
        "accessibility settings"
    )

    private val focusGuardSettingsKeywords = listOf(
        "app info",
        "app details",
        "app permissions",
        "notifications",
        "battery",
        "storage",
        "mobile data",
        "data usage",
        "screen time",
        "open by default",
        "display over other apps",
        "appear on top",
        "usage access",
        "force stop",
        "uninstall",
        "clear data",
        "clear storage"
    )

    fun onSettingsOpened(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): Boolean {
        if (isBlockingSettings) return true

        val text = extractText(event, rootNode).lowercase(Locale.US)
        rememberAccessibilityZone(text)

        if (shouldBlockSettingsAttempt(text)) {
            Log.w(TAG, "Focus Guard settings path opened during active block")
            blockSettings()
            return true
        }

        Log.d(TAG, "Settings/installer opened; waiting for Focus Guard removal context")
        return false
    }

    fun onSettingsContentChanged(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): Boolean {
        val text = extractText(event, rootNode).lowercase(Locale.US)
        if (text.isBlank()) return false
        rememberAccessibilityZone(text)

        if (shouldBlockSettingsAttempt(text)) {
            if (!isBlockingSettings) {
                Log.w(TAG, "Focus Guard settings bypass attempt detected")
                blockSettings()
            }
            return true
        }

        return false
    }

    private fun blockSettings() {
        isBlockingSettings = true

        FocusGuardApp.instance.antiBypassManager.recordSettingsProtectionTriggered("focus_guard_settings")
        Log.w(TAG, "Self-removal protection level increased: +2 escalation levels")

        com.focusguard.app.detection.AppDetectorService.instance?.forceHome()

        val overlayShown = overlayManager.show()
        if (!overlayShown) {
            isBlockingSettings = false
            return
        }

        settingsBlockJob = scope.launch {
            withContext(Dispatchers.Main) {
                overlayManager.resetUI()

                overlayManager.getPrimaryMessage()?.apply {
                    text = "Focus Guard is protected"
                    setTextColor(FrictionColors.Error.toArgb())
                    textSize = 26f
                }
                overlayManager.getSecondaryMessage()?.text =
                    "Active block time is keeping you on track. Accessibility protection remains active.\n\n" +
                        "Protection level increased to help you stay focused."

                overlayManager.getAttemptInfo()?.apply {
                    text = "Exit protection active"
                    setTextColor(FrictionColors.Warning.toArgb())
                }
            }

            delay(5000)

            withContext(Dispatchers.Main) {
                overlayManager.hide()
                isBlockingSettings = false
            }
        }
    }

    private fun isFocusGuardRemovalAttempt(text: String): Boolean {
        val mentionsFocusGuard = protectedTargetKeywords.any(text::contains)
        if (!mentionsFocusGuard) return false

        return selfRemovalKeywords.any(text::contains) ||
            selfAdminRemovalKeywords.any(text::contains)
    }

    private fun isAccessibilityDisableAttempt(text: String): Boolean {
        val mentionsFocusGuard = protectedTargetKeywords.any(text::contains)
        val hasAccessibilityContext = accessibilityDisableKeywords.any(text::contains)
        return mentionsFocusGuard && hasAccessibilityContext
    }

    private fun isFocusGuardSettingsAttempt(text: String): Boolean {
        val mentionsFocusGuard = protectedTargetKeywords.any(text::contains)
        if (!mentionsFocusGuard) return false

        return focusGuardSettingsKeywords.any(text::contains) ||
            isFocusGuardRemovalAttempt(text) ||
            isAccessibilityDisableAttempt(text)
    }

    private fun shouldBlockSettingsAttempt(fullText: String): Boolean {
        return isFocusGuardSettingsAttempt(fullText) ||
            protectedTargetKeywords.any(fullText::contains) ||
            (isWatchingAccessibilityZone() && isFocusGuardAccessibilityTarget(fullText))
    }

    private fun rememberAccessibilityZone(text: String) {
        val isAccessibilitySettings = accessibilityDisableKeywords.any(text::contains)
        if (isAccessibilitySettings) {
            accessibilityWatchUntilMs = SystemClock.elapsedRealtime() + ACCESSIBILITY_WATCH_MS
        }
    }

    private fun isWatchingAccessibilityZone(): Boolean {
        return SystemClock.elapsedRealtime() <= accessibilityWatchUntilMs
    }

    private fun isFocusGuardAccessibilityTarget(text: String): Boolean {
        val mentionsFocusGuard = protectedTargetKeywords.any(text::contains)
        if (!mentionsFocusGuard) return false

        return accessibilityDisableKeywords.any(text::contains) ||
            "on" in text ||
            "off" in text ||
            "allow" in text ||
            "deny" in text ||
            "use service" in text
    }

    private fun extractText(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): String {
        val texts = linkedSetOf<String>()
        event.text?.forEach { texts.add(it.toString()) }
        event.contentDescription?.let { texts.add(it.toString()) }
        collectNodeText(rootNode, texts)
        return texts.joinToString(" ")
    }

    private fun collectNodeText(
        node: AccessibilityNodeInfo?,
        texts: MutableSet<String>
    ) {
        if (node == null) return

        node.text?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(texts::add)

        for (index in 0 until node.childCount) {
            collectNodeText(node.getChild(index), texts)
        }
    }

    fun destroy() {
        settingsBlockJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SettingsBlocker"
        private const val ACCESSIBILITY_WATCH_MS = 8_000L
    }
}
