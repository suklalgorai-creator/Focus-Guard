package com.focusguard.app.friction.layers

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.focusguard.app.R
import com.focusguard.app.ui.PsychMessages
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * LAYER 1: Forced Delay
 *
 * Shows a countdown timer with an intentionally slow, jerky progress bar.
 * Duration scales with escalation level.
 * No skip. No way out except waiting.
 *
 * Escalation scaling:
 * - Level 0: 10-30 seconds
 * - Level 1: 30-60 seconds
 * - Level 2: 60-120 seconds
 * - Level 3+: 120-300 seconds
 */
class ForcedDelayLayer : FrictionLayer {

    override val name = "Forced Delay"

    override suspend fun execute(context: FrictionContext): FrictionResult {
        val delaySeconds = calculateDelay(context.escalationLevel)

        return withContext(Dispatchers.Main) {
            val view = context.overlayView

            val primaryText = view.findViewById<TextView>(R.id.text_primary_message)
            val secondaryText = view.findViewById<TextView>(R.id.text_secondary_message)
            val countdownText = view.findViewById<TextView>(R.id.text_countdown)
            val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)
            val attemptInfo = view.findViewById<TextView>(R.id.text_attempt_info)

            // Hide task elements
            view.findViewById<View>(R.id.task_container)?.visibility = View.GONE
            view.findViewById<View>(R.id.input_answer)?.visibility = View.GONE
            view.findViewById<View>(R.id.btn_submit)?.visibility = View.GONE

            // Show delay UI
            primaryText?.text = PsychMessages.getRandomMessage(
                context.attemptNumber,
                context.daysUntilExam
            )
            secondaryText?.text = "Wait for the timer to complete."
            countdownText?.visibility = View.VISIBLE
            progressBar?.apply {
                visibility = View.VISIBLE
                max = 1000
                progress = 0
            }
            attemptInfo?.text = "Attempt #${context.attemptNumber} today • Level ${context.escalationLevel}"

            // Run the countdown with intentionally jerky progress
            val totalMs = delaySeconds * 1000L
            val startTime = System.currentTimeMillis()
            var lastProgressUpdate = 0

            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= totalMs) break

                val remaining = ((totalMs - elapsed) / 1000).toInt()
                val progress = ((elapsed.toFloat() / totalMs) * 1000).toInt()

                // Update UI on main thread
                countdownText?.text = formatTime(remaining)

                // Intentionally jerky progress — sometimes pause or stutter
                val jerk = if (Random.nextFloat() < 0.3f) {
                    // 30% chance: progress bar freezes or goes backward slightly
                    (lastProgressUpdate - Random.nextInt(0, 20)).coerceAtLeast(0)
                } else {
                    progress
                }
                progressBar?.progress = jerk
                lastProgressUpdate = jerk

                // Random message changes during wait
                if (remaining % 15 == 0 && remaining > 0) {
                    primaryText?.text = PsychMessages.getRandomMessage(
                        context.attemptNumber,
                        context.daysUntilExam
                    )
                }

                delay(if (Random.nextFloat() < 0.2f) 1500L else 1000L) // Sometimes tick slower
            }

            // Final — fill progress
            progressBar?.progress = 1000
            countdownText?.text = "0:00"
            delay(500)

            countdownText?.visibility = View.GONE
            progressBar?.visibility = View.GONE

            FrictionResult.Passed
        }
    }

    private fun calculateDelay(escalationLevel: Int): Int {
        return when (escalationLevel) {
            0 -> Random.nextInt(10, 31)
            1 -> Random.nextInt(30, 61)
            2 -> Random.nextInt(60, 121)
            3 -> Random.nextInt(120, 301)
            else -> Random.nextInt(180, 600)
        }
    }

    private fun formatTime(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return String.format("%d:%02d", min, sec)
    }
}
