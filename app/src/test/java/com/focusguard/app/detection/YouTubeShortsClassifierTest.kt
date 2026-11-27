package com.focusguard.app.detection

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeShortsClassifierTest {

    private val classifier = YouTubeShortsClassifier()

    @Test
    fun activeShortsPlayer_returnsShortsConfirmed() {
        val signals = YouTubeHierarchySignals(
            viewIds = setOf("com.google.android.youtube:id/reel_watch_fragment"),
            texts = listOf("remix", "original sound", "@creator"),
            selectedLabels = listOf("shorts"),
            hasVerticalPager = true,
            nodeCount = 90
        )

        val result = classifier.classify(signals)

        assertEquals(YouTubeShortsClassification.SHORTS_CONFIRMED, result.classification)
    }

    @Test
    fun regularYouTubeVideoWithBottomNavShortsTab_returnsNotShorts() {
        val signals = YouTubeHierarchySignals(
            viewIds = setOf(
                "com.google.android.youtube:id/watch_while_time_bar",
                "com.google.android.youtube:id/fullscreen_button"
            ),
            texts = listOf("shorts", "subscribe", "comments", "likes", "@physicswallah"),
            nodeCount = 140
        )

        val result = classifier.classify(signals)

        assertEquals(YouTubeShortsClassification.NOT_SHORTS, result.classification)
    }

    @Test
    fun homeFeedShortsShelf_returnsNotShorts() {
        val signals = YouTubeHierarchySignals(
            viewIds = setOf(
                "com.google.android.youtube:id/shorts_shelf",
                "com.google.android.youtube:id/reel_shelf"
            ),
            texts = listOf("shorts", "home"),
            selectedLabels = listOf("home"),
            nodeCount = 110
        )

        val result = classifier.classify(signals)

        assertEquals(YouTubeShortsClassification.NOT_SHORTS, result.classification)
    }

    @Test
    fun shortsOnProductiveChannel_returnsProductiveAllowed() {
        val signals = YouTubeHierarchySignals(
            viewIds = setOf("com.google.android.youtube:id/shorts_player"),
            texts = listOf("Physics Wallah", "remix", "original sound"),
            productiveChannels = setOf("Physics Wallah"),
            nodeCount = 75
        )

        val result = classifier.classify(signals)

        assertEquals(YouTubeShortsClassification.PRODUCTIVE_ALLOWED, result.classification)
        assertEquals("Physics Wallah", result.matchedChannel)
    }
}
