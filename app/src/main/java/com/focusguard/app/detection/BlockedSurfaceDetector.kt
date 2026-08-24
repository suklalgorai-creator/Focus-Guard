package com.focusguard.app.detection

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.focusguard.app.persistence.FocusGuardPrefs
import java.util.ArrayDeque

data class ContentSurfaceMatch(
    val surfaceId: String,
    val blockKey: String,
    val packageName: String,
    val title: String,
    val message: String
)

class BlockedSurfaceDetector(private val prefs: FocusGuardPrefs) {

    fun detect(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo?
    ): ContentSurfaceMatch? {
        val packageName = event.packageName?.toString() ?: return null

        if (!prefs.isGuardActiveNow()) return null
        if (prefs.blacklistedApps.contains(packageName)) return null

        return when (packageName) {
            INSTAGRAM_PACKAGE, INSTAGRAM_LITE_PACKAGE -> detectInstagramReels(packageName, event, rootNode)
            YOUTUBE_PACKAGE -> detectYoutubeShorts(packageName, event, rootNode)
            else -> null
        }
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
        val hasViewerViewId = snapshot.viewIds.any { id ->
            INSTAGRAM_REELS_VIEWER_IDS.any(id::contains)
        }
        val hasStrongPhrase = snapshot.texts.any { text ->
            INSTAGRAM_REELS_PHRASES.any(text::contains)
        }
        val hasReelsLabel = snapshot.texts.any { it == "reels" || it == "reel" }
        val hasSelectedReelsTab = snapshot.selectedTexts.any { text ->
            text == "reels" ||
                text == "reel" ||
                (text.contains("reels") && text.contains("selected"))
        }
        val hasViewerHint = snapshot.texts.any { text ->
            text.contains("original audio") ||
                text.contains("use audio") ||
                text.contains("remix this reel") ||
                text.contains("send reel")
        }

        if (!hasViewerViewId &&
            !hasSelectedReelsTab &&
            !(hasStrongPhrase && hasViewerHint) &&
            !(hasReelsLabel && hasViewerHint)
        ) {
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
        val viewIds: Set<String>
    ) {
        companion object {
            fun from(
                event: AccessibilityEvent,
                rootNode: AccessibilityNodeInfo?
            ): NodeSnapshot {
                val texts = mutableListOf<String>()
                val selectedTexts = mutableListOf<String>()
                val viewIds = linkedSetOf<String>()

                fun addText(value: CharSequence?) {
                    val normalized = value?.toString()?.trim()?.lowercase().orEmpty()
                    if (normalized.isNotEmpty()) {
                        texts.add(normalized)
                    }
                }

                event.text.forEach(::addText)
                addText(event.contentDescription)

                val queue = ArrayDeque<AccessibilityNodeInfo>()
                rootNode?.let(queue::addLast)

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

                    node.viewIdResourceName
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let(viewIds::add)

                    for (index in 0 until node.childCount) {
                        node.getChild(index)?.let(queue::addLast)
                    }
                }

                return NodeSnapshot(
                    texts = texts,
                    selectedTexts = selectedTexts,
                    viewIds = viewIds
                )
            }

            private const val MAX_NODES = 180
        }
    }

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val INSTAGRAM_LITE_PACKAGE = "com.instagram.lite"
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

        private val INSTAGRAM_REELS_VIEWER_IDS = listOf(
            "clips_viewer",
            "clips_viewer_container",
            "reels_viewer",
            "reel_viewer"
        )

        private val INSTAGRAM_REELS_PHRASES = listOf(
            "watch more reels",
            "reel by",
            "send reel",
            "remix this reel",
            "use audio",
            "original audio"
        )

        private val YOUTUBE_SHORTS_PHRASES = listOf(
            "shorts",
            "subscribe",
            "remix",
            "original sound"
        )
    }
}
