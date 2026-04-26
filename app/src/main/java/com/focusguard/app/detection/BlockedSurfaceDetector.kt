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
        val hasStrongViewId = snapshot.viewIds.any { id ->
            id.contains("clips") || id.contains("reel")
        }
        val hasStrongPhrase = snapshot.texts.any { text ->
            INSTAGRAM_REELS_PHRASES.any(text::contains)
        }
        val hasReelsLabel = snapshot.texts.any { it == "reels" || it == "reel" }
        val hasViewerHint = snapshot.texts.any { text ->
            text.contains("original audio") ||
                text.contains("use audio") ||
                text.contains("remix this reel") ||
                text.contains("send reel")
        }

        if (!hasStrongViewId && !hasStrongPhrase && !(hasReelsLabel && hasViewerHint)) {
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

    private data class NodeSnapshot(
        val texts: Set<String>,
        val viewIds: Set<String>
    ) {
        companion object {
            fun from(
                event: AccessibilityEvent,
                rootNode: AccessibilityNodeInfo?
            ): NodeSnapshot {
                val texts = linkedSetOf<String>()
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

                    node.viewIdResourceName
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf { it.isNotEmpty() }
                        ?.let(viewIds::add)

                    for (index in 0 until node.childCount) {
                        node.getChild(index)?.let(queue::addLast)
                    }
                }

                return NodeSnapshot(texts = texts, viewIds = viewIds)
            }

            private const val MAX_NODES = 180
        }
    }

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val INSTAGRAM_LITE_PACKAGE = "com.instagram.lite"

        private val INSTAGRAM_REELS_PHRASES = listOf(
            "watch more reels",
            "reel by",
            "send reel",
            "remix this reel",
            "use audio",
            "original audio"
        )
    }
}
