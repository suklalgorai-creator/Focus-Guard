package com.focusguard.app.detection

import android.util.Log

/**
 * Privacy-safe structured diagnostic logger for detection, blocking, and permission recovery.
 * Never logs node text, captions, usernames, or personal content.
 */
object GuardDiagnostics {

    private const val TAG = "FocusGuardDiag"

    fun logEvent(event: String, detail: String = "") {
        if (detail.isBlank()) {
            Log.i(TAG, event)
        } else {
            Log.i(TAG, "$event | $detail")
        }
    }

    const val REELS_DETECTED = "REELS_DETECTED"
    const val SHORTS_DETECTED = "SHORTS_DETECTED"
    const val BLOCK_REASON_FOCUS_SESSION = "BLOCK_REASON_FOCUS_SESSION"
    const val BLOCK_REASON_SCHEDULE = "BLOCK_REASON_SCHEDULE"
    const val BLOCK_REASON_STRICT_MODE = "BLOCK_REASON_STRICT_MODE"
    const val BLOCK_REASON_REEL_LIMIT = "BLOCK_REASON_REEL_LIMIT"
    const val BLOCK_EXECUTED = "BLOCK_EXECUTED"
    const val BLOCK_SUPPRESSED_DUPLICATE = "BLOCK_SUPPRESSED_DUPLICATE"
    const val PERMISSION_UNAVAILABLE = "PERMISSION_UNAVAILABLE"
    const val PROTECTION_RECOVERED = "PROTECTION_RECOVERED"
}
