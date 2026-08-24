package com.focusguard.app.data.notification

import com.focusguard.app.domain.notification.NotificationType
import kotlin.random.Random

class SmartMessageProvider(
    private val random: Random = Random.Default
) {

    fun generate(
        type: NotificationType,
        subject: String? = null,
        avoidMessage: String? = null
    ): String {
        val templates = messageBank.getValue(type)
        val template = templates
            .filterNot { it == avoidMessage }
            .ifEmpty { templates }
            .random(random)

        return template.replace(SUBJECT_PLACEHOLDER, subject ?: "Aaj ka topic")
    }

    companion object {
        private const val SUBJECT_PLACEHOLDER = "\$subject"

        private val messageBank = mapOf(
            NotificationType.PRAISE to listOf(
                "$SUBJECT_PLACEHOLDER progress is improving.",
                "Good streak. Keep it steady.",
                "Accuracy is clean. Stay consistent.",
                "Momentum is building.",
                "Today has been focused so far.",
                "$SUBJECT_PLACEHOLDER is getting clearer."
            ),
            NotificationType.STRUGGLE to listOf(
                "$SUBJECT_PLACEHOLDER needs one more pass.",
                "Same mistake repeated. Review it calmly.",
                "Try one slower attempt.",
                "This topic needs practice, not pressure.",
                "Fix the pattern while it is fresh.",
                "Small correction now saves time later."
            ),
            NotificationType.COMEBACK to listOf(
                "Start with one PYQ.",
                "Restart simple: one question.",
                "Come back with a short session.",
                "One clean attempt is enough to resume.",
                "Your next step is small.",
                "Open the plan and continue."
            ),
            NotificationType.BLOCK_TRIGGER to listOf(
                "Blocked app detected. Solve one PYQ first.",
                "Focus mode is active.",
                "One answer before access.",
                "Return to the study session.",
                "This app is blocked during focus time.",
                "$SUBJECT_PLACEHOLDER can use one quick review."
            ),
            NotificationType.IMPROVEMENT to listOf(
                "$SUBJECT_PLACEHOLDER is improving.",
                "Better than last time. Keep going.",
                "Progress is visible.",
                "Good direction. Build the streak.",
                "Small improvement counts.",
                "Today's effort becomes tomorrow's confidence."
            )
        )
    }
}
