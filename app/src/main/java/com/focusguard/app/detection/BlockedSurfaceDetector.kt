package com.focusguard.app.detection

import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.BuildConfig
import com.focusguard.app.persistence.FocusGuardPrefs
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
    private val reelsStabilizer: ReelsStateStabilizer = ReelsStateStabilizer()
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

        if (!prefs.isGuardActiveNow()) {
            reelsStabilizer.reset()
            return null
        }
        if (prefs.blacklistedApps.contains(packageName)) return null

        return when (packageName) {
            INSTAGRAM_PACKAGE, INSTAGRAM_LITE_PACKAGE -> detectInstagramReels(packageName, event, rootNode)
            YOUTUBE_PACKAGE -> detectYoutubeShorts(packageName, event, rootNode)
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
        if (match.surfaceId != FocusGuardPrefs.SURFACE_INSTAGRAM_REELS) return false
        val rootPackage = rootNode?.packageName?.toString() ?: return false
        if (rootPackage != match.packageName) return false

        val snapshot = NodeSnapshot.from(event = null, rootNode = rootNode)
        val signals = snapshot.toReelsSignals(packageName = rootPackage, event = null)
        val result = reelsClassifier.classify(signals)
        return result.classification == ReelsClassification.REELS_CONFIRMED ||
            result.classification == ReelsClassification.REELS_LIKELY
    }

    private fun detectInstagramReels(
        packageName: String,
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): ContentSurfaceMatch? {
        if (!prefs.blockedContentSurfaces.contains(FocusGuardPrefs.SURFACE_INSTAGRAM_REELS)) {
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

        if (!decision.shouldTriggerBlock) {
            return null
        }

        return ContentSurfaceMatch(
            surfaceId = FocusGuardPrefs.SURFACE_INSTAGRAM_REELS,
            blockKey = "${packageName}:${FocusGuardPrefs.SURFACE_INSTAGRAM_REELS}",
            packageName = packageName,
            title = "Instagram Reels",
            message = "Reels are blocked during focus hours. Feed, DMs, and stories can stay open."
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
        val hasShortsViewId = snapshot.viewIds.any { id ->
            id.contains("shorts") || id.contains("reel")
        }
        val hasShortsPhrase = snapshot.texts.any { text ->
            YOUTUBE_SHORTS_PHRASES.any(text::contains) || text == "shorts"
        }
        val hasChannelHandle = snapshot.texts.any { text ->
            text.startsWith("@") && text.length > 2
        }
        val hasViewerHints = snapshot.texts.any { text ->
            text.contains("subscribe") ||
                text.contains("subscribers") ||
                text.contains("comments") ||
                text.contains("likes") ||
                text.contains("original sound") ||
                text.contains("sound")
        }

        if (!hasShortsViewId && !(hasShortsPhrase && (hasChannelHandle || hasViewerHints))) {
            return null
        }

        val productiveMatch = matchConfiguredChannel(snapshot.texts, prefs.youtubeProductiveChannels)
        if (productiveMatch != null) {
            return null
        }

        val distractingMatch = matchConfiguredChannel(snapshot.texts, prefs.youtubeDistractingChannels)
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

        private val YOUTUBE_SHORTS_PHRASES = listOf(
            "shorts",
            "subscribe",
            "remix",
            "original sound"
        )
    }
}
