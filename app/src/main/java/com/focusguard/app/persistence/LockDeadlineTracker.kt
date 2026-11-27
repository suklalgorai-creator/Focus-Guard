package com.focusguard.app.persistence

/**
 * Reboot-safe, clock-manipulation-resistant deadline evaluator.
 *
 * Within a single boot session, monotonic elapsed time ([nowElapsedMs]) is authoritative so
 * backwards wall-clock changes cannot extend or prematurely expire a lock.
 * Across a reboot (detected when [nowElapsedMs] < [LockDeadlineState.lastKnownElapsedMs]),
 * remaining time is safely recalculated from wall-clock bounds and clamped to the last
 * known remaining duration so a reboot neither wipes nor inflates the lock.
 */
data class LockDeadlineState(
    val wallStartMs: Long = 0L,
    val wallDeadlineMs: Long = 0L,
    val elapsedDeadlineMs: Long = 0L,
    val lastKnownElapsedMs: Long = 0L,
    val remainingAtCheckpointMs: Long = 0L,
    val originalDurationMs: Long = 0L
)

data class LockEvaluation(
    val isActive: Boolean,
    val remainingMs: Long,
    val updatedState: LockDeadlineState,
    val rebootDetected: Boolean = false
)

object LockDeadlineTracker {

    private const val CLOCK_BACKWARD_TOLERANCE_MS = 5_000L

    fun create(
        durationMs: Long,
        nowWallMs: Long,
        nowElapsedMs: Long
    ): LockDeadlineState {
        if (durationMs <= 0L || nowWallMs <= 0L || nowElapsedMs < 0L) {
            return LockDeadlineState()
        }
        return LockDeadlineState(
            wallStartMs = nowWallMs,
            wallDeadlineMs = nowWallMs + durationMs,
            elapsedDeadlineMs = nowElapsedMs + durationMs,
            lastKnownElapsedMs = nowElapsedMs,
            remainingAtCheckpointMs = durationMs,
            originalDurationMs = durationMs
        )
    }

    fun evaluate(
        state: LockDeadlineState,
        nowWallMs: Long,
        nowElapsedMs: Long
    ): LockEvaluation {
        if (state.originalDurationMs <= 0L || state.wallDeadlineMs <= 0L || nowWallMs <= 0L || nowElapsedMs < 0L) {
            return LockEvaluation(
                isActive = false,
                remainingMs = 0L,
                updatedState = LockDeadlineState()
            )
        }

        val maxCapMs = state.originalDurationMs
        val rebootDetected = state.lastKnownElapsedMs > 0L && nowElapsedMs < state.lastKnownElapsedMs

        if (!rebootDetected && state.elapsedDeadlineMs > 0L) {
            val elapsedRemaining = (state.elapsedDeadlineMs - nowElapsedMs).coerceAtMost(maxCapMs)
            if (elapsedRemaining <= 0L) {
                return LockEvaluation(
                    isActive = false,
                    remainingMs = 0L,
                    updatedState = LockDeadlineState()
                )
            }
            val updated = state.copy(
                lastKnownElapsedMs = nowElapsedMs,
                remainingAtCheckpointMs = elapsedRemaining
            )
            return LockEvaluation(
                isActive = true,
                remainingMs = elapsedRemaining,
                updatedState = updated,
                rebootDetected = false
            )
        }

        // Reboot detected (or legacy wall-clock-only state): recalculate safely using wall clock,
        // clamping against both clock-backward manipulation and clock-forward extension.
        val checkpointCap = if (state.remainingAtCheckpointMs > 0L) {
            state.remainingAtCheckpointMs.coerceAtMost(maxCapMs)
        } else {
            maxCapMs
        }

        val clockMovedBackBeforeStart = state.wallStartMs > 0L &&
            nowWallMs < (state.wallStartMs - CLOCK_BACKWARD_TOLERANCE_MS)

        val recalculatedRemaining = if (clockMovedBackBeforeStart) {
            checkpointCap
        } else {
            (state.wallDeadlineMs - nowWallMs).coerceIn(0L, checkpointCap)
        }

        if (recalculatedRemaining <= 0L) {
            return LockEvaluation(
                isActive = false,
                remainingMs = 0L,
                updatedState = LockDeadlineState(),
                rebootDetected = rebootDetected
            )
        }

        val reanchoredState = state.copy(
            wallStartMs = nowWallMs,
            wallDeadlineMs = nowWallMs + recalculatedRemaining,
            elapsedDeadlineMs = nowElapsedMs + recalculatedRemaining,
            lastKnownElapsedMs = nowElapsedMs,
            remainingAtCheckpointMs = recalculatedRemaining
        )

        return LockEvaluation(
            isActive = true,
            remainingMs = recalculatedRemaining,
            updatedState = reanchoredState,
            rebootDetected = rebootDetected
        )
    }
}
