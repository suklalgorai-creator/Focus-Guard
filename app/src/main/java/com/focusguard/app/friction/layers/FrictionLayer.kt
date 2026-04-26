package com.focusguard.app.friction.layers

import android.view.ViewGroup
import kotlinx.coroutines.CoroutineScope

/**
 * Base interface for all friction layers.
 * Each layer is a step in the friction pipeline that the user must survive.
 */
interface FrictionLayer {
    /** Human-readable name for logging */
    val name: String

    /**
     * Execute this friction layer.
     * Must update the overlay UI through the context and wait for user interaction
     * or timeout before returning a result.
     *
     * @param context Contains attempt info, escalation level, and overlay references
     * @return Result determining whether pipeline continues, restarts, or fails
     */
    suspend fun execute(context: FrictionContext): FrictionResult
}

/**
 * Context passed to each friction layer with all the info it needs.
 */
data class FrictionContext(
    /** Current attempt number for today (1-based) */
    val attemptNumber: Int,

    /** Current escalation level (increases with attempts and bypass penalties) */
    val escalationLevel: Int,

    /** The overlay's friction container for updating UI */
    val overlayView: ViewGroup,

    /** Coroutine scope for launching UI updates */
    val coroutineScope: CoroutineScope,

    /** The blocked app's package name */
    val blockedPackage: String,

    /** Days until Target exam (-1 if not set) */
    val daysUntilExam: Int = -1
)

/**
 * Result of executing a friction layer.
 */
sealed class FrictionResult {
    /** Layer passed — continue to next layer */
    object Passed : FrictionResult()

    /** Layer failed — restart the ENTIRE friction pipeline from scratch */
    object Restart : FrictionResult()

    /** User abandoned (closed app / went home) — cancel session */
    object Abandoned : FrictionResult()

    /** Fatal error — log and continue */
    data class Error(val message: String) : FrictionResult()
}
