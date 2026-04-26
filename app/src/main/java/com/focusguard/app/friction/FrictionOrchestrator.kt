package com.focusguard.app.friction

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.R
import com.focusguard.app.detection.AppDetectorService
import com.focusguard.app.friction.layers.*
import com.focusguard.app.overlay.OverlayManager
import com.focusguard.app.persistence.AttemptEntity
import com.focusguard.app.ui.PsychMessages
import kotlinx.coroutines.*

/**
 * MAIN FRICTION FLOW CONTROLLER
 *
 * This is the brain of the entire system. When a blocked app is detected:
 *
 * 1. Overlay is already showing (instant, done by AppDetectorService)
 * 2. EscalationEngine calculates difficulty for this attempt
 * 3. FrictionPipeline builds a randomized layer sequence
 * 4. Layers execute one by one (any failure → restart entire pipeline)
 * 5. If all layers pass → ALWAYS DENY + force home (hard block, no exceptions)
 * 6. 2-minute friction timeout prevents the app from becoming the distraction
 *
 * The orchestrator runs on a coroutine scope tied to the overlay lifecycle.
 * If the user closes the blocked app (goes home), the session is cancelled.
 */
class FrictionOrchestrator(
    private val context: Context,
    private val overlayManager: OverlayManager,
    private val onSessionFinished: (String) -> Unit = {}
) {

    private val escalationEngine = EscalationEngine()
    private val pipeline = FrictionPipeline()

    private var sessionJob: Job? = null
    private val sessionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentAttemptEntity: AttemptEntity? = null
    private var sessionStartTime: Long = 0
    private var isSessionActive = false

    /**
     * Called by AppDetectorService when a blacklisted app is detected.
     * Overlay is already showing at this point.
     */
    fun onBlockedAppDetected(packageName: String) {
        if (isSessionActive) {
            Log.d(TAG, "Session already active, ignoring duplicate detection")
            return
        }

        isSessionActive = true
        sessionStartTime = System.currentTimeMillis()

        sessionJob = sessionScope.launch {
            try {
                val completed = withTimeoutOrNull(SESSION_TIMEOUT_MS) {
                    if (FocusGuardApp.instance.prefs.isStrictBlockActive()) {
                        runStrictBlockSession(packageName)
                    } else {
                        runFrictionSession(packageName)
                    }
                    true
                }
                if (completed == null) {
                    handleSessionTimeout(packageName)
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Friction session cancelled (user left app)")
                logAttempt(packageName, wasAbandoned = true)
            } catch (e: Exception) {
                Log.e(TAG, "Friction session error: ${e.message}", e)
            } finally {
                isSessionActive = false
                onSessionFinished(packageName)
            }
        }
    }

    private suspend fun handleSessionTimeout(packageName: String) {
        Log.w(TAG, "Friction session timed out. Forcing home and resetting state.")
        withContext(Dispatchers.Main) {
            overlayManager.resetUI()
            overlayManager.getPrimaryMessage()?.apply {
                text = "Session timed out"
                setTextColor(0xFFFF4D6D.toInt())
            }
            overlayManager.getSecondaryMessage()?.text =
                "Focus Guard could not complete the challenge safely. Returning home."
        }
        delay(1_000)
        AppDetectorService.instance?.forceHome()
        withContext(Dispatchers.Main) {
            overlayManager.hide()
        }
        logAttempt(packageName, wasAbandoned = true)
    }

    /**
     * Absolute hard block with endless PYQs and motivating lines for Strict Mode duration.
     */
    private suspend fun runStrictBlockSession(packageName: String) {
        val motivatingLines = listOf(
            "Your competitors are studying right now. Don't scroll.",
            "Reels won't get you a medical seat. Books will.",
            "Is watching this really worth failing the exam?",
            "You committed to Monk Mode. Prove it.",
            "Short-term pleasure, long-term pain. Close this app.",
            "Every second you waste here, your rank drops.",
            "Dopamine detox active. Feed your brain PYQs instead.",
            "Future YOU is begging you to close this app.",
            "Distraction is the enemy of selection. Stay focused."
        )

        // Keep them blocked indefinitely with endless PYQs
        while (FocusGuardApp.instance.prefs.isStrictBlockActive() && isSessionActive) {
            val task = com.focusguard.app.friction.tasks.ExamQuestionTask(difficulty = kotlin.random.Random.nextInt(1, 4))
            val challenge = task.generate()
            
            val deferredResult = CompletableDeferred<Boolean>()

            withContext(Dispatchers.Main) {
                overlayManager.resetUI()
                
                overlayManager.getPrimaryMessage()?.apply {
                    text = motivatingLines.random()
                    setTextColor(0xFF34D399.toInt()) // New Theme Green motivation
                    textSize = 20f
                }
                
                overlayManager.getSecondaryMessage()?.apply {
                    text = challenge.question
                    setTextColor(0xCCF0F0F5.toInt()) // Adjusted text color
                }
                
                overlayManager.getProgressBar()?.visibility = View.GONE
                overlayManager.getAttemptInfo()?.apply {
                    text = "Distraction Blocked. Solve this PYQ."
                    setTextColor(0xFFFFB347.toInt()) // Amber emphasis
                }
                
                // Shared handler for answering strict mode PYQ
                val handleStrictAnswer = { userAnswer: String ->
                    if (challenge.checkAnswer(userAnswer)) {
                        // Correct! Feed them guilt and another PYQ or throw them home.
                        overlayManager.getPrimaryMessage()?.text = listOf(
                            "Right answer! But you're still locked out.",
                            "Correct! Now go back to real studying.",
                            "Good job. No reels for you though."
                        ).random()
                        overlayManager.getPrimaryMessage()?.setTextColor(0xFFFFB347.toInt())
                        
                        listOf("A", "B", "C", "D").forEach { 
                            overlayManager.getOptionButton(it)?.isEnabled = false 
                        }
                        
                        sessionScope.launch {
                            delay(2500)
                            // Force them out of the app to truly block them
                            com.focusguard.app.detection.AppDetectorService.instance?.forceHome()
                            deferredResult.complete(true)
                        }
                    } else {
                        // Wrong! Show solution then next PYQ
                        overlayManager.getPrimaryMessage()?.text = "✗ Wrong. Correct answer: ${challenge.answer}"
                        overlayManager.getPrimaryMessage()?.setTextColor(0xFFFF4D6D.toInt()) // New Theme Red
                        
                        if (!challenge.explanation.isNullOrBlank()) {
                            overlayManager.getSecondaryMessage()?.apply {
                                text = "📖 Explanation:\n\n${challenge.explanation}"
                                setTextColor(0xFFBBBBBB.toInt())
                            }
                        }
                        
                        listOf("A", "B", "C", "D").forEach { 
                            overlayManager.getOptionButton(it)?.isEnabled = false 
                        }
                        
                        sessionScope.launch {
                            delay(6000) // Force read explanation
                            deferredResult.complete(false)
                        }
                    }
                }

                overlayManager.getOptionsContainer()?.visibility = View.VISIBLE
                listOf("A", "B", "C", "D").forEach { option ->
                    overlayManager.getOptionButton(option)?.apply {
                        visibility = View.VISIBLE
                        isEnabled = true
                        setOnClickListener {
                            handleStrictAnswer(option)
                        }
                    }
                }
            }
            // Wait for user to interact or 30s timeout to auto-refresh PYQ
            withTimeoutOrNull(30_000) { deferredResult.await() }
        }

        if (FocusGuardApp.instance.prefs.isGuardActiveNow()) {
            // Guard active but strict block ended (i.e. normal block hours)
            runFrictionSession(packageName)
        }
    }

    /**
     * Main friction session loop.
     * Can restart multiple times due to failures/uncertainty gate.
     */
    private suspend fun runFrictionSession(packageName: String) {
        var restartCount = 0
        val maxRestarts = 10 // Safety limit
        val frictionStartTime = System.currentTimeMillis()

        while (restartCount < maxRestarts) {
            // ── FRICTION TIMEOUT ──
            // If user has been stuck in friction for > 2 min, just force-home.
            // At this point the app is becoming the distraction, not the cure.
            val elapsedMs = System.currentTimeMillis() - frictionStartTime
            if (elapsedMs > MAX_FRICTION_MS) {
                Log.w(TAG, "Friction timeout hit (${elapsedMs}ms). Force-homing.")
                withContext(Dispatchers.Main) {
                    overlayManager.getPrimaryMessage()?.apply {
                        text = "You've wasted ${elapsedMs / 60_000} minutes here."
                        setTextColor(0xFFFF4D6D.toInt())
                    }
                    overlayManager.getSecondaryMessage()?.text =
                        "That's more time than a reel takes. Go study."
                }
                delay(2500)
                AppDetectorService.instance?.forceHome()
                logAttempt(packageName, wasAbandoned = true)
                return
            }

            // Calculate escalation
            val params = if (restartCount == 0) {
                escalationEngine.calculate()
            } else {
                escalationEngine.calculate().copy(
                    attemptNumber = FocusGuardApp.instance.prefs.dailyAttemptCount
                )
            }

            Log.d(TAG, "Starting friction session: attempt=${params.attemptNumber}, " +
                    "escalation=${params.escalationLevel}, restart=$restartCount")

            val layers = pipeline.build(params)

            val frictionContext = FrictionContext(
                attemptNumber = params.attemptNumber,
                escalationLevel = params.escalationLevel,
                overlayView = overlayManager.getFrictionContainer() as ViewGroup,
                coroutineScope = sessionScope,
                blockedPackage = packageName,
                daysUntilExam = FocusGuardApp.instance.prefs.getDaysUntilExam()
            )

            withContext(Dispatchers.Main) {
                overlayManager.resetUI()
                overlayManager.getAttemptInfo()?.text =
                    "Attempt #${params.attemptNumber} today • Restart #$restartCount"
            }

            var pipelinePassed = true
            for (layer in layers) {
                Log.d(TAG, "Executing layer: ${layer.name}")
                val result = layer.execute(frictionContext)

                when (result) {
                    is FrictionResult.Passed -> {
                        Log.d(TAG, "Layer ${layer.name} passed")
                        continue
                    }
                    is FrictionResult.Restart -> {
                        Log.d(TAG, "Layer ${layer.name} triggered RESTART")
                        restartCount++
                        pipelinePassed = false
                        withContext(Dispatchers.Main) {
                            overlayManager.getPrimaryMessage()?.apply {
                                text = "Restarting from the beginning..."
                                setTextColor(0xFFE94560.toInt())
                            }
                        }
                        delay(2000)
                        break
                    }
                    is FrictionResult.Abandoned -> {
                        Log.d(TAG, "User abandoned friction session")
                        logAttempt(packageName, wasAbandoned = true)
                        return
                    }
                    is FrictionResult.Error -> {
                        Log.e(TAG, "Layer ${layer.name} error: ${result.message}")
                        continue
                    }
                }
            }

            if (!pipelinePassed) continue

            // ── All layers passed — ALWAYS DENY, force home ──
            Log.d(TAG, "All layers passed. Access DENIED (always).")

            withContext(Dispatchers.Main) {
                overlayManager.getPrimaryMessage()?.apply {
                    text = PsychMessages.getDenialMessage()
                    setTextColor(0xFFFF4D6D.toInt())
                }
                overlayManager.getSecondaryMessage()?.text =
                    "You solved everything but this app stays locked.\nGo study. Your future self will thank you."
                overlayManager.getProgressBar()?.visibility = View.GONE
            }
            delay(3500)

            AppDetectorService.instance?.forceHome()
            logAttempt(packageName, wasAbandoned = true)
            return
        }

        // Max restarts hit — forced denial + force home
        withContext(Dispatchers.Main) {
            overlayManager.getPrimaryMessage()?.apply {
                text = "Maximum attempts reached. BLOCKED. 🔒"
                setTextColor(0xFFE94560.toInt())
            }
            overlayManager.getSecondaryMessage()?.text =
                "You tried 10 times. This app is not for you right now.\nGo study."
        }
        delay(3000)
        AppDetectorService.instance?.forceHome()
        logAttempt(packageName, wasAbandoned = true)
    }

    /**
     * Called when user leaves the blocked app (home button, back, etc.)
     */
    fun onBlockedAppClosed() {
        sessionJob?.cancel()
        isSessionActive = false
    }

    /**
     * Log attempt to Room database for analytics.
     */
    private fun logAttempt(
        packageName: String,
        wasSuccessful: Boolean = false,
        wasAbandoned: Boolean = false
    ) {
        val prefs = FocusGuardApp.instance.prefs
        val duration = System.currentTimeMillis() - sessionStartTime

        if (wasAbandoned) {
            prefs.totalGiveUps = prefs.totalGiveUps + 1
        }

        sessionScope.launch(Dispatchers.IO) {
            try {
                val attempt = AttemptEntity(
                    blockedPackage = packageName,
                    attemptNumber = prefs.dailyAttemptCount,
                    escalationLevel = prefs.currentEscalationLevel,
                    wasSuccessful = wasSuccessful,
                    durationMs = duration,
                    wasAbandoned = wasAbandoned,
                    dateKey = prefs.getTodayKey()
                )
                FocusGuardApp.instance.database.attemptDao().insert(attempt)
                Log.d(TAG, "Attempt logged: success=$wasSuccessful, abandoned=$wasAbandoned, " +
                        "duration=${duration}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log attempt: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "FrictionOrchestrator"
        /** Max time a friction session can run before force-home. Prevents the app from becoming the distraction. */
        private const val MAX_FRICTION_MS = 2 * 60 * 1000L // 2 minutes
        private const val SESSION_TIMEOUT_MS = 45_000L
    }
}
