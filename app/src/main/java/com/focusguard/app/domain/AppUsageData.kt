package com.focusguard.app.domain

/**
 * Represents usage statistics for a single app within a time period.
 * Used by the Usage Stats screen to display per-app time data.
 */
data class AppUsageData(
    /** The package name */
    val packageName: String,

    /** Human-readable app name */
    val appName: String,

    /** Total foreground time in milliseconds */
    val usageTimeMs: Long,

    /** Whether this app is in the user's blacklist */
    val isBlacklisted: Boolean = false,

    /** Number of foreground sessions recorded for this app in the selected period. */
    val sessionCount: Int = 0
) {
    /** Formatted usage time string, e.g. "2h 15m" or "45m" or "< 1m" */
    val formattedTime: String
        get() {
            val minutes = usageTimeMs / (1000 * 60)
            val hours = minutes / 60
            val remainingMinutes = minutes % 60
            return when {
                hours > 0 -> "${hours}h ${remainingMinutes}m"
                minutes > 0 -> "${minutes}m"
                else -> "< 1m"
            }
        }
}
