package com.focusguard.app.reelcount.domain

import com.focusguard.app.detection.ReelsClassification

/**
 * Pure-Kotlin observation derived from an Instagram accessibility event.
 * Contains no raw message/caption text—only structural classification and an ephemeral hash.
 */
data class ReelObservation(
    val packageName: String,
    val classification: ReelsClassification,
    val confidenceScore: Int,
    val hasClipsViewerId: Boolean,
    val isScrollTransition: Boolean,
    val isSubtreeContentChange: Boolean,
    val contentSignature: Int,
    val isBlockedByPolicy: Boolean
)

/**
 * Stateful, pure-Kotlin tracker that converts stabilized Instagram Reels signals
 * into deduplicated [ReelTrackingEvent.ReelViewDetected] events.
 *
 * Guarantees:
 * - Opening Instagram, Feed, Profile, DMs, Stories, or YouTube Shorts never increments.
 * - Merely opening the Reels tab without an active Reels viewer is not counted.
 * - Staying on the same Reel does not re-count on repeated content-changed callbacks.
 * - Rapid scroll bursts are debounced.
 * - Blocked Reel attempts never increment viewed count.
 */
class ReelViewTracker(
    private val clock: ReelCountClock
) {
    private var currentPackage: String? = null
    private var inActiveReelsViewer: Boolean = false
    private var lastContentSignature: Int = 0
    private var lastCountedElapsedMs: Long = 0L

    fun onForegroundPackageChanged(packageName: String?) {
        if (packageName != currentPackage) {
            reset(packageName)
        }
    }

    fun reset(packageName: String? = null) {
        currentPackage = packageName
        inActiveReelsViewer = false
        lastContentSignature = 0
        lastCountedElapsedMs = 0L
    }

    fun onObservation(observation: ReelObservation): ReelTrackingEvent.ReelViewDetected? {
        if (observation.packageName != currentPackage) {
            reset(observation.packageName)
        }

        // Blocked attempts must never be counted as viewed Reels.
        if (observation.isBlockedByPolicy) {
            return null
        }

        val isConfirmedViewer = observation.classification == ReelsClassification.REELS_CONFIRMED &&
            (observation.hasClipsViewerId || observation.confidenceScore >= MIN_CONFIRMED_SCORE)

        if (!isConfirmedViewer) {
            if (observation.classification == ReelsClassification.NOT_REELS) {
                inActiveReelsViewer = false
                lastContentSignature = 0
            }
            return null
        }

        val nowElapsed = clock.elapsedRealtimeMs()

        // Case 1: User just entered the Reels viewer from a non-Reels surface.
        if (!inActiveReelsViewer) {
            inActiveReelsViewer = true
            if (lastCountedElapsedMs > 0L && (nowElapsed - lastCountedElapsedMs) < DEBOUNCE_WINDOW_MS) {
                return null
            }
            lastCountedElapsedMs = nowElapsed
            lastContentSignature = observation.contentSignature
            return ReelTrackingEvent.ReelViewDetected(
                dateKey = clock.todayDateKey(),
                timestampMs = clock.nowMillis(),
                confidenceScore = observation.confidenceScore
            )
        }

        // Case 2: Already inside Reels viewer—require a genuine transition to a different Reel.
        val hasDifferentSignature = observation.contentSignature != 0 &&
            lastContentSignature != 0 &&
            observation.contentSignature != lastContentSignature

        val isGenuineTransition = observation.isScrollTransition ||
            (observation.isSubtreeContentChange && hasDifferentSignature)

        if (!isGenuineTransition) {
            return null
        }

        // Suppress rapid-fire duplicate scroll events for the same swipe.
        if ((nowElapsed - lastCountedElapsedMs) < DEBOUNCE_WINDOW_MS) {
            return null
        }

        // If the structural signature is identical to the previous Reel, require a longer settle
        // window so repeated accessibility callbacks on a resting Reel cannot double-count.
        if (observation.contentSignature != 0 &&
            observation.contentSignature == lastContentSignature &&
            (nowElapsed - lastCountedElapsedMs) < SAME_SIGNATURE_GUARD_MS
        ) {
            return null
        }

        lastCountedElapsedMs = nowElapsed
        if (observation.contentSignature != 0) {
            lastContentSignature = observation.contentSignature
        }

        return ReelTrackingEvent.ReelViewDetected(
            dateKey = clock.todayDateKey(),
            timestampMs = clock.nowMillis(),
            confidenceScore = observation.confidenceScore
        )
    }

    companion object {
        const val MIN_CONFIRMED_SCORE = 70
        const val DEBOUNCE_WINDOW_MS = 450L
        const val SAME_SIGNATURE_GUARD_MS = 1_500L
    }
}
