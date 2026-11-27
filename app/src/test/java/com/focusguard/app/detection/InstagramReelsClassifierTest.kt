package com.focusguard.app.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramReelsClassifierTest {

    private val classifier = InstagramReelsClassifier()

    // ── Positive Cases ──

    @Test
    fun genuineReelsViewerWithMultipleSignals_returnsConfirmed() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            windowClassName = "com.instagram.clips.viewer.ClipsViewerActivity",
            viewIds = setOf(
                "com.instagram.android:id/clips_viewer_container",
                "com.instagram.android:id/clips_ufi",
                "com.instagram.android:id/clips_audio_attribution"
            ),
            texts = listOf("like", "comment", "share", "use audio"),
            selectedLabels = listOf("reels"),
            hasVerticalPager = true,
            actionControlCount = 3,
            nodeCount = 95
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.REELS_CONFIRMED, result.classification)
        assertTrue(result.confidenceScore >= 70)
    }

    @Test
    fun reelsViewerWithLimitedTextButStrongStructuralEvidence_returnsConfirmed() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf(
                "com.instagram.android:id/clips_viewer_container",
                "com.instagram.android:id/clips_ufi"
            ),
            texts = emptyList(),
            selectedLabels = emptyList(),
            hasVerticalPager = true,
            actionControlCount = 2,
            nodeCount = 48
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.REELS_CONFIRMED, result.classification)
        assertTrue(result.evidenceSummary.contains("clips_viewer_id"))
        assertTrue(result.evidenceSummary.contains("vertical_pager"))
    }

    @Test
    fun reelsScreenAfterNavigationFromHomeTab_transitionsAndTriggersOnce() {
        var now = 1_000L
        val stabilizer = ReelsStateStabilizer(clock = { now })

        val homeSignals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf("com.instagram.android:id/feed_timeline", "com.instagram.android:id/story_tray"),
            selectedLabels = listOf("home"),
            nodeCount = 120
        )
        val homeDecision = stabilizer.update(INSTAGRAM, classifier.classify(homeSignals), "MainActivity")
        assertEquals(ReelsClassification.NOT_REELS, homeDecision.stableState)
        assertFalse(homeDecision.shouldTriggerBlock)

        now += 300L
        val reelsSignals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf("com.instagram.android:id/clips_viewer_container", "com.instagram.android:id/clips_ufi"),
            selectedLabels = listOf("reels"),
            hasVerticalPager = true,
            actionControlCount = 2,
            nodeCount = 110
        )
        val reelsDecision = stabilizer.update(INSTAGRAM, classifier.classify(reelsSignals), "ClipsViewer")
        assertEquals(ReelsClassification.REELS_CONFIRMED, reelsDecision.stableState)
        assertTrue(reelsDecision.shouldTriggerBlock)
        assertTrue(reelsDecision.stateChanged)
    }

    // ── Negative Cases ──

    @Test
    fun normalInstagramFeed_returnsNotReels() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf(
                "com.instagram.android:id/feed_timeline",
                "com.instagram.android:id/story_tray",
                "com.instagram.android:id/like_button"
            ),
            texts = listOf("home", "liked by user1 and others", "original audio"),
            selectedLabels = listOf("home"),
            hasVerticalPager = true,
            actionControlCount = 1,
            nodeCount = 140
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.NOT_REELS, result.classification)
    }

    @Test
    fun instagramExplore_returnsNotReels() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf(
                "com.instagram.android:id/explore_grid",
                "com.instagram.android:id/action_bar_search_edit_text"
            ),
            texts = listOf("search", "reels", "original audio"),
            selectedLabels = listOf("search"),
            nodeCount = 130
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.NOT_REELS, result.classification)
    }

    @Test
    fun instagramProfile_returnsNotReels() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf(
                "com.instagram.android:id/profile_header",
                "com.instagram.android:id/edit_profile"
            ),
            texts = listOf("posts", "followers", "following", "reels"),
            selectedLabels = listOf("profile"),
            nodeCount = 105
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.NOT_REELS, result.classification)
    }

    @Test
    fun instagramDirectMessagesWithSharedReelPreview_returnsNotReels() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf(
                "com.instagram.android:id/direct_thread",
                "com.instagram.android:id/composer_input"
            ),
            texts = listOf("message...", "reel by creator", "original audio"),
            selectedLabels = emptyList(),
            nodeCount = 85
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.NOT_REELS, result.classification)
    }

    @Test
    fun instagramVideoPreviewWithoutReelsViewer_returnsNotReels() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf("com.instagram.android:id/video_preview"),
            texts = listOf("watch", "original audio", "likes"),
            hasVerticalPager = false,
            actionControlCount = 1,
            nodeCount = 60
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.NOT_REELS, result.classification)
    }

    @Test
    fun screenContainingOnlyOriginalAudio_returnsNotReels() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            texts = listOf("original audio"),
            nodeCount = 25
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.NOT_REELS, result.classification)
        assertEquals(0, result.confidenceScore)
    }

    @Test
    fun screenWithLikesCommentsUsernamesCaptionsOnly_returnsNotReels() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            texts = listOf("@study_buddy", "1,240 likes", "view all 48 comments", "nice post!"),
            actionControlCount = 0,
            nodeCount = 70
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.NOT_REELS, result.classification)
    }

    // ── Edge Cases ──

    @Test
    fun emptyAccessibilityHierarchy_returnsUnknown() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            nodeCount = 0,
            hasRootNode = false
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.UNKNOWN, result.classification)
    }

    @Test
    fun missingResourceIdsWithTextAndCluster_requiresEnoughEvidence() {
        val likelyOnly = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = emptySet(),
            texts = listOf("use audio", "watch more reels"),
            selectedLabels = listOf("reels"),
            hasVerticalPager = true,
            actionControlCount = 1,
            nodeCount = 65
        )

        val result = classifier.classify(likelyOnly)

        assertEquals(ReelsClassification.REELS_CONFIRMED, result.classification)
    }

    @Test
    fun partialHierarchyDueToTraversalLimit_returnsUnknown() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = emptySet(),
            texts = listOf("reels"),
            nodeCount = 350,
            wasTruncated = true
        )

        val result = classifier.classify(signals)

        assertEquals(ReelsClassification.UNKNOWN, result.classification)
    }

    @Test
    fun rapidScreenTransitions_likelyDoesNotBlockUntilConfirmedTwice() {
        var now = 10_000L
        val stabilizer = ReelsStateStabilizer(clock = { now })

        val likelySignals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf("com.instagram.android:id/clips_viewer_container"),
            nodeCount = 40
        )

        val first = stabilizer.update(INSTAGRAM, classifier.classify(likelySignals), "ScreenA")
        assertEquals(ReelsClassification.REELS_LIKELY, first.stableState)
        assertFalse(first.shouldTriggerBlock)

        // Rapid transition to Feed cancels pending likely state
        now += 120L
        val feedSignals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf("com.instagram.android:id/feed_timeline"),
            selectedLabels = listOf("home"),
            nodeCount = 90
        )
        val feed = stabilizer.update(INSTAGRAM, classifier.classify(feedSignals), "ScreenB")
        assertEquals(ReelsClassification.NOT_REELS, feed.stableState)
        assertFalse(feed.shouldTriggerBlock)
    }

    @Test
    fun repeatedContentChangeEvents_respectsBlockCooldown() {
        var now = 50_000L
        val stabilizer = ReelsStateStabilizer(clock = { now })
        val confirmedSignals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf("com.instagram.android:id/clips_viewer_container"),
            hasVerticalPager = true,
            nodeCount = 80
        )
        val result = classifier.classify(confirmedSignals)

        val first = stabilizer.update(INSTAGRAM, result)
        assertTrue(first.shouldTriggerBlock)

        now += 200L
        val second = stabilizer.update(INSTAGRAM, result)
        assertFalse(second.shouldTriggerBlock)

        now += 1_600L
        val third = stabilizer.update(INSTAGRAM, result)
        assertTrue(third.shouldTriggerBlock)
    }

    @Test
    fun switchingFromInstagramToAnotherApp_resetsStabilizerState() {
        var now = 20_000L
        val stabilizer = ReelsStateStabilizer(clock = { now })
        val likelySignals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf("com.instagram.android:id/clips_viewer_container"),
            nodeCount = 40
        )

        val first = stabilizer.update(INSTAGRAM, classifier.classify(likelySignals))
        assertEquals(ReelsClassification.REELS_LIKELY, first.stableState)

        stabilizer.onForegroundPackageChanged("com.whatsapp")

        now += 100L
        val afterReturn = stabilizer.update(INSTAGRAM, classifier.classify(likelySignals))
        assertEquals(ReelsClassification.REELS_LIKELY, afterReturn.stableState)
        assertFalse(afterReturn.shouldTriggerBlock)
    }

    @Test
    fun temporaryUnknownClassification_holdsConfirmedWithinGraceWindow() {
        var now = 30_000L
        val stabilizer = ReelsStateStabilizer(clock = { now })
        val confirmedSignals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf("com.instagram.android:id/clips_viewer_container"),
            hasVerticalPager = true,
            nodeCount = 80
        )
        val unknownSignals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            nodeCount = 0,
            hasRootNode = false
        )

        stabilizer.update(INSTAGRAM, classifier.classify(confirmedSignals))

        now += 300L
        val held = stabilizer.update(INSTAGRAM, classifier.classify(unknownSignals))
        assertEquals(ReelsClassification.REELS_CONFIRMED, held.stableState)

        now += 1_000L
        val decayed = stabilizer.update(INSTAGRAM, classifier.classify(unknownSignals))
        assertEquals(ReelsClassification.UNKNOWN, decayed.stableState)
    }

    @Test
    fun selectedReelsLabelWithFeedTimelineAndNoViewerId_returnsNotReels() {
        val signals = ReelsHierarchySignals(
            packageName = INSTAGRAM,
            viewIds = setOf("com.instagram.android:id/feed_timeline"),
            selectedLabels = listOf("reels"),
            hasVerticalPager = true,
            nodeCount = 90
        )

        val result = classifier.classify(signals)
        assertEquals(ReelsClassification.NOT_REELS, result.classification)
    }

    companion object {
        private const val INSTAGRAM = "com.instagram.android"
    }
}
