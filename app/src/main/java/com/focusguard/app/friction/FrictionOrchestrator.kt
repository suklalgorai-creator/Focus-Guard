package com.focusguard.app.friction

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.R
import com.focusguard.app.detection.AppDetectorService
import com.focusguard.app.friction.layers.*
import com.focusguard.app.friction.tasks.OverlayPyqAttemptLogger
import com.focusguard.app.friction.tasks.QuestionRepository
import com.focusguard.app.integration.studyflow.StudyFlowDaySnapshot
import com.focusguard.app.integration.studyflow.StudyFlowOverlayRenderer
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
 * 6. Escalating friction timeout prevents the app from becoming the distraction
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
    private var exitRevealJob: Job? = null

    private data class ReflectionResponse(
        val reason: String,
        val examAligned: Boolean,
        val proof: String?
    )

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
        FocusGuardApp.instance.prefs.recordDistractionRecovery(
            sourceKey = packageName,
            sourceTitle = blockedAppLabel(packageName)
        )

        sessionJob = sessionScope.launch {
            try {
                val sessionTimeout = calculateSessionTimeout()
                val completed = withTimeoutOrNull(sessionTimeout) {
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
                exitRevealJob?.cancel()
                exitRevealJob = null
                isSessionActive = false
                onSessionFinished(packageName)
            }
        }
    }

    private suspend fun handleSessionTimeout(packageName: String) {
        Log.w(TAG, "Friction session timed out. Forcing home and resetting state.")
        val prefs = FocusGuardApp.instance.prefs
        prefs.timeoutExploitCount = prefs.timeoutExploitCount + 1
        prefs.bypassPenalty = prefs.bypassPenalty + 1

        withContext(Dispatchers.Main) {
            overlayManager.resetUI()
            overlayManager.getPrimaryMessage()?.apply {
                text = "Session timed out"
                setTextColor(0xFFFF4D6D.toInt())
            }
            overlayManager.getSecondaryMessage()?.text =
                "Protection level increased. Next session will be longer."
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
            "Focus mode is active. Stay with your plan.",
            "This app is blocked. Your study session continues.",
            "Return to your study plan. You are building a habit.",
            "One PYQ done. Back to focused work.",
            "Stay with the session. Momentum matters.",
            "Your exam prep does not pause. Neither should you.",
            "This block is protecting your future score."
        )

        // Keep them blocked indefinitely with endless PYQs
        while (FocusGuardApp.instance.prefs.isStrictBlockActive() && isSessionActive) {
            val task = com.focusguard.app.friction.tasks.ExamQuestionTask(difficulty = kotlin.random.Random.nextInt(1, 4))
            val challenge = task.generate()
            
            val deferredResult = CompletableDeferred<Boolean>()

            withContext(Dispatchers.Main) {
                overlayManager.resetUI()
                overlayManager.getExitButton()?.visibility = View.GONE
                renderStudyFlowSnapshot()
                
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
                    text = "Blocked app. Solve this PYQ."
                    setTextColor(0xFFFFB347.toInt()) // Amber emphasis
                }
                
                // Shared handler for answering strict mode PYQ
                val handleStrictAnswer = { userAnswer: String ->
                    val isCorrect = challenge.checkAnswer(userAnswer)
                    OverlayPyqAttemptLogger.logAnswer(
                        scope = sessionScope,
                        challenge = challenge,
                        selectedOption = userAnswer,
                        blockedPackage = packageName,
                        sourceTitle = blockedAppLabel(packageName)
                    )

                    if (isCorrect) {
                        overlayManager.getPrimaryMessage()?.text = listOf(
                            "Correct. Focus mode stays active.",
                            "Good answer. Returning you to study.",
                            "Correct. This app remains blocked."
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
                        // Wrong answer: show solution, then refresh the challenge.
                        overlayManager.getPrimaryMessage()?.text =
                            "Not quite - the answer is ${challenge.answer}"
                        overlayManager.getPrimaryMessage()?.setTextColor(0xFFFF4D6D.toInt()) // New Theme Red
                        
                        if (!challenge.explanation.isNullOrBlank()) {
                            overlayManager.getSecondaryMessage()?.apply {
                                text = "Explanation:\n\n${challenge.explanation}"
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
        val sessionParams = escalationEngine.calculate()

        while (restartCount < maxRestarts) {
            // ── FRICTION TIMEOUT ──
            // If user stays in friction past the escalating session limit, force-home.
            // At this point the app is becoming the distraction, not the cure.
            val elapsedMs = System.currentTimeMillis() - frictionStartTime
            val maxFrictionMs = calculateSessionTimeout()
            if (elapsedMs > maxFrictionMs) {
                Log.w(TAG, "Friction timeout hit (${elapsedMs}ms). Force-homing.")
                withContext(Dispatchers.Main) {
                    overlayManager.getPrimaryMessage()?.apply {
                        text = "Time limit reached"
                        setTextColor(0xFFFF4D6D.toInt())
                    }
                    overlayManager.getSecondaryMessage()?.text =
                        "Back to your study session so Focus Guard does not become another distraction."
                }
                delay(2500)
                AppDetectorService.instance?.forceHome()
                logAttempt(packageName, wasAbandoned = true)
                return
            }

            val params = sessionParams

            Log.d(TAG, "Starting friction session: attempt=${params.attemptNumber}, " +
                    "escalation=${params.escalationLevel}, restart=$restartCount")

            val streakDays = FocusGuardApp.instance.prefs.focusStreakDays
            if (streakDays >= 3 && params.attemptNumber == 1) {
                withContext(Dispatchers.Main) {
                    overlayManager.resetUI()
                    overlayManager.getPrimaryMessage()?.apply {
                        text = "You had a $streakDays-day clean streak"
                        setTextColor(0xFFFFB347.toInt())
                        textSize = 24f
                    }
                    overlayManager.getSecondaryMessage()?.apply {
                        text = "Is it worth breaking for ${blockedAppLabel(packageName)}?\n\n" +
                            "Keeping today to one attempt preserves your streak."
                        setTextColor(0xCCF0F0F5.toInt())
                    }
                }
                delay(5000)
            }

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
                scheduleExitButtonReveal(params.escalationLevel)
                val currentStreakDays = FocusGuardApp.instance.prefs.focusStreakDays
                overlayManager.getAttemptInfo()?.text = if (currentStreakDays >= 2) {
                    "$currentStreakDays-day focus streak | Attempt ${params.attemptNumber}"
                } else {
                    "Attempt ${params.attemptNumber} | Restart $restartCount"
                }
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
                                text = "Let's try that again. You are building the habit."
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

            val reflection = runIntentReflectionGate(packageName)

            // ── All layers passed — ALWAYS DENY, force home ──
            Log.d(TAG, "All layers passed. Access DENIED after reflection gate.")

            withContext(Dispatchers.Main) {
                val contextualDenialTitle = PsychMessages.getDenialMessage(
                    attemptCount = FocusGuardApp.instance.prefs.dailyAttemptCount,
                    streakDays = FocusGuardApp.instance.prefs.focusStreakDays,
                    examName = QuestionRepository.getExamName(),
                    daysLeft = FocusGuardApp.instance.prefs.getDaysUntilExam()
                )
                val denialTitle = when {
                    reflection.examAligned && !reflection.proof.isNullOrBlank() ->
                        "Intent noted. Focus protection stays active."
                    reflection.examAligned ->
                        "Focus mode stays active."
                    else -> contextualDenialTitle
                }
                val denialBody = buildString {
                    if (reflection.reason.isNotBlank()) {
                        append("You said: \"")
                        append(reflection.reason.take(80))
                        append("\".\n\n")
                    }
                    if (reflection.examAligned && !reflection.proof.isNullOrBlank()) {
                        append("You even tried to justify it:\n")
                        append(reflection.proof.take(120))
                        append("\n\n")
                    }
                    append("Focus mode is active, so ${blockedAppLabel(packageName)} stays blocked for now.")
                }

                overlayManager.resetUI()
                renderStudyFlowSnapshot()
                overlayManager.getPrimaryMessage()?.apply {
                    text = denialTitle
                    setTextColor(0xFFFF4D6D.toInt())
                }
                overlayManager.getSecondaryMessage()?.text = denialBody
                overlayManager.getProgressBar()?.visibility = View.GONE
                overlayManager.getAttemptInfo()?.text = "Blocked after intent check"
            }
            delay(3500)

            AppDetectorService.instance?.forceHome()
            logAttempt(packageName, wasAbandoned = true)
            return
        }

        // Max restarts hit — forced denial + force home
        withContext(Dispatchers.Main) {
            overlayManager.getPrimaryMessage()?.apply {
                text = "Session complete"
                setTextColor(0xFFE94560.toInt())
            }
            overlayManager.getSecondaryMessage()?.text =
                "Good effort on the challenges. Back to your study session."
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
        exitRevealJob?.cancel()
        exitRevealJob = null
        isSessionActive = false
    }

    private fun scheduleExitButtonReveal(escalationLevel: Int) {
        exitRevealJob?.cancel()

        val exitDelayMs = when {
            escalationLevel <= 0 -> 10_000L
            escalationLevel == 1 -> 20_000L
            escalationLevel == 2 -> 30_000L
            else -> 45_000L
        }

        exitRevealJob = sessionScope.launch {
            delay(exitDelayMs)
            if (!isSessionActive) return@launch
            withContext(Dispatchers.Main) {
                overlayManager.getExitButton()?.visibility = View.VISIBLE
            }
        }
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

    private suspend fun runIntentReflectionGate(packageName: String): ReflectionResponse {
        val appLabel = blockedAppLabel(packageName)
        val examName = QuestionRepository.getExamName().uppercase()
        val studyFlowSnapshot = currentStudyFlowSnapshot()
        val studyFlowLead = buildStudyFlowLead(studyFlowSnapshot)

        val reason = askForTextResponse(
            title = "Before opening $appLabel",
            body = buildString {
                if (studyFlowLead.isNotBlank()) {
                    append(studyFlowLead)
                    append("\n\n")
                }
                append("Why do you want to open $appLabel right now?")
            },
            hint = "Type the reason...",
            submitLabel = "Continue",
            stepLabel = "Intent check 1/3"
        )

        val examChoice = askForChoiceResponse(
            title = "Reality check",
            body = buildString {
                if (studyFlowLead.isNotBlank()) {
                    append(studyFlowLead)
                    append("\n\n")
                }
                append("Will this help your $examName prep in the next 10 minutes?")
            },
            stepLabel = "Intent check 2/3",
            choices = listOf("Yes, directly", "No", "Not really")
        )

        val proof = if (examChoice == 0) {
            askForTextResponse(
                title = "Prove it",
                body = buildString {
                    if (studyFlowLead.isNotBlank()) {
                        append(studyFlowLead)
                        append("\n\n")
                    }
                    append("What lesson, topic, or task will you do there?")
                },
                hint = "Be specific about the lesson, topic, or task.",
                submitLabel = "Lock it in",
                stepLabel = "Intent check 3/3"
            )
        } else {
            null
        }

        return ReflectionResponse(
            reason = reason,
            examAligned = examChoice == 0,
            proof = proof
        )
    }

    private suspend fun askForTextResponse(
        title: String,
        body: String,
        hint: String,
        submitLabel: String,
        stepLabel: String
    ): String {
        val response = CompletableDeferred<String>()

        withContext(Dispatchers.Main) {
            overlayManager.resetUI()
            renderStudyFlowSnapshot()

            val primaryText = overlayManager.getPrimaryMessage()
            val secondaryText = overlayManager.getSecondaryMessage()
            val attemptInfo = overlayManager.getAttemptInfo()
            val inputField = overlayManager.getInputField()
            val submitButton = overlayManager.getSubmitButton()

            primaryText?.apply {
                text = title
                setTextColor(0xFFF0F0F5.toInt())
                textSize = 22f
            }
            secondaryText?.apply {
                text = body
                setTextColor(0xCCF0F0F5.toInt())
            }
            attemptInfo?.apply {
                text = stepLabel
                setTextColor(0xFFFFB347.toInt())
            }

            inputField?.apply {
                visibility = View.VISIBLE
                setText("")
                this.hint = hint
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                isEnabled = true
                requestFocus()
            }
            submitButton?.apply {
                visibility = View.VISIBLE
                text = submitLabel
                isEnabled = true
                setOnClickListener {
                    val value = inputField?.text?.toString()?.trim().orEmpty()
                    if (value.length < 15) {
                        attemptInfo?.text = "Write at least 15 characters (${value.length}/15)"
                        return@setOnClickListener
                    }
                    if (!response.isCompleted) {
                        isEnabled = false
                        inputField?.isEnabled = false
                        response.complete(value)
                    }
                }
            }
        }

        return withTimeoutOrNull(TEXT_PROMPT_TIMEOUT_MS) {
            response.await()
        }.orEmpty()
    }

    private suspend fun askForChoiceResponse(
        title: String,
        body: String,
        stepLabel: String,
        choices: List<String>
    ): Int {
        val response = CompletableDeferred<Int>()

        withContext(Dispatchers.Main) {
            overlayManager.resetUI()
            renderStudyFlowSnapshot()

            val primaryText = overlayManager.getPrimaryMessage()
            val secondaryText = overlayManager.getSecondaryMessage()
            val attemptInfo = overlayManager.getAttemptInfo()
            val optionsContainer = overlayManager.getOptionsContainer()

            primaryText?.apply {
                text = title
                setTextColor(0xFFF0F0F5.toInt())
                textSize = 22f
            }
            secondaryText?.apply {
                text = body
                setTextColor(0xCCF0F0F5.toInt())
            }
            attemptInfo?.apply {
                text = stepLabel
                setTextColor(0xFFFFB347.toInt())
            }

            optionsContainer?.visibility = View.VISIBLE

            val buttons = listOfNotNull(
                overlayManager.getOptionButton("A"),
                overlayManager.getOptionButton("B"),
                overlayManager.getOptionButton("C"),
                overlayManager.getOptionButton("D")
            )

            buttons.forEachIndexed { index, button ->
                if (index < choices.size) {
                    button.visibility = View.VISIBLE
                    button.isEnabled = true
                    button.text = choices[index]
                    button.setOnClickListener {
                        if (!response.isCompleted) {
                            buttons.forEach { it.isEnabled = false }
                            response.complete(index)
                        }
                    }
                } else {
                    button.visibility = View.GONE
                }
            }
        }

        return withTimeoutOrNull(CHOICE_PROMPT_TIMEOUT_MS) {
            response.await()
        } ?: 2
    }

    private fun currentStudyFlowSnapshot(): StudyFlowDaySnapshot? {
        return FocusGuardApp.instance.prefs.getStudyFlowDaySnapshot()
    }

    private fun renderStudyFlowSnapshot(snapshot: StudyFlowDaySnapshot? = currentStudyFlowSnapshot()) {
        StudyFlowOverlayRenderer.render(
            container = overlayManager.getTaskContainer(),
            snapshot = snapshot
        )
    }

    private fun buildStudyFlowLead(snapshot: StudyFlowDaySnapshot?): String {
        val safeSnapshot = snapshot ?: return ""
        val topItems = safeSnapshot.pendingItems.take(2)
        if (topItems.isEmpty()) {
            return safeSnapshot.focusPrompt.orEmpty()
        }

        val tasks = topItems.joinToString(", ") { item ->
            listOfNotNull(item.subject?.takeIf { it.isNotBlank() }, item.title)
                .joinToString(" - ")
        }

        return buildString {
            append("StudyFlow still has ")
            append(tasks)
            append(" pending today.")
            safeSnapshot.focusPrompt?.takeIf { it.isNotBlank() }?.let {
                append(" ")
                append(it)
            }
        }
    }

    private fun blockedAppLabel(packageName: String): String {
        return runCatching {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    companion object {
        private const val TAG = "FrictionOrchestrator"
        private const val TEXT_PROMPT_TIMEOUT_MS = 25_000L
        private const val CHOICE_PROMPT_TIMEOUT_MS = 20_000L

        private fun calculateSessionTimeout(): Long {
            val prefs = FocusGuardApp.instance.prefs
            val attempt = prefs.dailyAttemptCount
            val exploits = prefs.timeoutExploitCount
            val baseMs = when {
                attempt <= 1 -> 3 * 60 * 1000L
                attempt == 2 -> 5 * 60 * 1000L
                attempt == 3 -> 10 * 60 * 1000L
                else -> 20 * 60 * 1000L
            }
            return baseMs + (exploits * 2 * 60 * 1000L)
        }
    }
}
