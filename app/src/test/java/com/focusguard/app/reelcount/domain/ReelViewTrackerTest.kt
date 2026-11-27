package com.focusguard.app.reelcount.domain

import com.focusguard.app.detection.ReelsClassification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class FakeReelCountClock(
    var nowMs: Long = 1_700_000_000_000L,
    var elapsedMs: Long = 10_000L,
    var dateKey: String = "2026-11-27"
) : ReelCountClock {
    override fun nowMillis(): Long = nowMs
    override fun elapsedRealtimeMs(): Long = elapsedMs
    override fun todayDateKey(): String = dateKey
}

class ReelViewTrackerTest {

    private fun confirmedObservation(
        packageName: String = "com.instagram.android",
        isScrollTransition: Boolean = false,
        isSubtreeContentChange: Boolean = false,
        contentSignature: Int = 101,
        isBlockedByPolicy: Boolean = false,
        hasClipsViewerId: Boolean = true
    ) = ReelObservation(
        packageName = packageName,
        classification = ReelsClassification.REELS_CONFIRMED,
        confidenceScore = 80,
        hasClipsViewerId = hasClipsViewerId,
        isScrollTransition = isScrollTransition,
        isSubtreeContentChange = isSubtreeContentChange,
        contentSignature = contentSignature,
        isBlockedByPolicy = isBlockedByPolicy
    )

    @Test
    fun `actual reel view is counted once when entering reels viewer`() {
        val clock = FakeReelCountClock()
        val tracker = ReelViewTracker(clock)

        val first = tracker.onObservation(confirmedObservation(contentSignature = 101))
        assertNotNull(first)
        assertEquals("2026-11-27", first!!.dateKey)

        // Repeated content callback on the same resting Reel must not re-count.
        clock.elapsedMs += 3_000L
        val repeat = tracker.onObservation(confirmedObservation(contentSignature = 101))
        assertNull(repeat)
    }

    @Test
    fun `normal instagram post or reels tab without viewer is not counted`() {
        val clock = FakeReelCountClock()
        val tracker = ReelViewTracker(clock)

        val nonReels = ReelObservation(
            packageName = "com.instagram.android",
            classification = ReelsClassification.NOT_REELS,
            confidenceScore = 10,
            hasClipsViewerId = false,
            isScrollTransition = true,
            isSubtreeContentChange = true,
            contentSignature = 202,
            isBlockedByPolicy = false
        )
        assertNull(tracker.onObservation(nonReels))

        val likelyOnly = ReelObservation(
            packageName = "com.instagram.android",
            classification = ReelsClassification.REELS_LIKELY,
            confidenceScore = 50,
            hasClipsViewerId = false,
            isScrollTransition = false,
            isSubtreeContentChange = true,
            contentSignature = 203,
            isBlockedByPolicy = false
        )
        assertNull(tracker.onObservation(likelyOnly))
    }

    @Test
    fun `different reel scrolled counts separately while rapid scroll burst is debounced`() {
        val clock = FakeReelCountClock()
        val tracker = ReelViewTracker(clock)

        assertNotNull(tracker.onObservation(confirmedObservation(contentSignature = 101)))

        // Rapid duplicate scroll callback within debounce window -> suppressed.
        clock.elapsedMs += 150L
        assertNull(
            tracker.onObservation(
                confirmedObservation(isScrollTransition = true, contentSignature = 202)
            )
        )

        // Genuine scroll after debounce window -> counted.
        clock.elapsedMs += 600L
        assertNotNull(
            tracker.onObservation(
                confirmedObservation(isScrollTransition = true, contentSignature = 202)
            )
        )
    }

    @Test
    fun `blocked reel attempt does not count as viewed`() {
        val clock = FakeReelCountClock()
        val tracker = ReelViewTracker(clock)

        val blocked = tracker.onObservation(
            confirmedObservation(contentSignature = 303, isBlockedByPolicy = true)
        )
        assertNull(blocked)
    }
}
