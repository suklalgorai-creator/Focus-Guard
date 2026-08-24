package com.focusguard.app.friction.layers

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.focusguard.app.R
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * LAYER 3: Randomized Waiting
 *
 * Shows a short, unpredictable pause before the next focus step.
 * Progress bar pacing is varied so the delay is harder to game.
 */
class RandomWaitLayer : FrictionLayer {

    override val name = "Random Wait"

    private val focusCheckMessages = listOf(
        "Pausing before access...",
        "Checking focus mode...",
        "Reviewing your schedule...",
        "Preparing the next step...",
        "Keeping the block active...",
        "Waiting briefly...",
        "Returning you to focus...",
    )

    override suspend fun execute(context: FrictionContext): FrictionResult {
        val waitSeconds = calculateWait(context.escalationLevel)

        return withContext(Dispatchers.Main) {
            val view = context.overlayView

            val primaryText = view.findViewById<TextView>(R.id.text_primary_message)
            val secondaryText = view.findViewById<TextView>(R.id.text_secondary_message)
            val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
            val countdownText = view.findViewById<TextView>(R.id.text_countdown)

            // Hide task elements
            view.findViewById<View>(R.id.task_container)?.visibility = View.GONE
            view.findViewById<View>(R.id.input_answer)?.visibility = View.GONE
            view.findViewById<View>(R.id.btn_submit)?.visibility = View.GONE
            countdownText?.visibility = View.GONE

            // Show loading state
            progressBar?.apply {
                visibility = View.VISIBLE
                max = 100
                progress = 0
            }

            val totalMs = waitSeconds * 1000L
            val startTime = System.currentTimeMillis()
            var currentProgress = 0
            var messageIndex = 0

            // Set initial message
            primaryText?.text = focusCheckMessages[0]
            secondaryText?.text = "Please wait..."

            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= totalMs) break

                // Varied progress pacing
                val action = Random.nextFloat()
                currentProgress = when {
                    action < 0.15f -> {
                        // 15%: Step back slightly
                        (currentProgress - Random.nextInt(3, 12)).coerceAtLeast(0)
                    }
                    action < 0.35f -> {
                        // 20%: Pause
                        currentProgress
                    }
                    action < 0.50f -> {
                        // 15%: Small jump
                        val jump = currentProgress + Random.nextInt(10, 25)
                        progressBar?.progress = jump.coerceAtMost(95)
                        delay(300)
                        (currentProgress + Random.nextInt(1, 3)).coerceAtMost(95)
                    }
                    else -> {
                        // 50%: Normal slow progress
                        (currentProgress + Random.nextInt(1, 4)).coerceAtMost(95)
                    }
                }

                progressBar?.progress = currentProgress

                // Cycle through focus messages
                if (Random.nextFloat() < 0.2f) {
                    messageIndex = (messageIndex + 1) % focusCheckMessages.size
                    primaryText?.text = focusCheckMessages[messageIndex]
                }

                // Random delay between ticks keeps timing unpredictable.
                delay(Random.nextLong(500, 2000))
            }

            // Finish
            progressBar?.progress = 100
            primaryText?.text = "Focus check complete."
            delay(500)
            progressBar?.visibility = View.GONE

            FrictionResult.Passed
        }
    }

    private fun calculateWait(escalationLevel: Int): Int {
        return when (escalationLevel) {
            0 -> Random.nextInt(5, 20)
            1 -> Random.nextInt(10, 30)
            2 -> Random.nextInt(15, 40)
            else -> Random.nextInt(20, 45)
        }
    }
}
