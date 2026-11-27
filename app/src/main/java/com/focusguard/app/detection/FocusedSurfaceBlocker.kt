package com.focusguard.app.detection

import android.os.SystemClock
import android.util.Log
import android.view.View
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.friction.tasks.ExamQuestionTask
import com.focusguard.app.friction.tasks.OverlayPyqAttemptLogger
import com.focusguard.app.integration.studyflow.StudyFlowOverlayRenderer
import com.focusguard.app.overlay.OverlayManager
import com.focusguard.app.persistence.AttemptEntity
import com.focusguard.app.persistence.FocusGuardPrefs
import kotlin.random.Random
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class FocusedSurfaceBlocker(
    private val overlayManager: OverlayManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var lastBlockKey: String? = null
    private var lastBlockAtMs: Long = 0L
    private var hideJob: Job? = null

    @Volatile
    var isSurfaceBlockActive: Boolean = false
        private set

    fun onSurfaceDetected(match: ContentSurfaceMatch) {
        val now = SystemClock.elapsedRealtime()
        if (isSurfaceBlockActive || (match.blockKey == lastBlockKey && (now - lastBlockAtMs) < BLOCK_COOLDOWN_MS)) {
            GuardDiagnostics.logEvent(
                GuardDiagnostics.BLOCK_SUPPRESSED_DUPLICATE,
                "surface=${match.surfaceId}"
            )
            return
        }

        val app = FocusGuardApp.instance
        val reelCountBlocked = match.surfaceId == FocusGuardPrefs.SURFACE_INSTAGRAM_REELS &&
            app.reelCountCoordinator.evaluateReelsBlockingPolicy().shouldBlock
        if (!app.prefs.isGuardActiveNow() && !reelCountBlocked) {
            isSurfaceBlockActive = false
            return
        }

        lastBlockKey = match.blockKey
        lastBlockAtMs = now
        isSurfaceBlockActive = true

        if (match.surfaceId == FocusGuardPrefs.SURFACE_INSTAGRAM_REELS) {
            app.reelCountCoordinator.onReelBlockedAttempt()
        }
        val attemptNumber = app.prefs.incrementDailyAttempt()
        app.prefs.recordDistractionRecovery(
            sourceKey = match.blockKey,
            sourceTitle = match.title
        )
        app.trackingManager.onBlockedSurface(match.blockKey, match.packageName)
        AppDetectorService.instance?.forceBack()

        GuardDiagnostics.logEvent(
            GuardDiagnostics.BLOCK_EXECUTED,
            "surface=${match.surfaceId} pkg=${match.packageName}"
        )

        if (!overlayManager.show()) {
            hideJob?.cancel()
            hideJob = scope.launch {
                try {
                    ensureExitedFromSurface(match)
                    logSurfaceAttempt(match, attemptNumber, wasSuccessful = false, durationMs = 0L)
                } finally {
                    isSurfaceBlockActive = false
                }
            }
            return
        }

        hideJob?.cancel()
        hideJob = scope.launch {
            try {
                val startedAtMs = SystemClock.elapsedRealtime()
                val solved = showSurfacePyq(match)
                ensureExitedFromSurface(match)
                overlayManager.hide()
                logSurfaceAttempt(
                    match = match,
                    attemptNumber = attemptNumber,
                    wasSuccessful = solved == true,
                    durationMs = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
                )
            } finally {
                isSurfaceBlockActive = false
            }
        }
    }

    private fun logSurfaceAttempt(
        match: ContentSurfaceMatch,
        attemptNumber: Int,
        wasSuccessful: Boolean,
        durationMs: Long
    ) {
        val prefs = FocusGuardApp.instance.prefs
        scope.launch(Dispatchers.IO) {
            runCatching {
                FocusGuardApp.instance.database.attemptDao().insert(
                    AttemptEntity(
                        blockedPackage = match.blockKey,
                        attemptNumber = attemptNumber,
                        escalationLevel = prefs.currentEscalationLevel,
                        wasSuccessful = wasSuccessful,
                        durationMs = durationMs,
                        wasAbandoned = !wasSuccessful,
                        dateKey = prefs.getTodayKey()
                    )
                )
            }
        }
    }

    /**
     * Verifies that the initial Back action exited the blocked surface before hiding the overlay.
     * Performs at most [MAX_BACK_RETRIES] bounded Back retries, then falls back to Home if needed.
     */
    private suspend fun ensureExitedFromSurface(match: ContentSurfaceMatch) {
        val service = AppDetectorService.instance
        if (service == null) {
            Log.w(TAG, "Exit verification skipped: exit_unverified_service_null")
            return
        }

        if (!service.isSurfaceStillVisible(match)) {
            Log.d(TAG, "Exit result for ${match.blockKey}: exited_via_back")
            return
        }

        repeat(MAX_BACK_RETRIES) { attempt ->
            Log.w(TAG, "Surface still visible after Back; retry ${attempt + 1}/$MAX_BACK_RETRIES")
            service.forceBack()
            delay(EXIT_RETRY_DELAY_MS)
            if (!service.isSurfaceStillVisible(match)) {
                Log.d(TAG, "Exit result for ${match.blockKey}: exited_via_retry_back")
                return
            }
        }

        Log.w(TAG, "Exit result for ${match.blockKey}: fallback_force_home")
        service.forceHome()
        delay(EXIT_RETRY_DELAY_MS)
    }

    private suspend fun showSurfacePyq(match: ContentSurfaceMatch): Boolean? {
        val challenge = ExamQuestionTask(difficulty = Random.nextInt(1, 4)).generate()
        val answered = CompletableDeferred<Boolean>()

        overlayManager.resetUI()
        StudyFlowOverlayRenderer.render(
            container = overlayManager.getTaskContainer(),
            snapshot = FocusGuardApp.instance.prefs.getStudyFlowDaySnapshot()
        )

        overlayManager.getPrimaryMessage()?.apply {
            text = "${match.title} blocked. Solve one PYQ."
            setTextColor(0xFFE94560.toInt())
            textSize = 22f
        }
        overlayManager.getSecondaryMessage()?.text = challenge.question
        overlayManager.getAttemptInfo()?.apply {
            text = "Distraction traded for PYQ"
            visibility = View.VISIBLE
        }
        overlayManager.getProgressBar()?.visibility = View.GONE
        overlayManager.getInputField()?.visibility = View.GONE
        overlayManager.getSubmitButton()?.visibility = View.GONE

        overlayManager.getOptionsContainer()?.visibility = View.VISIBLE
        listOf("A", "B", "C", "D").forEach { option ->
            overlayManager.getOptionButton(option)?.apply {
                visibility = View.VISIBLE
                isEnabled = true
                text = option
                setOnClickListener {
                    if (answered.isCompleted) return@setOnClickListener

                    val isCorrect = challenge.checkAnswer(option)
                    OverlayPyqAttemptLogger.logAnswer(
                        scope = scope,
                        challenge = challenge,
                        selectedOption = option,
                        blockedPackage = match.blockKey,
                        sourceTitle = match.title
                    )
                    listOf("A", "B", "C", "D").forEach { key ->
                        overlayManager.getOptionButton(key)?.isEnabled = false
                    }

                    if (isCorrect) {
                        overlayManager.getPrimaryMessage()?.apply {
                            text = "Good. Back to study."
                            setTextColor(0xFF34D399.toInt())
                        }
                        overlayManager.getSecondaryMessage()?.text =
                            "You turned a distraction impulse into one productive rep."
                    } else {
                        overlayManager.getPrimaryMessage()?.apply {
                            text = "Not quite - the answer is ${challenge.answer}"
                            setTextColor(0xFFFF4D6D.toInt())
                        }
                        overlayManager.getSecondaryMessage()?.text = buildString {
                            append(match.message)
                            challenge.explanation?.takeIf { it.isNotBlank() }?.let {
                                append("\n\nExplanation:\n")
                                append(it)
                            }
                        }
                    }

                    answered.complete(isCorrect)
                }
            }
        }

        val correct = withTimeoutOrNull(SURFACE_PYQ_TIMEOUT_MS) {
            answered.await()
        }

        when (correct) {
            true -> delay(1400)
            false -> delay(4500)
            null -> {
                overlayManager.getPrimaryMessage()?.apply {
                    text = "PYQ skipped. Returning home."
                    setTextColor(0xFFFF4D6D.toInt())
                }
                overlayManager.getSecondaryMessage()?.text =
                    "This surface is blocked during focus mode."
                AppDetectorService.instance?.forceHome()
                delay(1400)
            }
        }
        return correct
    }

    fun destroy() {
        isSurfaceBlockActive = false
        hideJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val TAG = "FocusedSurfaceBlocker"
        private const val BLOCK_COOLDOWN_MS = 1200L
        private const val SURFACE_PYQ_TIMEOUT_MS = 30_000L
        private const val MAX_BACK_RETRIES = 2
        private const val EXIT_RETRY_DELAY_MS = 250L
    }
}
