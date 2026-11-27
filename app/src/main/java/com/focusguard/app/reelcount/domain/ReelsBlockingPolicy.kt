package com.focusguard.app.reelcount.domain

/**
 * Source of an Instagram Reels blocking decision.
 */
enum class ReelsBlockSource {
    NONE,
    FOCUS_GUARD,
    REEL_COUNT_LIMIT
}

data class ReelsBlockDecision(
    val shouldBlock: Boolean,
    val source: ReelsBlockSource,
    val title: String,
    val message: String
)

/**
 * Combines Focus Guard's independent protection policy (Policy A) and Reel Count's
 * daily-limit policy (Policy B) without allowing either to weaken the other.
 *
 * Rule: FinalBlockDecision = FocusGuardRequiresBlock || ReelCountRequiresBlock
 */
object ReelsBlockingPolicy {

    fun evaluate(
        focusGuardRequiresBlock: Boolean,
        reelCountEnabled: Boolean,
        reelCountLimitEnabled: Boolean,
        todayDateKey: String,
        limitState: ReelCountLimitState
    ): ReelsBlockDecision {
        // Policy A always takes precedence and cannot be bypassed by Reel Count.
        if (focusGuardRequiresBlock) {
            return ReelsBlockDecision(
                shouldBlock = true,
                source = ReelsBlockSource.FOCUS_GUARD,
                title = "Instagram Reels",
                message = "Reels are blocked during focus hours. Feed, DMs, and stories can stay open."
            )
        }

        // Policy B applies only when Reel Count and its daily limit are enabled and BLOCK_TODAY is active for today.
        val reelCountBlocksToday = reelCountEnabled &&
            reelCountLimitEnabled &&
            limitState.dateKey == todayDateKey &&
            limitState.status == ReelCountLimitStatus.BLOCK_TODAY

        if (reelCountBlocksToday) {
            return ReelsBlockDecision(
                shouldBlock = true,
                source = ReelsBlockSource.REEL_COUNT_LIMIT,
                title = "Daily Reels Limit",
                message = "You chose to block Reels after reaching today's limit. Resets at midnight."
            )
        }

        return ReelsBlockDecision(
            shouldBlock = false,
            source = ReelsBlockSource.NONE,
            title = "",
            message = ""
        )
    }
}
