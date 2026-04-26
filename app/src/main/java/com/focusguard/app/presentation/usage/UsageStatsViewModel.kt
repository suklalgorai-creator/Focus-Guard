package com.focusguard.app.presentation.usage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.usage.UsageRepository
import com.focusguard.app.data.usage.UsageStatsReconciler
import com.focusguard.app.domain.AppUsageData
import com.focusguard.app.domain.usage.UsageAnalyticsSummary
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
    val errorMessage: String? = null
)

class UsageStatsViewModel(
    private val usageRepository: UsageRepository,
    private val usageStatsReconciler: UsageStatsReconciler
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
                val snapshot = withContext(Dispatchers.IO) {
                    usageStatsReconciler.reconcileToday(force = force)
                    usageRepository.getUsageSnapshot(forceRefresh = force)
                }
                _state.value = UsageStatsUiState(
                    isLoading = false,
                    today = snapshot.today,
                    weeklyUsage = snapshot.weeklyUsage
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
            usageStatsReconciler: UsageStatsReconciler
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return UsageStatsViewModel(
                    usageRepository = usageRepository,
                    usageStatsReconciler = usageStatsReconciler
                ) as T
            }
        }
    }
}
