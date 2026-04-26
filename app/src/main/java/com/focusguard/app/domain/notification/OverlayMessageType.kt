package com.focusguard.app.domain.notification

enum class OverlayMessageType {
    QUICK_CHALLENGE,
    REALITY_CHECK,
    PERSONAL_PUSH,
    LIGHT_TEASE,
    MISTAKE,
    COMEBACK,
    ULTRA_SHORT
}

data class OverlayMessageContext(
    val weakSubject: String? = null,
    val hasWrongStreak: Boolean = false,
    val isComeback: Boolean = false,
    val useUltraShort: Boolean = false
)
