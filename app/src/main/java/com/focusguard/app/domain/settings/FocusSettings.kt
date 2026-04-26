package com.focusguard.app.domain.settings

/**
 * Stable settings model used by presentation and domain layers.
 *
 * The existing services still read FocusGuardPrefs synchronously during this
 * transition, but new UI/ViewModel code should prefer this model.
 */
data class FocusSettings(
    val strictModeEndTimeMillis: Long = 0L,
    val strictModeDurationMinutes: Int = 60,
    val isStrictModeExitProtectionEnabled: Boolean = false,
    val hasAcceptedAccessibilityDisclosure: Boolean = false
) {
    val isStrictModeEnabled: Boolean
        get() = System.currentTimeMillis() < strictModeEndTimeMillis

    fun canBlockCriticalActions(isFocusActive: Boolean): Boolean {
        return isStrictModeEnabled &&
            isStrictModeExitProtectionEnabled &&
            isFocusActive &&
            hasAcceptedAccessibilityDisclosure
    }
}
