package com.focusguard.app.data.focus

import android.util.Log
import com.focusguard.app.data.analytics.AnalyticsRepository
import com.focusguard.app.data.settings.SettingsRepository
import com.focusguard.app.domain.focus.FocusBadge
import com.focusguard.app.domain.focus.FocusMode
import com.focusguard.app.domain.focus.FocusSessionState
import com.focusguard.app.persistence.FocusGuardPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FocusSessionRepository(
    private val prefs: FocusGuardPrefs,
    private val settingsRepository: SettingsRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    suspend fun snapshot(mode: FocusMode): FocusSessionState = withContext(ioDispatcher) {
        finalizeExpiredSessionIfNeeded(mode)
        buildState(mode)
    }

    suspend fun start(mode: FocusMode, accountabilityPin: String? = null): FocusSessionState = withContext(ioDispatcher) {
        val now = clock()
        val useAccountabilityLock = !accountabilityPin.isNullOrBlank()
        if (useAccountabilityLock && !prefs.startAccountabilityLock(
                pin = accountabilityPin!!,
                durationMs = mode.durationMinutes * 60_000L
            )
        ) {
            return@withContext buildState(mode)
        }
        prefs.isGuardActive = true
        prefs.focusSessionStartTime = now
        prefs.focusSessionModeName = mode.name
        prefs.isFocusSessionXpAwarded = false
        settingsRepository.enableStrictMode(
            durationMinutes = mode.durationMinutes,
            exitProtectionEnabled = prefs.isStrictModeExitProtectionEnabled || useAccountabilityLock,
            keepRequestedDuration = useAccountabilityLock
        )
        Log.d(TAG, "Focus session started: mode=${mode.name}, start=$now")
        buildState(mode)
    }

    suspend fun stop(mode: FocusMode, accountabilityPin: String? = null): FocusSessionState = withContext(ioDispatcher) {
        if (prefs.isAccountabilityLockActive && !prefs.verifyAccountabilityPin(accountabilityPin.orEmpty())) {
            Log.w(TAG, "Blocked focus-session exit: Accountability PIN required")
            return@withContext buildState(mode)
        }
        awardCurrentSessionXpIfNeeded(mode, clock())
        settingsRepository.disableStrictMode()
        prefs.isGuardActive = false
        prefs.clearFocusSessionMetadata()
        prefs.clearAccountabilityLock()
        Log.d(TAG, "Focus session stopped manually")
        buildState(mode).copy(
            sessionXp = 0,
            totalXp = prefs.totalFocusXp
        )
    }

    suspend fun setMode(mode: FocusMode): FocusSessionState = withContext(ioDispatcher) {
        buildState(mode)
    }

    private suspend fun buildState(mode: FocusMode): FocusSessionState {
        val now = clock()
        val activeMode = storedModeOr(mode)
        val isActive = prefs.blockEndTime > now
        val durationMillis = activeMode.durationMinutes * 60_000L
        val startedAt = sessionStartTimeOrFallback(activeMode, isActive)
        val remainingMillis = if (isActive) {
            (prefs.blockEndTime - now).coerceIn(0L, durationMillis)
        } else {
            durationMillis
        }
        val elapsedMillis = if (startedAt > 0L) {
            (minOf(now, prefs.blockEndTime) - startedAt).coerceIn(0L, durationMillis)
        } else {
            (durationMillis - remainingMillis).coerceAtLeast(0L)
        }
        val elapsedMinutes = (elapsedMillis / 60_000L).toInt().coerceAtLeast(0)
        val sessionXp = if (!prefs.isFocusSessionXpAwarded && startedAt > 0L) {
            elapsedMinutes * activeMode.xpPerMinute
        } else {
            0
        }
        val streak = runCatching { analyticsRepository.getStreak() }.getOrDefault(0)

        return FocusSessionState(
            mode = activeMode,
            isActive = isActive,
            durationMillis = durationMillis,
            remainingMillis = remainingMillis,
            sessionXp = sessionXp,
            totalXp = prefs.totalFocusXp + sessionXp,
            streakDays = streak,
            badges = badgesFor(sessionXp = sessionXp, totalXp = prefs.totalFocusXp + sessionXp, streak = streak),
            isAccountabilityLockActive = prefs.isAccountabilityLockActive
        )
    }

    private fun badgesFor(sessionXp: Int, totalXp: Int, streak: Int): List<FocusBadge> {
        return buildList {
            if (sessionXp >= 20) add(FocusBadge("session-spark", "20 XP"))
            if (sessionXp >= 60) add(FocusBadge("deep-flow", "Deep Flow"))
            if (streak >= 3) add(FocusBadge("streak", "${streak}d"))
            if (totalXp >= 500) add(FocusBadge("grinder", "500 XP"))
        }.takeLast(4)
    }

    private suspend fun finalizeExpiredSessionIfNeeded(fallbackMode: FocusMode) {
        val endTime = prefs.blockEndTime
        if (endTime <= 0L || clock() < endTime) return
        if (!prefs.isFocusSessionXpAwarded) {
            awardCurrentSessionXpIfNeeded(fallbackMode, endTime)
        }
        settingsRepository.disableStrictMode()
        prefs.isGuardActive = false
        prefs.clearFocusSessionMetadata()
        prefs.clearAccountabilityLock()
        Log.d(TAG, "Focus session finalized after natural timer completion")
    }

    private fun awardCurrentSessionXpIfNeeded(fallbackMode: FocusMode, endTime: Long) {
        if (prefs.isFocusSessionXpAwarded) return
        val mode = storedModeOr(fallbackMode)
        val durationMillis = mode.durationMinutes * 60_000L
        val startedAt = sessionStartTimeOrFallback(mode, isActive = prefs.blockEndTime > clock())
        if (startedAt <= 0L) return

        val elapsedMillis = (endTime - startedAt).coerceIn(0L, durationMillis)
        val earnedXp = ((elapsedMillis / 60_000L).toInt().coerceAtLeast(0)) * mode.xpPerMinute
        prefs.totalFocusXp = prefs.totalFocusXp + earnedXp
        prefs.isFocusSessionXpAwarded = true
        Log.d(TAG, "Focus XP awarded: $earnedXp for mode=${mode.name}")
    }

    private fun storedModeOr(fallbackMode: FocusMode): FocusMode {
        return runCatching { FocusMode.valueOf(prefs.focusSessionModeName) }
            .getOrDefault(fallbackMode)
    }

    private fun sessionStartTimeOrFallback(mode: FocusMode, isActive: Boolean): Long {
        val storedStart = prefs.focusSessionStartTime
        if (storedStart > 0L) return storedStart
        return if (isActive && prefs.blockEndTime > 0L) {
            prefs.blockEndTime - mode.durationMinutes * 60_000L
        } else {
            0L
        }
    }

    companion object {
        private const val TAG = "FocusSessionRepo"
    }
}
