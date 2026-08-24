package com.focusguard.app.friction.layers

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.R
import com.focusguard.app.friction.tasks.*
import com.focusguard.app.integration.studyflow.StudyFlowOverlayRenderer
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * LAYER 2: Cognitive Tasks — PYQ Quiz + Motivating Lines
 *
 * Shows a motivating line at the top, then a PYQ below it.
 * ON CORRECT → Advance deeper into friction.
 * ON WRONG → Show explanation for 8 seconds, then restart pipeline.
 */
class CognitiveTaskLayer : FrictionLayer {

    override val name = "Cognitive Task"

    override suspend fun execute(context: FrictionContext): FrictionResult {
        val taskCount = calculateTaskCount(context.escalationLevel)
        val difficulty = (context.escalationLevel + 1).coerceAtMost(4)

        for (i in 0 until taskCount) {
            val task = selectRandomTask(difficulty)
            val result = executeTask(task, context, i + 1, taskCount)
            if (result != FrictionResult.Passed) {
                return result
            }
        }

        return FrictionResult.Passed
    }

    private fun selectRandomTask(difficulty: Int): CognitiveTask {
        return ExamQuestionTask(difficulty)
    }

    private suspend fun executeTask(
        task: CognitiveTask,
        context: FrictionContext,
        taskNumber: Int,
        totalTasks: Int
    ): FrictionResult {
        val challenge = task.generate()

        return withContext(Dispatchers.Main) {
            val view = context.overlayView

            val primaryText = view.findViewById<TextView>(R.id.text_primary_message)
            val secondaryText = view.findViewById<TextView>(R.id.text_secondary_message)
            val inputField = view.findViewById<EditText>(R.id.input_answer)
            val submitButton = view.findViewById<Button>(R.id.btn_submit)
            val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
            val countdownText = view.findViewById<TextView>(R.id.text_countdown)
            val taskContainer = view.findViewById<FrameLayout>(R.id.task_container)
            val attemptInfo = view.findViewById<TextView>(R.id.text_attempt_info)

            // Hide unused elements
            progressBar?.visibility = View.GONE
            countdownText?.visibility = View.GONE

            // Show motivating line at top
            val motiveLine = MOTIVATING_LINES[Random.nextInt(MOTIVATING_LINES.size)]
            primaryText?.apply {
                text = motiveLine
                setTextColor(0xFF34D399.toInt()) // Green
                textSize = 18f
            }

            // Show question
            secondaryText?.apply {
                text = challenge.question
                setTextColor(0xCCF0F0F5.toInt())
            }

            // Show attempt info
            attemptInfo?.text = "PYQ $taskNumber/$totalTasks"

            if (challenge.taskType == TaskType.MEMORY) {
                val safeTaskContainer = taskContainer
                if (safeTaskContainer == null) {
                    primaryText?.apply {
                        text = "Focus task unavailable"
                        setTextColor(0xFFFF4D6D.toInt())
                    }
                    secondaryText?.text = "Close the distracting app and return to your study session."
                    return@withContext FrictionResult.Error("Focus task unavailable")
                }

                safeTaskContainer.visibility = View.VISIBLE
                inputField?.visibility = View.GONE
                submitButton?.visibility = View.GONE
                return@withContext executeMemoryTask(
                    challenge, safeTaskContainer, context
                )
            }

            StudyFlowOverlayRenderer.render(
                container = taskContainer,
                snapshot = FocusGuardApp.instance.prefs.getStudyFlowDaySnapshot()
            )
            val optionsContainer = view.findViewById<LinearLayout>(R.id.options_container)
            
            val result = CompletableDeferred<FrictionResult>()

            // Shared handler for answering (from button or edittext)
            val handleAnswer: (String) -> Unit = { userAnswer: String ->
                val isCorrect = challenge.checkAnswer(userAnswer)
                OverlayPyqAttemptLogger.logAnswer(
                    scope = context.coroutineScope,
                    challenge = challenge,
                    selectedOption = userAnswer,
                    blockedPackage = context.blockedPackage
                )

                if (isCorrect) {
                    val nextLine = FOLLOW_UP_MESSAGES[Random.nextInt(FOLLOW_UP_MESSAGES.size)]
                    primaryText?.apply {
                        text = nextLine
                        setTextColor(0xFFFFB347.toInt()) // Amber
                    }
                    secondaryText?.text = "Good. Continue the focus check."
                    
                    submitButton?.isEnabled = false
                    inputField?.isEnabled = false
                    listOf("A", "B", "C", "D").forEach { 
                        val bid = when(it) {
                            "A" -> R.id.btn_option_a
                            "B" -> R.id.btn_option_b
                            "C" -> R.id.btn_option_c
                            else -> R.id.btn_option_d
                        }
                        view.findViewById<Button>(bid)?.isEnabled = false 
                    }

                    context.coroutineScope.launch {
                        delay(1800)
                        withContext(Dispatchers.Main) {
                            secondaryText?.text = "Good. Continue."
                        }
                        result.complete(FrictionResult.Passed)
                    }
                } else {
                    // Wrong answer: show solution, then restart.
                    primaryText?.apply {
                        text = "Not quite - the answer is ${challenge.answer}"
                        setTextColor(0xFFFF4D6D.toInt())
                    }

                    val explanation = challenge.explanation
                    if (!explanation.isNullOrBlank()) {
                        secondaryText?.apply {
                            text = "Explanation:\n\n$explanation"
                            setTextColor(0xFFBBBBBB.toInt())
                        }
                    }

                    submitButton?.isEnabled = false
                    inputField?.isEnabled = false
                    listOf("A", "B", "C", "D").forEach { 
                        val bid = when(it) {
                            "A" -> R.id.btn_option_a
                            "B" -> R.id.btn_option_b
                            "C" -> R.id.btn_option_c
                            else -> R.id.btn_option_d
                        }
                        view.findViewById<Button>(bid)?.isEnabled = false 
                    }

                    context.coroutineScope.launch {
                        delay(8000)
                        withContext(Dispatchers.Main) {
                            primaryText?.apply {
                                text = "Let's try one more time."
                                setTextColor(0xFFFF4D6D.toInt())
                            }
                            secondaryText?.text = ""
                        }
                        delay(2000)
                        withContext(Dispatchers.Main) {
                            primaryText?.setTextColor(0xFFF0F0F5.toInt())
                        }
                        result.complete(FrictionResult.Restart)
                    }
                }
            }

            if (challenge.taskType == TaskType.EXAM_QUESTION) {
                // Show MCQ Buttons
                inputField?.visibility = View.GONE
                submitButton?.visibility = View.GONE
                optionsContainer?.visibility = View.VISIBLE
                
                listOf(
                    "A" to R.id.btn_option_a,
                    "B" to R.id.btn_option_b,
                    "C" to R.id.btn_option_c,
                    "D" to R.id.btn_option_d
                ).forEach { (option, btnId) ->
                    view.findViewById<Button>(btnId)?.apply {
                        visibility = View.VISIBLE
                        isEnabled = true
                        setOnClickListener {
                            handleAnswer(option)
                        }
                    }
                }
            } else {
                // Show text input
                optionsContainer?.visibility = View.GONE
                inputField?.apply {
                    visibility = View.VISIBLE
                    setText("")
                    hint = if (challenge.taskType == TaskType.TYPING) {
                        "Type the sentence exactly..."
                    } else {
                        "Enter your answer..."
                    }
                    inputType = if (challenge.taskType == TaskType.MATH) {
                        android.text.InputType.TYPE_CLASS_NUMBER or
                                android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    } else {
                        android.text.InputType.TYPE_CLASS_TEXT
                    }
                    isEnabled = true
                    requestFocus()
                }
                submitButton?.apply {
                    visibility = View.VISIBLE
                    text = "Submit Answer"
                    isEnabled = true
                    setOnClickListener {
                        val userAnswer = inputField?.text?.toString()?.trim() ?: ""
                        handleAnswer(userAnswer)
                    }
                }
            }

            withTimeoutOrNull(TASK_TIMEOUT_MS) {
                result.await()
            } ?: FrictionResult.Abandoned
        }
    }

