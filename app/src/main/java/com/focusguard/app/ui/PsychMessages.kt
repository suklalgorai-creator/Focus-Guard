package com.focusguard.app.ui

import com.focusguard.app.FocusGuardApp
import java.util.Calendar

/**
 * Compact overlay copy. Keep friction useful without turning the blocker into
 * a wall of text.
 */
object PsychMessages {

    private fun examName(): String =
        FocusGuardApp.instance.prefs.targetExam.uppercase()

    fun getRandomMessage(attemptNumber: Int, daysUntilExam: Int): String {
        return getMotivationMessage(
            attemptCount = attemptNumber,
            streakDays = FocusGuardApp.instance.prefs.focusStreakDays,
            examName = examName(),
            daysLeft = daysUntilExam
        )
    }

    fun getDelayMessage(escalationLevel: Int): String {
        val pool = when (toneIndex(escalationLevel)) {
            0 -> calmMessages
            1 -> dataMessages
            2 -> neutralMessages
            else -> firmMessages
        }
        return pool.pickForToday(escalationLevel)
            .applyPlaceholders(
                attemptCount = FocusGuardApp.instance.prefs.dailyAttemptCount,
                streakDays = FocusGuardApp.instance.prefs.focusStreakDays,
                examName = examName(),
                daysLeft = FocusGuardApp.instance.prefs.getDaysUntilExam()
            )
    }

    fun getDenialMessage(
        attemptCount: Int = 0,
        streakDays: Int = 0,
        examName: String = "NEET",
        daysLeft: Int = 0
    ): String {
        val pool = when (toneIndex(attemptCount)) {
            0 -> calmMessages
            1 -> dataMessages
            2 -> neutralMessages
            else -> firmMessages
        }
        return pool.pickForToday(attemptCount)
            .applyPlaceholders(attemptCount, streakDays, examName, daysLeft)
    }

    fun getMotivationMessage(
        attemptCount: Int = 0,
        streakDays: Int = 0,
        examName: String = "NEET",
        daysLeft: Int = 0
    ): String {
        return calmMessages.pickForToday(attemptCount)
            .applyPlaceholders(attemptCount, streakDays, examName, daysLeft)
    }

    private fun toneIndex(attemptCount: Int): Int {
        return (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + attemptCount).mod(4)
    }

    private fun List<String>.pickForToday(seed: Int): String {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        return this[(day + seed).mod(size)]
    }

    private fun String.applyPlaceholders(
        attemptCount: Int,
        streakDays: Int,
        examName: String,
        daysLeft: Int
    ): String {
        val safeDaysLeft = daysLeft.takeIf { it >= 0 }?.toString() ?: "soon"
        return this
            .replace("{attemptCount}", attemptCount.toString())
            .replace("{streakDays}", streakDays.toString())
            .replace("{examName}", examName.uppercase())
            .replace("{daysLeft}", safeDaysLeft)
    }

    private val calmMessages = listOf(
        "Focus mode is active.",
        "Pause. Return to study.",
        "One question, then back.",
        "Stay with the plan.",
        "This urge will pass.",
        "Protect this session.",
        "Back to your task.",
        "Keep the day clean.",
        "Small reset. Continue.",
        "Choose study now."
    )

    private val dataMessages = listOf(
        "Attempt {attemptCount} today.",
        "{daysLeft} days to {examName}.",
        "{streakDays}-day focus streak.",
        "One tap can become 30 minutes.",
        "Save this study block.",
        "Your plan is still waiting.",
        "This is a focus checkpoint.",
        "Keep momentum.",
        "Do one PYQ now.",
        "Return before the feed starts."
    )

    private val firmMessages = listOf(
        "Not now.",
        "This app stays blocked.",
        "Close the loop.",
        "Do not start the feed.",
        "Study session first.",
        "Hold the line.",
        "Leave it here.",
        "Back to work.",
        "Protect your rank.",
        "Stop the scroll."
    )

    private val neutralMessages = listOf(
        "Blocked during focus mode.",
        "Session protected.",
        "Access paused.",
        "Focus window active.",
        "Study mode running.",
        "App blocked for now.",
        "Return to study.",
        "Focus check active.",
        "Timer active.",
        "Please wait."
    )

    val BYPASS_MESSAGES = listOf(
        "Exit protection active.",
        "Protection level increased.",
        "Strict Mode is running.",
        "Settings change blocked.",
        "Return to study."
    )
}
