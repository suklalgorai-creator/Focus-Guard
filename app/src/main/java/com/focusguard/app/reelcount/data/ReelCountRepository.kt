package com.focusguard.app.reelcount.data

import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.reelcount.domain.ReelCountClock
import com.focusguard.app.reelcount.domain.ReelCountDailyStats
import com.focusguard.app.reelcount.domain.ReelCountLimitState
import com.focusguard.app.reelcount.domain.ReelCountLimitStatus
import com.focusguard.app.reelcount.domain.ReelCountSettings
import com.focusguard.app.reelcount.domain.SystemReelCountClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Single source of truth for Reel Count settings, daily statistics, and daily limit state.
 *
 * - Preferences are backed by [FocusGuardPrefs] for instant synchronous reads in AccessibilityService
 *   and mirrored via [settingsFlow] for reactive Compose UI.
 * - Daily stats & limit decisions are persisted in Room via [ReelCountDao] so historical data
 *   is preserved even when Reel Count is disabled.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReelCountRepository(
    private val dao: ReelCountDao,
    private val prefs: FocusGuardPrefs,
    private val clock: ReelCountClock = SystemReelCountClock()
) {
    private val _settingsFlow = MutableStateFlow(readSettingsSnapshot())
    val settingsFlow: StateFlow<ReelCountSettings> = _settingsFlow.asStateFlow()

    private val _activeDateKeyFlow = MutableStateFlow(clock.todayDateKey())
    val activeDateKeyFlow: StateFlow<String> = _activeDateKeyFlow.asStateFlow()

    /**
     * Reactive today stats that automatically switch to the new day's row when [refreshActiveDate]
     * detects a local midnight rollover.
     */
    val todayStatsFlow: Flow<ReelCountDailyStats> = _activeDateKeyFlow
        .flatMapLatest { dateKey ->
            dao.observeStatsForDate(dateKey).map { entity ->
                entity?.toDomain() ?: ReelCountDailyStats(dateKey = dateKey)
            }
        }
        .distinctUntilChanged()

    val todayLimitStateFlow: Flow<ReelCountLimitState> = _activeDateKeyFlow
        .flatMapLatest { dateKey ->
            dao.observeLimitStateForDate(dateKey).map { entity ->
                entity?.toDomain() ?: ReelCountLimitState(dateKey = dateKey)
            }
        }
        .distinctUntilChanged()

    val recentStatsFlow: Flow<List<ReelCountDailyStats>> = dao.observeRecentStats(14)
        .map { list -> list.map { it.toDomain() } }

    val limitReachedDaysCountFlow: Flow<Int> = dao.observeLimitReachedDaysCount()

    fun currentSettings(): ReelCountSettings = readSettingsSnapshot()

    fun currentDateKey(): String {
        val today = clock.todayDateKey()
        if (_activeDateKeyFlow.value != today) {
            _activeDateKeyFlow.value = today
        }
        return today
    }

    fun refreshActiveDate(): String = currentDateKey()

    fun setEnabled(enabled: Boolean) {
        prefs.isReelCountEnabled = enabled
        _settingsFlow.value = readSettingsSnapshot()
    }

    fun setFloatingCounterEnabled(enabled: Boolean) {
        prefs.isReelCountFloatingCounterEnabled = enabled
        _settingsFlow.value = readSettingsSnapshot()
    }

    fun setDailyLimit(limit: Int) {
        val validated = limit.coerceIn(
            ReelCountSettings.MIN_DAILY_LIMIT,
            ReelCountSettings.MAX_DAILY_LIMIT
        )
        prefs.reelCountDailyLimit = validated
        _settingsFlow.value = readSettingsSnapshot()
    }

    fun setLimitEnabled(enabled: Boolean) {
        prefs.isReelCountLimitEnabled = enabled
        _settingsFlow.value = readSettingsSnapshot()
    }

    suspend fun recordReelView(): ReelCountDailyStats {
        val dateKey = currentDateKey()
        val settings = readSettingsSnapshot()
        val updated = dao.incrementViewedCount(
            dateKey = dateKey,
            updatedAt = clock.nowMillis(),
            dailyLimit = settings.dailyLimit,
            limitEnabled = settings.limitEnabled
        )
        return updated.toDomain()
    }

    suspend fun recordBlockedAttempt(): ReelCountDailyStats {
        val dateKey = currentDateKey()
        val updated = dao.incrementBlockedAttempts(
            dateKey = dateKey,
            updatedAt = clock.nowMillis()
        )
        return updated.toDomain()
    }

    suspend fun getTodayStats(): ReelCountDailyStats {
        val dateKey = currentDateKey()
        return dao.getStatsForDate(dateKey)?.toDomain() ?: ReelCountDailyStats(dateKey = dateKey)
    }

    suspend fun getTodayLimitState(): ReelCountLimitState {
        val dateKey = currentDateKey()
        return dao.getLimitStateForDate(dateKey)?.toDomain()
            ?: ReelCountLimitState(dateKey = dateKey)
    }

    suspend fun updateLimitState(
        dateKey: String,
        status: ReelCountLimitStatus,
        lastPromptedAt: Long? = null
    ): ReelCountLimitState {
        val existing = dao.getLimitStateForDate(dateKey)
        val now = clock.nowMillis()
        val entity = ReelCountLimitStateEntity(
            dateKey = dateKey,
            status = status.name,
            lastPromptedAt = lastPromptedAt ?: existing?.lastPromptedAt ?: 0L,
            decisionUpdatedAt = now
        )
        dao.upsertLimitState(entity)
        return entity.toDomain()
    }

    private fun readSettingsSnapshot(): ReelCountSettings {
        return ReelCountSettings(
            enabled = prefs.isReelCountEnabled,
            floatingCounterEnabled = prefs.isReelCountFloatingCounterEnabled,
            dailyLimit = prefs.reelCountDailyLimit.coerceIn(
                ReelCountSettings.MIN_DAILY_LIMIT,
                ReelCountSettings.MAX_DAILY_LIMIT
            ),
            limitEnabled = prefs.isReelCountLimitEnabled
        )
    }
}
