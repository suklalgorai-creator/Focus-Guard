package com.focusguard.app.friction.layers

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.focusguard.app.R
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * LAYER 4: Focus Check Reset
 *
 * Randomly restarts the flow after the user completes a step.
 * Copy stays honest so the blocker does not look broken or scammy.
 *
 * Failure probability scales with escalation:
 * - Level 0: 10% chance
 * - Level 1: 20% chance
 * - Level 2: 30% chance
 * - Level 3+: 35% chance
 *
 * Random success reduces the reward loop without pretending an external system failed.
 */
class FailureInjectionLayer : FrictionLayer {

    override val name = "Focus Check Reset"

    private val restartReasons = listOf(
        RestartReason(
            "Focus Check Reset",
            "Focus mode is still active.\nRestarting the check.",
            delayMs = 3000
        ),
        RestartReason(
            "Pause Extended",
            "Taking a little more time before access.\nPlease start over.",
            delayMs = 2500
        ),
        RestartReason(
            "Intent Check Needed",
            "Your session needs one more focus check.\nRestarting now.",
            delayMs = 4000
        ),
        RestartReason(
            "Block Still Active",
            "The blocked app is still within focus time.\nPlease try again.",
            delayMs = 2000
        ),
        RestartReason(
            "Another Step Required",
            "One more step is required before leaving focus mode.",
            delayMs = 3500
        ),
        RestartReason(
            "Session Still Locked",
            "Focus Guard is keeping this session locked.",
            delayMs = 3000
        ),
        RestartReason(
            "Cooling Down",
            "Too many distraction attempts.\nWait and try again.",
            delayMs = 5000
        ),
        RestartReason(
            "Progress Reset",
            "The check is restarting so you can return to focus.",
            delayMs = 4000
        ),
    )

    override suspend fun execute(context: FrictionContext): FrictionResult {
        if (context.attemptNumber <= 1) {
            return FrictionResult.Passed
        }

        val failureChance = calculateFailureChance(context.escalationLevel)

        if (Random.nextFloat() > failureChance) {
            return FrictionResult.Passed
        }

        val reason = restartReasons[Random.nextInt(restartReasons.size)]

        return withContext(Dispatchers.Main) {
            val view = context.overlayView

            val primaryText = view.findViewById<TextView>(R.id.text_primary_message)
            val secondaryText = view.findViewById<TextView>(R.id.text_secondary_message)
            val progressBar = view.findViewById<ProgressBar>(R.id.progress_bar)

            // Hide task elements
            view.findViewById<View>(R.id.task_container)?.visibility = View.GONE
            view.findViewById<View>(R.id.input_answer)?.visibility = View.GONE
            view.findViewById<View>(R.id.btn_submit)?.visibility = View.GONE
            view.findViewById<View>(R.id.text_countdown)?.visibility = View.GONE

            // Show reset state.
            progressBar?.apply {
                visibility = View.VISIBLE
                max = 100
                progress = 100
            }

            primaryText?.text = "Focus check in progress..."
            primaryText?.setTextColor(0xFF4CAF50.toInt())
            delay(1500)

            primaryText?.text = reason.title
            primaryText?.setTextColor(0xFFE94560.toInt())
            secondaryText?.text = reason.message
            progressBar?.progress = 0

            delay(reason.delayMs)

            // Reset colors
            primaryText?.setTextColor(0xFFECEFF4.toInt())
            progressBar?.visibility = View.GONE

            FrictionResult.Restart
        }
    }

    private fun calculateFailureChance(escalationLevel: Int): Float {
        return when (escalationLevel) {
            0 -> 0.10f
            1 -> 0.20f
            2 -> 0.30f
            else -> 0.35f
        }
    }

    private data class RestartReason(
        val title: String,
        val message: String,
        val delayMs: Long
    )
}
