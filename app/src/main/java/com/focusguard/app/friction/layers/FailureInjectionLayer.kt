package com.focusguard.app.friction.layers

import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.focusguard.app.R
import kotlinx.coroutines.*
import kotlin.random.Random

/**
 * LAYER 4: Intentional Failure Injection
 *
 * Randomly fails the user even after they've completed everything correctly.
 * Shows fake error messages and sends them back to restart the pipeline.
 *
 * Failure probability scales with escalation:
 * - Level 0: 20% chance
 * - Level 1: 35% chance
 * - Level 2: 45% chance
 * - Level 3+: 55% chance
 *
 * This is psychologically devastating because success feels random,
 * which destroys the reward anticipation loop.
 */
class FailureInjectionLayer : FrictionLayer {

    override val name = "Failure Injection"

    private val fakeErrors = listOf(
        FakeError(
            "Connection Lost",
            "Unable to verify your session. The connection was interrupted.\nRetrying from the beginning...",
            delayMs = 3000
        ),
        FakeError(
            "Session Expired",
            "Your verification session has timed out.\nPlease start over.",
            delayMs = 2500
        ),
        FakeError(
            "Server Timeout",
            "The verification server did not respond in time.\nReconnecting...",
            delayMs = 4000
        ),
        FakeError(
            "Verification Failed",
            "Your responses could not be validated.\nPlease try again.",
            delayMs = 2000
        ),
        FakeError(
            "Integrity Check Failed",
            "System detected an inconsistency in your session.\nRestarting verification...",
            delayMs = 3500
        ),
        FakeError(
            "Access Token Invalid",
            "Your access token was revoked.\nRe-authentication required.",
            delayMs = 3000
        ),
        FakeError(
            "Rate Limited",
            "Too many verification attempts. Please wait and try again.",
            delayMs = 5000
        ),
        FakeError(
            "Sync Error",
            "Failed to sync with the accountability server.\nRolling back progress...",
            delayMs = 4000
        ),
    )

    override suspend fun execute(context: FrictionContext): FrictionResult {
        val failureChance = calculateFailureChance(context.escalationLevel)

        // Roll the dice
        if (Random.nextFloat() > failureChance) {
            // Lucky — no failure injection this time
            return FrictionResult.Passed
        }

        // FAILURE TRIGGERED — show fake error
        val error = fakeErrors[Random.nextInt(fakeErrors.size)]

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

            // Show fake error with dramatic effect
            progressBar?.apply {
                visibility = View.VISIBLE
                max = 100
                progress = 100
            }

            // Brief "success" tease before error
            primaryText?.text = "Almost there..."
            primaryText?.setTextColor(0xFF4CAF50.toInt())
            delay(1500) // Build false hope

            // CRASH — show error
            primaryText?.text = "⚠ ${error.title}"
            primaryText?.setTextColor(0xFFE94560.toInt())
            secondaryText?.text = error.message
            progressBar?.progress = 0

            // Dramatic wait
            delay(error.delayMs)

            // Reset colors
            primaryText?.setTextColor(0xFFECEFF4.toInt())
            progressBar?.visibility = View.GONE

            FrictionResult.Restart // Send user back to beginning
        }
    }

    private fun calculateFailureChance(escalationLevel: Int): Float {
        return when (escalationLevel) {
            0 -> 0.20f
            1 -> 0.35f
            2 -> 0.45f
            else -> 0.55f
        }
    }

    private data class FakeError(
        val title: String,
        val message: String,
        val delayMs: Long
    )
}
