package com.focusguard.app.friction.layers

import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.focusguard.app.R
import com.focusguard.app.friction.tasks.*
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * LAYER 2: Cognitive Tasks — PYQ Quiz + Motivating Lines
 *
 * Shows a motivating line at the top, then a PYQ below it.
 * ON CORRECT → Guilt message, force home. No access ever.
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
            attemptInfo?.text = "Solve to prove you should be studying · PYQ $taskNumber/$totalTasks"

            if (challenge.taskType == TaskType.MEMORY) {
                taskContainer?.visibility = View.VISIBLE
                inputField?.visibility = View.GONE
                submitButton?.visibility = View.GONE
                return@withContext executeMemoryTask(
                    challenge, taskContainer!!, context
                )
            }

            // Hide irrelevant containers
            taskContainer?.visibility = View.GONE
            val optionsContainer = view.findViewById<LinearLayout>(R.id.options_container)
            
            val result = CompletableDeferred<FrictionResult>()

            // Shared handler for answering (from button or edittext)
            val handleAnswer: (String) -> Unit = { userAnswer: String ->
                if (challenge.checkAnswer(userAnswer)) {
                    // CORRECT — Guilt trip + Force HOME
                    val taunt = GUILT_TRIP_MESSAGES[Random.nextInt(GUILT_TRIP_MESSAGES.size)]
                    primaryText?.apply {
                        text = taunt
                        setTextColor(0xFFFFB347.toInt()) // Amber
                    }
                    secondaryText?.text = "This app is locked. Go study."
                    
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
                        delay(3500)
                        withContext(Dispatchers.Main) {
                            primaryText?.apply {
                                text = "Redirecting to Home..."
                                setTextColor(0xFFFF4D6D.toInt())
                            }
                        }
                        delay(500)
                        com.focusguard.app.detection.AppDetectorService.instance?.forceHome()
                        result.complete(FrictionResult.Abandoned)
                    }
                } else {
                    // WRONG — Show solution, THEN restart
                    primaryText?.apply {
                        text = "✗ Wrong. Correct: ${challenge.answer}"
                        setTextColor(0xFFFF4D6D.toInt())
                    }

                    val explanation = challenge.explanation
                    if (!explanation.isNullOrBlank()) {
                        secondaryText?.apply {
                            text = "📖 Explanation:\n\n$explanation"
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
                                text = "Restarting..."
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
         * Motivating lines shown at the top when a PYQ appears.
         */
        private val MOTIVATING_LINES = listOf(
            "Your competitors are studying right now.",
            "Reels won't get you a medical seat.",
            "Is wasting time worth failing the exam?",
            "You committed to focus. Prove it.",
            "Every second here drops your rank.",
            "Future YOU is begging you to stop.",
            "Dopamine detox — feed your brain PYQs.",
            "Close this. Open your books.",
            "Distraction is the enemy of selection.",
            "You're better than this addiction.",
            "Your parents are working hard for you.",
            "89 din mein NEET hai. Padh le.",
            "This won't help you crack the exam.",
            "Phone rakh. Book utha. Abhi."
        )

        /**
         * Guilt-trip messages shown on correct answer.
         * No praise. No access. Just guilt.
         */
        private val GUILT_TRIP_MESSAGES = listOf(
            "Good. So why are you here wasting time?",
            "Right answer. Now go solve 50 more.",
            "You knew this. Why aren't you studying?",
            "Correct. Still no access. Go study.",
            "If you can solve PYQs, you don't need reels.",
            "Correct answer, wrong priorities.",
            "Your brain works. Use it for prep.",
            "That was easy. Your exam won't be.",
            "You just proved you can study. Go DO it.",
            "Sahi jawab. Ab ja ke padh le.",
            "Answer sahi hai. Phone rakh, book utha.",
        )
    }
}
