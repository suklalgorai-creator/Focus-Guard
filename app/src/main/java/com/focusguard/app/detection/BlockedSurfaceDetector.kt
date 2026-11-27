package com.focusguard.app.detection

import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.BuildConfig
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.reelcount.domain.ReelObservation
import java.util.ArrayDeque

data class ContentSurfaceMatch(
    val surfaceId: String,
    val blockKey: String,
    val packageName: String,
    val title: String,
    val message: String
)

class BlockedSurfaceDetector(
    private val prefs: FocusGuardPrefs,
    private val reelsClassifier: InstagramReelsClassifier = InstagramReelsClassifier(),
    private val reelsStabilizer: ReelsStateStabilizer = ReelsStateStabilizer(),
    private val youtubeClassifier: YouTubeShortsClassifier = YouTubeShortsClassifier()
) {

    fun onForegroundPackageChanged(packageName: String) {
        if (packageName != INSTAGRAM_PACKAGE && packageName != INSTAGRAM_LITE_PACKAGE) {
            reelsStabilizer.onForegroundPackageChanged(packageName)
        }
    }

    fun detect(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): ContentSurfaceMatch? {
        val packageName = event.packageName?.toString() ?: return null
        val isInstagram = packageName == INSTAGRAM_PACKAGE || packageName == INSTAGRAM_LITE_PACKAGE
        val reelCountEnabled = FocusGuardApp.instance.reelCountRepository.currentSettings().enabled

        if (!prefs.isGuardActiveNow() && !(isInstagram && reelCountEnabled)) {
            reelsStabilizer.reset()
            return null
        }
        if (prefs.blacklistedApps.contains(packageName)) return null

        return when (packageName) {
            INSTAGRAM_PACKAGE, INSTAGRAM_LITE_PACKAGE -> detectInstagramReels(packageName, event, rootNode)
            YOUTUBE_PACKAGE -> {
                if (!prefs.isGuardActiveNow()) return null
                detectYoutubeShorts(packageName, event, rootNode)
            }
            else -> {
                reelsStabilizer.onForegroundPackageChanged(packageName)
                null
            }
        }
    }

    fun isSurfaceStillVisible(
        match: ContentSurfaceMatch,
        rootNode: AccessibilityNodeInfo?
    ): Boolean {
        val rootPackage = rootNode?.packageName?.toString() ?: return false
        if (rootPackage != match.packageName) return false

        val snapshot = NodeSnapshot.from(event = null, rootNode = rootNode)
        return when (match.surfaceId) {
            FocusGuardPrefs.SURFACE_INSTAGRAM_REELS -> {
                val signals = snapshot.toReelsSignals(packageName = rootPackage, event = null)
                val result = reelsClassifier.classify(signals)
                result.classification == ReelsClassification.REELS_CONFIRMED ||
                    result.classification == ReelsClassification.REELS_LIKELY
            }
            FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS -> {
                val result = youtubeClassifier.classify(snapshot.toYouTubeSignals(prefs))
                result.classification == YouTubeShortsClassification.SHORTS_CONFIRMED
            }
            else -> false
        }
    }

    private fun detectInstagramReels(
        packageName: String,
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): ContentSurfaceMatch? {
        val coordinator = FocusGuardApp.instance.reelCountCoordinator
        val policyDecision = coordinator.evaluateReelsBlockingPolicy()
        val reelCountEnabled = FocusGuardApp.instance.reelCountRepository.currentSettings().enabled

        // Skip expensive hierarchy traversal if neither blocking policy nor Reel Count is active.
        if (!policyDecision.shouldBlock && !reelCountEnabled) {
            return null
        }

        val snapshot = NodeSnapshot.from(event, rootNode)
        val signals = snapshot.toReelsSignals(packageName = packageName, event = event)
        val result = reelsClassifier.classify(signals)
        val screenKey = event.className?.toString()?.takeIf { it.isNotBlank() }
        val decision = reelsStabilizer.update(
            packageName = packageName,
            result = result,
            screenKey = screenKey
        )

        logReelsDiagnostics(
            packageName = packageName,
            eventType = event.eventType,
            result = result,
            decision = decision
        )

        if (!policyDecision.shouldBlock) {
            if (reelCountEnabled) {
                val eventClassName = event.className?.toString().orEmpty()
                val isScrollTransition = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED &&
                    (eventClassName.contains("ViewPager", ignoreCase = true) ||
                        eventClassName.contains("RecyclerView", ignoreCase = true))
                val isSubtreeChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
                    (event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0
                val hasClipsViewerId = signals.viewIds.any { id ->
                    id.contains("clips_viewer") || id.contains("reels_viewer") || id.contains("clips_video_container")
                }

                coordinator.onInstagramObservation(
                    ReelObservation(
                        packageName = packageName,
                        classification = decision.stableState,
                        confidenceScore = result.confidenceScore,
                        hasClipsViewerId = hasClipsViewerId,
                        isScrollTransition = isScrollTransition,
                        isSubtreeContentChange = isSubtreeChange,
                        contentSignature = snapshot.contentSignature(),
                        isBlockedByPolicy = false
                    )
                )
            }
            return null
        }

        if (!decision.shouldTriggerBlock) {
            return null
        }

        GuardDiagnostics.logEvent(
            GuardDiagnostics.REELS_DETECTED,
            "pkg=$packageName score=${result.confidenceScore} source=${policyDecision.source}"
        )
        val reasonEvent = when {
            policyDecision.source == com.focusguard.app.reelcount.domain.ReelsBlockSource.REEL_COUNT_LIMIT ->
                GuardDiagnostics.BLOCK_REASON_REEL_LIMIT
            prefs.isStrictBlockActive() ->
                GuardDiagnostics.BLOCK_REASON_STRICT_MODE
            prefs.isWithinSchedule() ->
                GuardDiagnostics.BLOCK_REASON_SCHEDULE
            else ->
                GuardDiagnostics.BLOCK_REASON_FOCUS_SESSION
        }
        GuardDiagnostics.logEvent(reasonEvent, "surface=instagram_reels")

        return ContentSurfaceMatch(
            surfaceId = FocusGuardPrefs.SURFACE_INSTAGRAM_REELS,
            blockKey = "${packageName}:${FocusGuardPrefs.SURFACE_INSTAGRAM_REELS}",
            packageName = packageName,
            title = policyDecision.title,
            message = policyDecision.message
        )
    }

    private fun logReelsDiagnostics(
        packageName: String,
        eventType: Int,
        result: ReelsDetectionResult,
        decision: ReelsStateStabilizer.StabilizedDecision
    ) {
        if (!BuildConfig.DEBUG && !decision.shouldTriggerBlock && !decision.stateChanged) {
            return
        }

        val eventName = AccessibilityEvent.eventTypeToString(eventType)
        val summary = "pkg=$packageName event=$eventName class=${result.classification} " +
            "score=${result.confidenceScore} evidence=[${result.evidenceSummary}] " +
            "stable=${decision.stableState} trigger=${decision.shouldTriggerBlock} " +
            "reason=\"${decision.reason}\""

        if (decision.shouldTriggerBlock) {
            Log.w(TAG, "Reels block triggered: $summary")
        } else if (BuildConfig.DEBUG) {
            Log.d(TAG, summary)
        }
    }

    private fun detectYoutubeShorts(
        packageName: String,
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): ContentSurfaceMatch? {
        if (!prefs.blockedContentSurfaces.contains(FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS)) {
            return null
        }

        val snapshot = NodeSnapshot.from(event, rootNode)
        val result = youtubeClassifier.classify(snapshot.toYouTubeSignals(prefs))
        if (result.classification != YouTubeShortsClassification.SHORTS_CONFIRMED) {
            return null
        }

        GuardDiagnostics.logEvent(
            GuardDiagnostics.SHORTS_DETECTED,
            "pkg=$packageName"
        )
        val reasonEvent = when {
            prefs.isStrictBlockActive() -> GuardDiagnostics.BLOCK_REASON_STRICT_MODE
            prefs.isWithinSchedule() -> GuardDiagnostics.BLOCK_REASON_SCHEDULE
            else -> GuardDiagnostics.BLOCK_REASON_FOCUS_SESSION
        }
        GuardDiagnostics.logEvent(reasonEvent, "surface=youtube_shorts")

        val distractingMatch = result.matchedChannel
        val message = if (distractingMatch != null) {
            "$distractingMatch is marked as distracting. Shorts stay blocked during focus hours."
        } else {
            "Shorts are blocked during focus hours. Long-form lessons and productive channels can stay open."
        }

        return ContentSurfaceMatch(
            surfaceId = FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS,
            blockKey = "${packageName}:${FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS}",
            packageName = packageName,
            title = "YouTube Shorts",
            message = message
        )
    }

    private data class NodeSnapshot(
        val texts: List<String>,
        val selectedTexts: List<String>,
        val viewIds: Set<String>,
        val hasVerticalPager: Boolean,
        val actionControlCount: Int,
        val nodeCount: Int,
        val wasTruncated: Boolean,
        val hasRootNode: Boolean
    ) {
        /**
         * Ephemeral, privacy-safe structural hash of the visible Reels hierarchy used solely
         * for in-memory deduplication of the currently resting Reel. Never persisted.
         */
        fun contentSignature(): Int {
            if (texts.isEmpty() && viewIds.isEmpty()) return 0
            var hash = 17
            // Sample up to first 12 text tokens (e.g. handle/caption/audio length & hash) without storing raw strings.
            texts.take(12).forEach { token ->
                hash = 31 * hash + token.hashCode()
            }
            hash = 31 * hash + viewIds.size
            return hash
        }

        fun toReelsSignals(packageName: String, event: AccessibilityEvent?): ReelsHierarchySignals {
            val eventType = event?.eventType ?: 0
            return ReelsHierarchySignals(
                packageName = packageName,
                isWindowTransition = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                    eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED,
                isSelectionEvent = eventType == AccessibilityEvent.TYPE_VIEW_SELECTED,
                windowClassName = event?.className?.toString(),
                viewIds = viewIds,
                texts = texts,
                selectedLabels = selectedTexts,
                hasVerticalPager = hasVerticalPager,
                actionControlCount = actionControlCount,
                nodeCount = nodeCount,
                wasTruncated = wasTruncated,
                hasRootNode = hasRootNode
            )
        }

        fun toYouTubeSignals(prefs: FocusGuardPrefs): YouTubeHierarchySignals {
            return YouTubeHierarchySignals(
                viewIds = viewIds,
                texts = texts,
                selectedLabels = selectedTexts,
                hasVerticalPager = hasVerticalPager,
                productiveChannels = prefs.youtubeProductiveChannels,
                distractingChannels = prefs.youtubeDistractingChannels,
                nodeCount = nodeCount
            )
        }

        companion object {
            fun from(
                event: AccessibilityEvent?,
                rootNode: AccessibilityNodeInfo?
            ): NodeSnapshot {
                val texts = mutableListOf<String>()
                val selectedTexts = mutableListOf<String>()
                val viewIds = linkedSetOf<String>()
                var hasVerticalPager = false
                var actionControlCount = 0

                fun addText(value: CharSequence?) {
                    val normalized = value?.toString()?.trim()?.lowercase().orEmpty()
                    if (normalized.isNotEmpty()) {
                        texts.add(normalized)
                    }
                }

                event?.text?.forEach(::addText)
                addText(event?.contentDescription)

                if (rootNode == null) {
                    return NodeSnapshot(
                        texts = texts,
                        selectedTexts = selectedTexts,
                        viewIds = viewIds,
                        hasVerticalPager = false,
                        actionControlCount = 0,
                        nodeCount = 0,
                        wasTruncated = false,
                        hasRootNode = false
                    )
                }

                val queue = ArrayDeque<AccessibilityNodeInfo>()
                queue.addLast(rootNode)

                var visited = 0
                while (queue.isNotEmpty() && visited < MAX_NODES) {
                    val node = queue.removeFirst()
                    visited++

                    addText(node.text)
                    addText(node.contentDescription)
                    if (node.isSelected) {
                        node.text?.toString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(selectedTexts::add)
                        node.contentDescription?.toString()?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let(selectedTexts::add)
                    }

                    val viewId = node.viewIdResourceName
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf { it.isNotEmpty() }
                    if (viewId != null) {
                        viewIds.add(viewId)
                        if (REELS_ACTION_CONTROL_IDS.any(viewId::contains)) {
                            actionControlCount++
                        }
                    }

                    if (!hasVerticalPager && isVerticalPagerNode(node, viewId)) {
                        hasVerticalPager = true
                    }

                    for (index in 0 until node.childCount) {
                        node.getChild(index)?.let(queue::addLast)
                    }

                    if (node !== rootNode) {
                        recycleSafely(node)
                    }
                }

                val wasTruncated = queue.isNotEmpty()
                while (queue.isNotEmpty()) {
                    val remaining = queue.removeFirst()
                    if (remaining !== rootNode) {
                        recycleSafely(remaining)
                    }
                }

                return NodeSnapshot(
                    texts = texts,
                    selectedTexts = selectedTexts,
                    viewIds = viewIds,
                    hasVerticalPager = hasVerticalPager,
                    actionControlCount = actionControlCount,
                    nodeCount = visited,
                    wasTruncated = wasTruncated,
                    hasRootNode = true
                )
            }

            private fun isVerticalPagerNode(node: AccessibilityNodeInfo, viewId: String?): Boolean {
                val className = node.className?.toString()?.lowercase().orEmpty()
                val isPagerOrList = className.contains("viewpager") ||
                    className.contains("recyclerview")
                val hasPagerId = viewId != null && (
                    viewId.contains("clips_viewer") ||
                        viewId.contains("reels_viewer") ||
                        viewId.contains("pager")
                    )
                return node.isScrollable && (isPagerOrList || hasPagerId)
            }

            @Suppress("DEPRECATION")
            private fun recycleSafely(node: AccessibilityNodeInfo) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    runCatching { node.recycle() }
                }
            }

            private const val MAX_NODES = 350
        }
    }

    companion object {
        private const val TAG = "BlockedSurfaceDetector"
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val INSTAGRAM_LITE_PACKAGE = "com.instagram.lite"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        private val REELS_ACTION_CONTROL_IDS = listOf(
            "clips_ufi",
            "like_button",
            "comment_button",
            "share_button",
            "clips_audio",
            "clips_remix"
        )
    }
}
