package com.focusguard.app.detection

/**
 * Confidence-based classification for Instagram Reels surface detection.
 */
enum class ReelsClassification {
    REELS_CONFIRMED,
    REELS_LIKELY,
    NOT_REELS,
    UNKNOWN
}

/**
 * Pure-Kotlin snapshot of accessibility signals extracted from an Instagram window.
 * Kept free of Android framework types so classification and stabilization are unit-testable.
 */
data class ReelsHierarchySignals(
    val packageName: String,
    val isWindowTransition: Boolean = false,
    val isSelectionEvent: Boolean = false,
    val windowClassName: String? = null,
    val viewIds: Set<String> = emptySet(),
    val texts: List<String> = emptyList(),
    val selectedLabels: List<String> = emptyList(),
    val hasVerticalPager: Boolean = false,
    val actionControlCount: Int = 0,
    val nodeCount: Int = 0,
    val wasTruncated: Boolean = false,
    val hasRootNode: Boolean = true
)

data class ReelsDetectionResult(
    val classification: ReelsClassification,
    val confidenceScore: Int,
    val evidenceSummary: String,
    val reason: String
)

/**
 * Multi-signal, confidence-based classifier for Instagram Reels.
 *
 * Design principles:
 * - Generic audio/engagement labels (e.g. "original audio", "likes", "comments") never trigger
 *   or contribute to a Reels classification on their own.
 * - Strong structural signals (Reels viewer resource IDs, selected Reels tab, Reels window class)
 *   and viewer action clusters are combined and penalized when non-Reels context (Feed, Explore,
 *   Profile, DMs, Stories) is present.
 * - Incomplete/truncated hierarchies without strong evidence resolve to UNKNOWN rather than guessing.
 */
class InstagramReelsClassifier {

    fun classify(signals: ReelsHierarchySignals): ReelsDetectionResult {
        if (!signals.hasRootNode || signals.nodeCount <= 0) {
            return ReelsDetectionResult(
                classification = ReelsClassification.UNKNOWN,
                confidenceScore = 0,
                evidenceSummary = "no_root_or_empty",
                reason = "Empty or unavailable accessibility hierarchy"
            )
        }

        val normalizedWindowClass = signals.windowClassName?.trim()?.lowercase().orEmpty()
        val hasClipsViewerId = signals.viewIds.any { id ->
            CLIPS_VIEWER_IDS.any(id::contains) && !isStoryViewId(id)
        }
        val hasSelectedReelsTab = signals.selectedLabels.any { label ->
            label == "reels" ||
                label == "reel" ||
                (label.contains("reels") && label.contains("selected")) ||
                label.contains("reels and short videos")
        }
        val hasReelsWindowClass = normalizedWindowClass.isNotEmpty() &&
            (normalizedWindowClass.contains("clips") || normalizedWindowClass.contains("reels")) &&
            !normalizedWindowClass.contains("story") &&
            !normalizedWindowClass.contains("stories")

        val hasViewerSpecificPhrase = signals.texts.any { text ->
            REELS_VIEWER_ONLY_PHRASES.any(text::contains)
        }
        val hasReelsAudioOrEffectId = signals.viewIds.any { id ->
            REELS_AUDIO_OR_EFFECT_IDS.any(id::contains)
        }
        val hasReelsActionCluster = signals.actionControlCount >= 2 ||
            (signals.actionControlCount >= 1 && (hasViewerSpecificPhrase || hasReelsAudioOrEffectId)) ||
            hasViewerSpecificPhrase
        val hasReelsLabel = signals.texts.any { it == "reels" || it == "reel" }

        val nonReelsReasons = detectNonReelsContext(signals)
        val hasNonReelsContext = nonReelsReasons.isNotEmpty()

        val evidence = mutableListOf<String>()
        var score = 0

        if (hasClipsViewerId) {
            score += SCORE_CLIPS_VIEWER_ID
            evidence += "clips_viewer_id"
        }
        if (hasSelectedReelsTab) {
            score += SCORE_SELECTED_REELS_TAB
            evidence += "selected_reels_tab"
        }
        if (hasReelsWindowClass) {
            score += SCORE_REELS_WINDOW_CLASS
            evidence += "reels_window_class"
        }
        if (signals.hasVerticalPager) {
            score += SCORE_VERTICAL_PAGER
            evidence += "vertical_pager"
        }
        if (hasReelsActionCluster) {
            score += SCORE_ACTION_CLUSTER
            evidence += "action_cluster"
        }
        if (hasReelsAudioOrEffectId) {
            score += SCORE_AUDIO_EFFECT_ID
            evidence += "reels_audio_effect_id"
        }
        if (hasReelsLabel && (hasClipsViewerId || hasSelectedReelsTab || signals.hasVerticalPager)) {
            score += SCORE_REELS_LABEL_WITH_CONTEXT
            evidence += "reels_label"
        }

        // Only a confirmed Reels viewer ID may override background non-Reels context.
        // A selected tab label alone must never override active Feed, DM, Profile, or Stories hierarchies.
        if (hasNonReelsContext && !hasClipsViewerId) {
            score = (score - PENALTY_NON_REELS_CONTEXT).coerceAtLeast(0)
            evidence += "non_reels_context:${nonReelsReasons.joinToString("+")}"
        }

        val strongSignalCount = listOf(hasClipsViewerId, hasSelectedReelsTab, hasReelsWindowClass).count { it }
        val structuralSignalCount = listOf(signals.hasVerticalPager, hasReelsActionCluster, hasReelsAudioOrEffectId).count { it }

        if (signals.wasTruncated && strongSignalCount == 0 && structuralSignalCount < 2 && !hasNonReelsContext) {
            return ReelsDetectionResult(
                classification = ReelsClassification.UNKNOWN,
                confidenceScore = score,
                evidenceSummary = (evidence + "truncated_hierarchy").joinToString(","),
                reason = "Hierarchy truncated at ${signals.nodeCount} nodes without sufficient Reels evidence"
            )
        }

        val classification = when {
            hasNonReelsContext && !hasClipsViewerId ->
                ReelsClassification.NOT_REELS
            score >= THRESHOLD_CONFIRMED && (strongSignalCount >= 1 || structuralSignalCount >= 2) ->
                ReelsClassification.REELS_CONFIRMED
            score >= THRESHOLD_LIKELY && (strongSignalCount >= 1 || structuralSignalCount >= 2) ->
                ReelsClassification.REELS_LIKELY
            else ->
                ReelsClassification.NOT_REELS
        }

        val reason = when (classification) {
            ReelsClassification.REELS_CONFIRMED ->
                "Confirmed Instagram Reels (${evidence.joinToString(", ")})"
            ReelsClassification.REELS_LIKELY ->
                "Likely Instagram Reels awaiting stabilization (${evidence.joinToString(", ")})"
            ReelsClassification.NOT_REELS ->
                if (hasNonReelsContext) {
                    "Rejected due to non-Reels context: ${nonReelsReasons.joinToString(", ")}"
                } else {
                    "Insufficient Reels evidence (score=$score)"
                }
            ReelsClassification.UNKNOWN ->
                "Incomplete hierarchy"
        }

        return ReelsDetectionResult(
            classification = classification,
            confidenceScore = score.coerceIn(0, 100),
            evidenceSummary = evidence.ifEmpty { listOf("none") }.joinToString(","),
            reason = reason
        )
    }

