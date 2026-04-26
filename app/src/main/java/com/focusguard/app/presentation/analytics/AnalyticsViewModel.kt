package com.focusguard.app.presentation.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.analytics.AnalyticsRepository
import com.focusguard.app.domain.analytics.AnalyticsDashboardState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnalyticsViewModel(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsDashboardState(isLoading = true))
    val state: StateFlow<AnalyticsDashboardState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            val previousState = _state.value
            val nextState = runCatching {
                withContext(Dispatchers.IO) {
                    analyticsRepository.getDashboardState(forceRefresh = force)
                }
            }.fold(
                onSuccess = { it.copy(isLoading = false, errorMessage = null) },
                onFailure = {
                    previousState.copy(
                        isLoading = false,
                        errorMessage = it.message ?: "Unable to load analytics right now."
                    )
                }
            )
            _state.value = nextState
        }
    }

    companion object {
        fun factory(analyticsRepository: AnalyticsRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AnalyticsViewModel(analyticsRepository) as T
                }
            }
        }
    }
}