    private fun setupTypingFeedback(editText: EditText, expectedText: String) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val typed = s?.toString() ?: ""
                if (typed.isEmpty()) return
                val isCorrectSoFar = typed.length <= expectedText.length &&
                        expectedText.startsWith(typed)
                editText.setTextColor(
                    if (isCorrectSoFar) 0xFFF0F0F5.toInt()
                    else 0xFFFF4D6D.toInt()
                )
            }
        })
    }

    private suspend fun executeMemoryTask(
        challenge: TaskChallenge,
        container: FrameLayout,
        context: FrictionContext
    ): FrictionResult {
        return FrictionResult.Passed
    }

    private fun calculateTaskCount(escalationLevel: Int): Int {
        return when (escalationLevel) {
            0 -> 1
            1 -> Random.nextInt(1, 3)
            2 -> Random.nextInt(2, 4)
            else -> Random.nextInt(3, 5)
        }
    }

    companion object {
        private const val TASK_TIMEOUT_MS = 45_000L

        /**
         * Focus lines shown at the top when a PYQ appears.
         */
        private val MOTIVATING_LINES = listOf(
            "Focus mode is active.",
            "This app is blocked right now.",
            "Solve one PYQ.",
            "Stay with your study plan.",
            "A short pause protects the session.",
            "Answer calmly, then return to study.",
            "Keep the promise you made to yourself.",
            "One question before anything else."
        )

        /**
         * Follow-up messages shown on correct answer.
         */
        private val FOLLOW_UP_MESSAGES = listOf(
            "Correct. Focus mode stays active.",
            "Good answer. Continue the check.",
            "Correct. Return to the plan.",
            "Good. One step closer to study.",
            "Correct. Keep the session clean.",
        )
    }
}