    private fun detectNonReelsContext(signals: ReelsHierarchySignals): List<String> {
        val reasons = mutableListOf<String>()

        if (signals.viewIds.any { id -> DM_VIEW_IDS.any(id::contains) }) {
            reasons += "dm"
        }
        if (signals.viewIds.any { id -> EXPLORE_SEARCH_VIEW_IDS.any(id::contains) }) {
            reasons += "explore_or_search"
        }
        if (signals.viewIds.any { id -> PROFILE_VIEW_IDS.any(id::contains) }) {
            reasons += "profile"
        }
        if (signals.viewIds.any { id -> FEED_OR_STORY_VIEW_IDS.any(id::contains) }) {
            reasons += "feed_or_stories"
        }

        val selectedNonReels = signals.selectedLabels.any { label ->
            label == "home" ||
                label == "search" ||
                label == "explore" ||
                label == "profile" ||
                label == "messages" ||
                label == "direct"
        }
        if (selectedNonReels) {
            reasons += "selected_non_reels_tab"
        }

        return reasons
    }

    private fun isStoryViewId(id: String): Boolean {
        return id.contains("story") || id.contains("stories") || id.contains("reel_ring")
    }

    companion object {
        private const val SCORE_CLIPS_VIEWER_ID = 55
        private const val SCORE_SELECTED_REELS_TAB = 50
        private const val SCORE_REELS_WINDOW_CLASS = 45
        private const val SCORE_VERTICAL_PAGER = 20
        private const val SCORE_ACTION_CLUSTER = 25
        private const val SCORE_AUDIO_EFFECT_ID = 20
        private const val SCORE_REELS_LABEL_WITH_CONTEXT = 10
        private const val PENALTY_NON_REELS_CONTEXT = 60

        private const val THRESHOLD_CONFIRMED = 70
        private const val THRESHOLD_LIKELY = 45

        private val CLIPS_VIEWER_IDS = listOf(
            "clips_viewer",
            "clips_viewer_container",
            "reels_viewer",
            "clips_media",
            "clips_video_container"
        )

        private val REELS_AUDIO_OR_EFFECT_IDS = listOf(
            "clips_audio",
            "clips_ufi",
            "clips_author",
            "audio_attribution",
            "effect_attribution",
            "clips_remix"
        )

        // Phrases specific to the Reels viewer; generic labels like "original audio" are excluded.
        private val REELS_VIEWER_ONLY_PHRASES = listOf(
            "watch more reels",
            "remix this reel",
            "send reel",
            "use audio"
        )

        private val DM_VIEW_IDS = listOf(
            "direct_inbox",
            "direct_thread",
            "thread_list",
            "message_list",
            "row_thread_composer",
            "composer_input"
        )

        private val EXPLORE_SEARCH_VIEW_IDS = listOf(
            "explore_grid",
            "action_bar_search_edit_text",
            "search_edit_text",
            "search_results"
        )

        private val PROFILE_VIEW_IDS = listOf(
            "profile_header",
            "profile_tab",
            "edit_profile",
            "row_profile_header"
        )

        private val FEED_OR_STORY_VIEW_IDS = listOf(
            "story_tray",
            "stories_tray",
            "reel_viewer_story",
            "story_viewer",
            "feed_timeline"
        )
    }
}

