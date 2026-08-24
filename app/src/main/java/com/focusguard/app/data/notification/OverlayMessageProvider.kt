package com.focusguard.app.data.notification

import com.focusguard.app.domain.notification.OverlayMessageContext
import com.focusguard.app.domain.notification.OverlayMessageType
import kotlin.random.Random

class OverlayMessageProvider(
    private val random: Random = Random.Default
) {

    fun generate(context: OverlayMessageContext): String {
        val type = selectType(context)
        val subject = context.weakSubject ?: "Ye topic"
        return overlayBank.getValue(type)
            .random(random)
            .replace(SUBJECT_PLACEHOLDER, subject)
    }

    fun generateExitAttemptMessage(subject: String? = null): String {
        return exitAttemptBank
            .random(random)
            .replace(SUBJECT_PLACEHOLDER, subject ?: "Aaj ka topic")
    }

    private fun selectType(context: OverlayMessageContext): OverlayMessageType {
        return when {
            context.useUltraShort -> OverlayMessageType.ULTRA_SHORT
            context.hasWrongStreak -> OverlayMessageType.MISTAKE
            context.weakSubject != null -> OverlayMessageType.PERSONAL_PUSH
            context.isComeback -> OverlayMessageType.COMEBACK
            else -> listOf(
                OverlayMessageType.QUICK_CHALLENGE,
                OverlayMessageType.REALITY_CHECK,
                OverlayMessageType.LIGHT_TEASE
            ).random(random)
        }
    }

    companion object {
        private const val SUBJECT_PLACEHOLDER = "\$subject"

        private val overlayBank = mapOf(
            OverlayMessageType.QUICK_CHALLENGE to listOf(
                "One question first.",
                "30 seconds. One answer.",
                "Solve one PYQ, then continue.",
                "Start with one attempt.",
                "One quick check before access."
            ),
            OverlayMessageType.REALITY_CHECK to listOf(
                "Focus mode is active.",
                "Return to the plan.",
                "This app is blocked right now.",
                "Stay with the session.",
                "Pause before opening this."
            ),
            OverlayMessageType.PERSONAL_PUSH to listOf(
                "$SUBJECT_PLACEHOLDER needs one review.",
                "Check today's target first.",
                "Continue the plan you set.",
                "This is study time.",
                "One focused step now."
            ),
            OverlayMessageType.LIGHT_TEASE to listOf(
                "Try the question.",
                "Small step first.",
                "One answer is enough.",
                "Keep it simple.",
                "Continue the focus check."
            ),
            OverlayMessageType.MISTAKE to listOf(
                "Review the repeated mistake.",
                "This topic needs one more pass.",
                "Fix the pattern now.",
                "Slow down and answer carefully.",
                "Do not skip this weak area."
            ),
            OverlayMessageType.COMEBACK to listOf(
                "Resume with one question.",
                "Rebuild the flow.",
                "One PYQ can restart momentum.",
                "Start simple.",
                "Come back to the session."
            ),
            OverlayMessageType.ULTRA_SHORT to listOf(
                "One question.",
                "Focus.",
                "Try once.",
                "Do it now.",
                "Stay here."
            )
        )

        private val exitAttemptBank = listOf(
            "Exit protection is active.",
            "Focus mode is still running.",
            "Return to the study session.",
            "This app remains protected.",
            "One more calm step.",
            "$SUBJECT_PLACEHOLDER can wait.",
            "Close settings and continue."
        )
    }
}
