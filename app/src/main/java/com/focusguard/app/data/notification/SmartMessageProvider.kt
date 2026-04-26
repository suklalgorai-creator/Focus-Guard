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
                "Aaj toh tum full focus mode me ho... $SUBJECT_PLACEHOLDER handle ho raha hai 😏",
                "3 correct in a row... ye luck nahi hai.",
                "Sach batao... topper banne ka plan secretly chal raha hai?",
                "$SUBJECT_PLACEHOLDER tumse darr raha hai ab.",
                "Momentum bana liya hai... ab todna mat.",
                "Aaj discipline ka scene strong lag raha hai.",
                "Accuracy clean hai. Bas ab consistency chahiye.",
                "$SUBJECT_PLACEHOLDER me grip aa rahi hai... nice."
            ),
            NotificationType.STRUGGLE to listOf(
                "$SUBJECT_PLACEHOLDER thoda ignore ho raha hai... ya jaan ke avoid kar rahe ho?",
                "Same mistake fir se... coincidence nahi hai.",
                "Tum better ho isse. Ek baar aur try karo.",
                "Ye topic tumse bhaag nahi raha... tum bhaag rahe ho kya?",
                "Abhi fix kar lo... baad me regret mat karna.",
                "$SUBJECT_PLACEHOLDER ka weak spot dikh raha hai. Aaj hi patch karo.",
                "Galti repeat ho rahi hai. Signal clear hai.",
                "Thoda slow jao, par sahi samajh ke jao."
            ),
            NotificationType.COMEBACK to listOf(
                "Aaj thoda missing ho... sab theek hai?",
                "Kal ka flow break ho gaya... wapas aa jao.",
                "Bas ek question solve karo... fir decide karna continue karna hai ya nahi.",
                "Main wait kar raha hoon... ek PYQ bas.",
                "Itna silent rehna tumhari habit nahi hai...",
                "Comeback ka best time abhi hai.",
                "Flow wapas lane ke liye ek question enough hai.",
                "Chalo, restart simple rakhte hain: ek PYQ."
            ),
            NotificationType.BLOCK_TRIGGER to listOf(
                "Instagram baad me... ye question pehle 😉",
                "Escape kar rahe ho ya face karoge?",
                "Bas 30 sec. Ek answer. Deal?",
                "Scroll karne se rank nahi aayega... answer dene se aayega.",
                "Distraction strong hai... par tum usse strong ho ya nahi?",
                "Bas 1 question. Itna toh kar sakte ho.",
                "Ek PYQ solve karo... fir jo chahe karo.",
                "$SUBJECT_PLACEHOLDER weak hai... aur tum yaha?"
            ),
            NotificationType.IMPROVEMENT to listOf(
                "$SUBJECT_PLACEHOLDER improve ho raha hai... ab push karoge ya yahin rukoge?",
                "Kal se better ho... ye hi growth hai.",
                "Consistency aa rahi hai... ye dangerous sign hai (good one 😏)",
                "$SUBJECT_PLACEHOLDER me progress visible hai. Ab rukna mat.",
                "Small improvement bhi rank banata hai.",
                "Aaj ka effort kal ka confidence banega.",
                "Pattern change ho raha hai... good direction.",
                "Progress pakdi gayi. Ab isko streak banao."
            )
        )
    }
}
