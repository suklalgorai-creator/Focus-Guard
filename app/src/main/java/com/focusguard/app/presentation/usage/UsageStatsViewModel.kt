package com.focusguard.app.presentation.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.usage.UsageRepository
import com.focusguard.app.data.usage.UsageStatsReconciler
import com.focusguard.app.domain.AppUsageData
import com.focusguard.app.domain.usage.UsageAnalyticsSummary
import com.focusguard.app.persistence.AttemptDao
import com.focusguard.app.persistence.AttemptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UsageStatsUiState(
    val isLoading: Boolean = true,
    val today: UsageAnalyticsSummary = UsageAnalyticsSummary(dateKey = ""),
    val weeklyUsage: Map<String, List<AppUsageData>> = emptyMap(),
    val recentBlocks: List<AttemptEntity> = emptyList(),
    val errorMessage: String? = null
)

class UsageStatsViewModel(
    private val usageRepository: UsageRepository,
    private val usageStatsReconciler: UsageStatsReconciler,
    private val attemptDao: AttemptDao? = null
) : ViewModel() {

    private val _state = MutableStateFlow(UsageStatsUiState())
    val state: StateFlow<UsageStatsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val (snapshot, recentBlocks) = withContext(Dispatchers.IO) {
                    usageStatsReconciler.reconcileToday(force = force)
                    val usage = usageRepository.getUsageSnapshot(forceRefresh = force)
                    val blocks = attemptDao?.getRecentAttempts(20).orEmpty()
                    usage to blocks
                }
                _state.value = UsageStatsUiState(
                    isLoading = false,
                    today = snapshot.today,
                    weeklyUsage = snapshot.weeklyUsage,
                    recentBlocks = recentBlocks
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Unable to load usage analytics"
                )
            }
        }
    }

    companion object {
        fun factory(
            usageRepository: UsageRepository,
            usageStatsReconciler: UsageStatsReconciler,
            attemptDao: AttemptDao? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UsageStatsViewModel(
                    usageRepository = usageRepository,
                    usageStatsReconciler = usageStatsReconciler,
                    attemptDao = attemptDao
                ) as T
            }
        }
    }
}
