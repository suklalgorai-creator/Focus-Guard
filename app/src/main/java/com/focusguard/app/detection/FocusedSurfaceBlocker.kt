package com.focusguard.app.detection

import android.os.SystemClock
import android.view.View
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.friction.tasks.ExamQuestionTask
import com.focusguard.app.friction.tasks.OverlayPyqAttemptLogger
import com.focusguard.app.integration.studyflow.StudyFlowOverlayRenderer
import com.focusguard.app.overlay.OverlayManager
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

    fun onSurfaceDetected(match: ContentSurfaceMatch) {
        val now = SystemClock.elapsedRealtime()
        if (match.blockKey == lastBlockKey && (now - lastBlockAtMs) < BLOCK_COOLDOWN_MS) {
            return
        }

        lastBlockKey = match.blockKey
        lastBlockAtMs = now

        FocusGuardApp.instance.prefs.recordDistractionRecovery(
            sourceKey = match.blockKey,
            sourceTitle = match.title
        )
        AppDetectorService.instance?.forceBack()

        if (!FocusGuardApp.instance.prefs.isGuardActiveNow()) return

        if (!overlayManager.show()) {
            AppDetectorService.instance?.forceHome()
            return
        }

        hideJob?.cancel()
        hideJob = scope.launch {
            showSurfacePyq(match)
            overlayManager.hide()
        }
    }

    private suspend fun showSurfacePyq(match: ContentSurfaceMatch) {
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
    }

    fun destroy() {
        hideJob?.cancel()
        scope.cancel()
    }

    companion object {
        private const val BLOCK_COOLDOWN_MS = 1200L
        private const val SURFACE_PYQ_TIMEOUT_MS = 30_000L
    }
}
