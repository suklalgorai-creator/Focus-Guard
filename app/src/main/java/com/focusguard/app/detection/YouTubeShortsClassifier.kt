package com.focusguard.app.detection

/**
 * Pure-Kotlin classifier for YouTube Shorts vs. normal YouTube videos/feeds.
 *
 * Prevents false positives on regular YouTube watch pages (which always show "Shorts"
 * in the bottom navigation bar alongside "Subscribe", "Comments", and "@handle") and on
 * Home/Search Shorts shelves.
 */
enum class YouTubeShortsClassification {
    SHORTS_CONFIRMED,
    NOT_SHORTS,
    PRODUCTIVE_ALLOWED
}

data class YouTubeHierarchySignals(
    val viewIds: Set<String> = emptySet(),
    val texts: List<String> = emptyList(),
    val selectedLabels: List<String> = emptyList(),
    val hasVerticalPager: Boolean = false,
    val productiveChannels: Set<String> = emptySet(),
    val distractingChannels: Set<String> = emptySet(),
    val nodeCount: Int = 0
)

data class YouTubeShortsResult(
    val classification: YouTubeShortsClassification,
    val matchedChannel: String? = null,
    val reason: String
)

class YouTubeShortsClassifier {

    fun classify(signals: YouTubeHierarchySignals): YouTubeShortsResult {
        if (signals.nodeCount <= 0) {
            return YouTubeShortsResult(
                classification = YouTubeShortsClassification.NOT_SHORTS,
                reason = "Empty hierarchy"
            )
        }

        val hasRegularVideoPlayer = signals.viewIds.any { id ->
            REGULAR_VIDEO_PLAYER_IDS.any(id::contains)
        }
        val hasFeedOrSearchContext = signals.viewIds.any { id ->
            FEED_OR_SEARCH_IDS.any(id::contains)
        }

        val hasActiveShortsPlayerId = signals.viewIds.any { id ->
            ACTIVE_SHORTS_PLAYER_IDS.any(id::contains) && !FEED_SHELF_IDS.any(id::contains)
        }
        val hasSelectedShortsTab = signals.selectedLabels.any { label ->
            label == "shorts" || (label.contains("shorts") && label.contains("selected"))
        }
        val hasShortsViewerControls = signals.texts.any { text ->
            text == "remix" || text == "original sound" || text.contains("remix this short")
        }

        val isShortsPlayer = when {
            hasActiveShortsPlayerId -> true
            hasRegularVideoPlayer || hasFeedOrSearchContext -> false
            hasSelectedShortsTab && (signals.hasVerticalPager || hasShortsViewerControls) -> true
            else -> false
        }

        if (!isShortsPlayer) {
            return YouTubeShortsResult(
                classification = YouTubeShortsClassification.NOT_SHORTS,
                reason = if (hasRegularVideoPlayer) {
                    "Regular YouTube video player active"
                } else {
                    "No active YouTube Shorts player signals"
                }
            )
        }

        val productiveMatch = matchConfiguredChannel(signals.texts, signals.productiveChannels)
        if (productiveMatch != null) {
            return YouTubeShortsResult(
                classification = YouTubeShortsClassification.PRODUCTIVE_ALLOWED,
                matchedChannel = productiveMatch,
                reason = "Allowed productive channel: $productiveMatch"
            )
        }

        val distractingMatch = matchConfiguredChannel(signals.texts, signals.distractingChannels)
        return YouTubeShortsResult(
            classification = YouTubeShortsClassification.SHORTS_CONFIRMED,
            matchedChannel = distractingMatch,
            reason = "Active YouTube Shorts player detected"
        )
    }

    private fun matchConfiguredChannel(
        observedTexts: List<String>,
        configuredChannels: Set<String>
    ): String? {
        if (configuredChannels.isEmpty()) return null

        val normalizedTexts = observedTexts
            .map(::normalizeChannelToken)
            .filter { it.length >= 3 }

        return configuredChannels.firstOrNull { rule ->
            val normalizedRule = normalizeChannelToken(rule)
            normalizedRule.length >= 3 && normalizedTexts.any { text ->
                text == normalizedRule ||
                    text.contains(normalizedRule) ||
                    normalizedRule.contains(text)
            }
        }
    }

    private fun normalizeChannelToken(value: String): String {
        return value.lowercase()
            .replace(Regex("[^a-z0-9@ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private val ACTIVE_SHORTS_PLAYER_IDS = listOf(
            "reel_watch_fragment",
            "reel_player_page",
            "shorts_player",
            "shorts_video",
            "reel_recycler"
        )

        private val FEED_SHELF_IDS = listOf(
            "reel_shelf",
            "shorts_shelf",
            "shorts_lockup",
            "reel_item"
        )

        private val REGULAR_VIDEO_PLAYER_IDS = listOf(
            "watch_while_time_bar",
            "time_bar",
            "player_control_play_pause_replay_button",
            "fullscreen_button",
            "player_fragment_container"
        )

        private val FEED_OR_SEARCH_IDS = listOf(
            "search_edit_text",
            "search_results"
        )
    }
}
