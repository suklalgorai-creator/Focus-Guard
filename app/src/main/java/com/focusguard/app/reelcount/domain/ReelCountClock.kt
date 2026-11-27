package com.focusguard.app.reelcount.domain

import android.os.SystemClock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Time abstraction for Reel Count so daily midnight rollovers, debouncing,
 * and timezone changes are deterministic and unit-testable.
 */
interface ReelCountClock {
    fun nowMillis(): Long
    fun elapsedRealtimeMs(): Long
    fun todayDateKey(): String
}

class SystemReelCountClock(
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) : ReelCountClock {

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()

    override fun todayDateKey(): String {
        val localDate = Instant.ofEpochMilli(nowMillis())
            .atZone(zoneIdProvider())
            .toLocalDate()
        return localDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