/**
 * Stabilizes frame-by-frame Reels classifications to prevent flickering, duplicate triggers,
 * and false blocks on incomplete accessibility trees.
 */
class ReelsStateStabilizer(
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    data class StabilizedDecision(
        val stableState: ReelsClassification,
        val shouldTriggerBlock: Boolean,
        val stateChanged: Boolean,
        val reason: String
    )

    private var currentPackage: String? = null
    private var stableState: ReelsClassification = ReelsClassification.NOT_REELS
    private var pendingLikelyCount: Int = 0
    private var firstLikelyAtMs: Long = 0L
    private var lastConfirmedAtMs: Long = 0L
    private var lastBlockedAtMs: Long = 0L
    private var lastScreenKey: String? = null

    fun onForegroundPackageChanged(packageName: String?) {
        if (packageName != currentPackage) {
            reset(packageName)
        }
    }

    fun reset(packageName: String? = null) {
        currentPackage = packageName
        stableState = ReelsClassification.NOT_REELS
        pendingLikelyCount = 0
        firstLikelyAtMs = 0L
        lastConfirmedAtMs = 0L
        lastScreenKey = null
    }

    fun update(
        packageName: String,
        result: ReelsDetectionResult,
        screenKey: String? = null
    ): StabilizedDecision {
        val now = clock()
        if (packageName != currentPackage) {
            reset(packageName)
        }

        if (screenKey != null && lastScreenKey != null && screenKey != lastScreenKey &&
            result.classification == ReelsClassification.NOT_REELS
        ) {
            pendingLikelyCount = 0
            firstLikelyAtMs = 0L
        }
        if (screenKey != null) {
            lastScreenKey = screenKey
        }

        val previousState = stableState
        var reason = result.reason

        when (result.classification) {
            ReelsClassification.REELS_CONFIRMED -> {
                pendingLikelyCount = 0
                firstLikelyAtMs = 0L
                stableState = ReelsClassification.REELS_CONFIRMED
                lastConfirmedAtMs = now
            }

            ReelsClassification.REELS_LIKELY -> {
                if (stableState == ReelsClassification.REELS_CONFIRMED) {
                    lastConfirmedAtMs = now
                } else {
                    if (pendingLikelyCount == 0 || (now - firstLikelyAtMs) > LIKELY_CONFIRM_WINDOW_MS) {
                        pendingLikelyCount = 1
                        firstLikelyAtMs = now
                        stableState = ReelsClassification.REELS_LIKELY
                        reason = "Awaiting second confirmation for REELS_LIKELY"
                    } else {
                        pendingLikelyCount++
                        if (pendingLikelyCount >= REQUIRED_LIKELY_OBSERVATIONS) {
                            stableState = ReelsClassification.REELS_CONFIRMED
                            lastConfirmedAtMs = now
                            reason = "Stabilized REELS_LIKELY after $pendingLikelyCount observations"
                        }
                    }
                }
            }

            ReelsClassification.UNKNOWN -> {
                val withinGrace = stableState == ReelsClassification.REELS_CONFIRMED &&
                    (now - lastConfirmedAtMs) <= UNKNOWN_GRACE_MS
                if (!withinGrace) {
                    pendingLikelyCount = 0
                    firstLikelyAtMs = 0L
                    stableState = ReelsClassification.UNKNOWN
                } else {
                    reason = "Holding REELS_CONFIRMED during brief UNKNOWN hierarchy"
                }
            }

            ReelsClassification.NOT_REELS -> {
                pendingLikelyCount = 0
                firstLikelyAtMs = 0L
                stableState = ReelsClassification.NOT_REELS
            }
        }

        val inCooldown = lastBlockedAtMs > 0L && (now - lastBlockedAtMs) < BLOCK_COOLDOWN_MS
        val shouldBlock = stableState == ReelsClassification.REELS_CONFIRMED && !inCooldown
        if (shouldBlock) {
            lastBlockedAtMs = now
        }

        return StabilizedDecision(
            stableState = stableState,
            shouldTriggerBlock = shouldBlock,
            stateChanged = stableState != previousState,
            reason = reason
        )
    }

    companion object {
        private const val LIKELY_CONFIRM_WINDOW_MS = 1_500L
        private const val REQUIRED_LIKELY_OBSERVATIONS = 2
        private const val UNKNOWN_GRACE_MS = 800L
        private const val BLOCK_COOLDOWN_MS = 1_500L
    }
}
