package com.focusguard.app.data.usage

object AnalyticsCalculator {
    private const val DEFAULT_SAVED_SESSION_MS = 6 * 60 * 1000L
    private const val MIN_SAVED_SESSION_MS = 2 * 60 * 1000L
    private const val MAX_SAVED_SESSION_MS = 10 * 60 * 1000L

    fun estimateSavedTimeMs(historicalAverageMs: Double?): Long {
        val baseline = historicalAverageMs?.toLong() ?: DEFAULT_SAVED_SESSION_MS
        return baseline.coerceIn(MIN_SAVED_SESSION_MS, MAX_SAVED_SESSION_MS)
    }
}
