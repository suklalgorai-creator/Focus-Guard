package com.focusguard.app.detection

import android.os.SystemClock
import android.view.View
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FocusedSurfaceBlocker(
    private val overlayManager: OverlayManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastBlockKey: String? = null
    private var lastBlockAtMs: Long = 0L
    private var hideJob: Job? = null

    fun onSurfaceDetected(match: ContentSurfaceMatch) {
        val now = SystemClock.elapsedRealtime()
        if (match.blockKey == lastBlockKey && (now - lastBlockAtMs) < BLOCK_COOLDOWN_MS) {
            return
        }

        lastBlockKey = match.blockKey
        lastBlockAtMs = now

        AppDetectorService.instance?.forceBack()

        if (!FocusGuardApp.instance.prefs.isGuardActiveNow()) return

        if (!overlayManager.show()) {
            AppDetectorService.instance?.forceHome()
            return
        }
        hideJob?.cancel()
        hideJob = scope.launch {
            overlayManager.resetUI()

            overlayManager.getPrimaryMessage()?.apply {
                text = "${match.title} is blocked"
                setTextColor(0xFFE94560.toInt())
                textSize = 24f
            }

            overlayManager.getSecondaryMessage()?.text = match.message
            overlayManager.getAttemptInfo()?.apply {
                text = "Focused block active"
                visibility = View.VISIBLE
            }
            overlayManager.getProgressBar()?.visibility = View.GONE

            delay(1800)
            overlayManager.hide()
        }
    }

    fun destroy() {
        hideJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val BLOCK_COOLDOWN_MS = 1200L
    }
}
