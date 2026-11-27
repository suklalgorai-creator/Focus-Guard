package com.focusguard.app.reelcount.integration

import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.reelcount.data.ReelCountRepository
import com.focusguard.app.reelcount.domain.ReelCountClock
import com.focusguard.app.reelcount.domain.ReelCountLimitState
import com.focusguard.app.reelcount.domain.ReelCountLimitStatus
import com.focusguard.app.reelcount.domain.ReelLimitEvaluator
import com.focusguard.app.reelcount.domain.ReelObservation
import com.focusguard.app.reelcount.domain.ReelViewTracker
import com.focusguard.app.reelcount.domain.ReelsBlockDecision
import com.focusguard.app.reelcount.domain.ReelsBlockingPolicy
import com.focusguard.app.reelcount.domain.SystemReelCountClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReelLimitPrompt(
    val dateKey: String,
    val viewedCount: Int,
    val dailyLimit: Int
)

/**
 * Coordinates Reel Count tracking, daily limit evaluation, and blocking policy state
 * inside Focus Guard without introducing duplicate services.
 */
class ReelCountCoordinator(
    private val repository: ReelCountRepository,
    private val prefs: FocusGuardPrefs,
    private val clock: ReelCountClock = SystemReelCountClock()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMutex = Mutex()
    private val tracker = ReelViewTracker(clock)
    private val limitEvaluator = ReelLimitEvaluator(clock)

    // Cached limit state for synchronous evaluation in AccessibilityService hot paths.
    @Volatile
    private var cachedLimitState: ReelCountLimitState = ReelCountLimitState(dateKey = clock.todayDateKey())

    private val _isInstagramForeground = MutableStateFlow(false)
    val isInstagramForeground: StateFlow<Boolean> = _isInstagramForeground.asStateFlow()

    private val _pendingLimitPrompt = MutableStateFlow<ReelLimitPrompt?>(null)
    val pendingLimitPrompt: StateFlow<ReelLimitPrompt?> = _pendingLimitPrompt.asStateFlow()

    init {
        scope.launch {
            cachedLimitState = repository.getTodayLimitState()
        }
    }

    fun onForegroundPackageChanged(packageName: String?) {
        val isInstagram = packageName == FocusGuardPrefs.INSTAGRAM_PACKAGE ||
            packageName == FocusGuardPrefs.INSTAGRAM_LITE_PACKAGE
        _isInstagramForeground.value = isInstagram
        tracker.onForegroundPackageChanged(packageName)
        repository.refreshActiveDate()
    }

    /**
     * Evaluates the combined blocking decision for Instagram Reels.
     * Focus Guard's own protection is always evaluated independently and cannot be weakened.
     */
    fun evaluateReelsBlockingPolicy(): ReelsBlockDecision {
        val todayKey = repository.refreshActiveDate()
        val focusGuardRequiresBlock = prefs.isGuardActiveNow() &&
            prefs.blockedContentSurfaces.contains(FocusGuardPrefs.SURFACE_INSTAGRAM_REELS)

        val settings = repository.currentSettings()
        val limitState = cachedLimitState.let {
            if (it.dateKey == todayKey) it else ReelCountLimitState(dateKey = todayKey)
        }

        return ReelsBlockingPolicy.evaluate(
            focusGuardRequiresBlock = focusGuardRequiresBlock,
            reelCountEnabled = settings.enabled,
            reelCountLimitEnabled = settings.limitEnabled,
            todayDateKey = todayKey,
            limitState = limitState
        )
    }

    /**
     * Processes a stabilized Reels observation from AccessibilityService when Reels is NOT blocked.
     */
    fun onInstagramObservation(observation: ReelObservation) {
        val settings = repository.currentSettings()
        if (!settings.enabled) return

        val event = tracker.onObservation(observation) ?: return

        scope.launch {
            stateMutex.withLock {
                val updatedStats = repository.recordReelView()
                val persistedLimit = repository.getTodayLimitState()
                val eval = limitEvaluator.evaluate(
                    settings = repository.currentSettings(),
                    stats = updatedStats,
                    persistedState = persistedLimit
                )
                cachedLimitState = eval.effectiveState
                if (eval.stateChanged) {
                    repository.updateLimitState(
                        dateKey = eval.effectiveState.dateKey,
                        status = eval.effectiveState.status,
                        lastPromptedAt = eval.effectiveState.lastPromptedAt
                    )
                }
                // Only show the Reel Count prompt if Focus Guard isn't already blocking Reels.
                val policy = evaluateReelsBlockingPolicy()
                if (eval.shouldPromptUser && !policy.shouldBlock) {
                    _pendingLimitPrompt.value = ReelLimitPrompt(
                        dateKey = updatedStats.dateKey,
                        viewedCount = updatedStats.viewedCount,
                        dailyLimit = settings.dailyLimit
                    )
                }
            }
        }
    }

    /**
     * Records a blocked Instagram Reels attempt separately from viewed count.
     */
    fun onReelBlockedAttempt() {
        if (!repository.currentSettings().enabled) return
        scope.launch {
            stateMutex.withLock {
                repository.recordBlockedAttempt()
            }
        }
    }

    fun onUserChoseBlockReels(dateKey: String) {
        _pendingLimitPrompt.value = null
        scope.launch {
            stateMutex.withLock {
                val updated = repository.updateLimitState(
                    dateKey = dateKey,
                    status = ReelCountLimitStatus.BLOCK_TODAY
                )
                cachedLimitState = updated
            }
        }
    }

    fun onUserChoseContinueWatching(dateKey: String) {
        _pendingLimitPrompt.value = null
        scope.launch {
            stateMutex.withLock {
                val updated = repository.updateLimitState(
                    dateKey = dateKey,
                    status = ReelCountLimitStatus.CONTINUE_TODAY
                )
                cachedLimitState = updated
            }
        }
    }

    fun onReelCountMasterToggled(enabled: Boolean) {
        repository.setEnabled(enabled)
        if (!enabled) {
            tracker.reset()
            _pendingLimitPrompt.value = null
        }
    }
}
