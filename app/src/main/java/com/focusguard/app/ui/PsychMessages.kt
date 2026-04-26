package com.focusguard.app.ui

import com.focusguard.app.FocusGuardApp
import kotlin.random.Random

/**
 * Confrontational, non-motivational messages designed to trigger
 * cognitive dissonance and make the user question their choice.
 *
 * These are NOT supportive or encouraging.
 * They are blunt, slightly aggressive, and rooted in reality.
 *
 * All exam-specific references use the dynamic exam name from prefs.
 */
object PsychMessages {

    private fun examName(): String =
        FocusGuardApp.instance.prefs.targetExam.uppercase()

    fun getRandomMessage(attemptNumber: Int, daysUntilExam: Int): String {
        val template = ALL_MESSAGES[Random.nextInt(ALL_MESSAGES.size)]
        return template
            .replace("%attempt%", attemptNumber.toString())
            .replace("%days%", if (daysUntilExam > 0) daysUntilExam.toString() else "???")
            .replace("%exam%", examName())
    }

    fun getDelayMessage(escalationLevel: Int): String {
        return DELAY_MESSAGES[Random.nextInt(DELAY_MESSAGES.size)]
    }

    fun getDenialMessage(): String {
        return DENIAL_MESSAGES[Random.nextInt(DENIAL_MESSAGES.size)]
    }

    private val ALL_MESSAGES = listOf(
        // Confrontational
        "You chose distraction again. Continue?",
        "This is attempt #%attempt% today. See the pattern?",
        "Every minute here is a minute stolen from your future.",
        "You know this is a waste. Your brain is lying to you.",
        "You're not relaxing. You're avoiding.",
        "Your competition is studying right now.",

        // Exam-specific (dynamic)
        "%exam% is in %days% days. Instagram won't help.",
        "30 minutes of Instagram = 0 MCQs solved.",
        "%days% days left. Is this reel worth it?",
        "Your %exam% rank doesn't care about your Instagram stories.",
        "Toppers didn't scroll reels during their prep.",

        // Emotional
        "The craving will pass in 10 minutes. The regret won't.",
        "You'll close Instagram feeling worse. You always do.",
        "How many reels until you feel better? (Hint: it's never enough)",
        "This is dopamine hunger, not a real need.",
        "Future you is watching. Make them proud.",
        "Would you show your screen time to your parents right now?",

        // Logical
        "You installed this app yourself. Trust past-you's judgment.",
        "The algorithm profits from your wasted time. Don't be the product.",
        "10 minutes of studying > 60 minutes of reels. Always.",
        "Name one reel that improved your life. I'll wait.",

        // Aggressive
        "Again? Really? Attempt #%attempt% in one day.",
        "You spent more time fighting this app than it takes to solve 5 MCQs.",
        "This is exactly why your screen time report looks the way it does.",
        "Discipline is doing what needs to be done when you don't feel like it.",
        "Instagram doesn't care about your %exam% score. Neither will your regret.",

        // Reality check
        "The average person wastes 2.5 hours daily on social media. You're trying to join that statistic.",
        "Every attempt you waste studying less costs your family another year of stress.",
        "You're choosing 15 seconds of dopamine over 15 years of career satisfaction.",
        "In 5 years, you won't remember a single reel. You WILL remember your %exam% score.",
    )

    private val DELAY_MESSAGES = listOf(
        "Patience is part of discipline.",
        "If you can't wait 30 seconds, how will you sit through a 3-hour exam?",
        "The wait is the point. Sit with the discomfort.",
        "Every second you wait here is a second you're NOT wasting on reels.",
        "This timer is shorter than the regret you'd feel after scrolling.",
        "Use this time to breathe. Then go study.",
        "The urgency you feel is manufactured by dopamine. It's not real.",
    )

    private val DENIAL_MESSAGES = listOf(
        "Access denied. The system decided you don't need this right now.",
        "Not this time. Maybe try studying instead?",
        "Request rejected. Your future self thanks us.",
        "Denied. Take it as a sign from the universe.",
        "No access. Close this and open your study material.",
        "Blocked. You've spent more time here than it takes to revise a chapter.",
        "The odds said no. Your textbook says yes. Choose wisely.",
    )

    // Messages shown when user tries to bypass via Settings
    val BYPASS_MESSAGES = listOf(
        "Nice try. Friction increased for next attempt. 🙂",
        "Detected: bypass attempt. Penalty applied.",
        "You're trying harder to open Instagram than to study. Think about that.",
        "Disabling this app won't disable your addiction.",
        "The fact that you're here proves the app is working.",
    )
}
