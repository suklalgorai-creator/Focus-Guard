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
                "Bas 1 question. Itna toh kar sakte ho.",
                "30 sec. Ek answer. Deal?",
                "Ek PYQ solve karo... fir jo chahe karo.",
                "Sirf ek attempt. Phir distraction allowed.",
                "Abhi ek question... ya seedha scroll?"
            ),
            OverlayMessageType.REALITY_CHECK to listOf(
                "Scroll karne se rank nahi aayega.",
                "Tum yaha padhne aaye the... ya bhatakne?",
                "Ye habit tumhe kaha le ja rahi hai?",
                "Distraction easy hai... result nahi.",
                "Sach me ye karna zaroori hai abhi?"
            ),
            OverlayMessageType.PERSONAL_PUSH to listOf(
                "$SUBJECT_PLACEHOLDER weak hai... aur tum yaha?",
                "Aaj ka target complete hua kya?",
                "Kal jo plan kiya tha... wo ho gaya?",
                "Ye time tumhara strongest hona chahiye.",
                "Abhi skip kiya... toh baad me regret."
            ),
            OverlayMessageType.LIGHT_TEASE to listOf(
                "Avoid kar rahe ho... ya try karoge?",
                "Distraction strong hai... tum usse strong ho?",
                "Dar lag raha hai question se?",
                "Ya toh solve karo... ya accept karo ki avoid kar rahe ho.",
                "Sach bolo... effort se bach rahe ho?"
            ),
            OverlayMessageType.MISTAKE to listOf(
                "Same mistake repeat ho rahi hai... abhi fix karo.",
                "Ye topic clear nahi hai... aur tum skip kar rahe ho?",
                "Galti wahi ho rahi hai... solution bhi wahi hai.",
                "Abhi nahi sudhara... toh exam me repeat hoga.",
                "Ye weak area ignore mat karo."
            ),
            OverlayMessageType.COMEBACK to listOf(
                "Flow break ho gaya... wapas aa jao.",
                "Momentum lose mat hone do.",
                "Bas ek question... fir flow aa jayega.",
                "Restart karna tough nahi hai... bas start karo.",
                "Abhi turn around ho sakta hai."
            ),
            OverlayMessageType.ULTRA_SHORT to listOf(
                "1 question. Now.",
                "Face it.",
                "Avoid mat karo.",
                "Do it.",
                "Abhi."
            )
        )

        private val exitAttemptBank = listOf(
            "Nice try 😏",
            "Escape attempt detected.",
            "So... we're quitting now?",
            "Itna jaldi haar maan li?",
            "Distraction choose kar rahe ho... consciously?",
            "Ye decision tum future wale tumhe explain kar paoge?",
            "Abhi quit karna easy hai... baad me regret harder hoga.",
            "Kal jo plan banaya tha... wo yaad hai?",
            "$SUBJECT_PLACEHOLDER weak hai... aur tum app hata rahe ho?",
            "Ye exit nahi... reset hona chahiye.",
            "Itna bhi tough nahi tha... phir bhi?",
            "Question se darr lag gaya kya?",
            "Avoid karna easy lag raha hai?"
        )
    }
}
