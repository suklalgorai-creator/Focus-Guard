package com.focusguard.app.presentation.focus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.focus.FocusSessionRepository
import com.focusguard.app.domain.focus.FocusMode
import com.focusguard.app.domain.focus.FocusSessionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FocusSessionViewModel(
    private val repository: FocusSessionRepository
) : ViewModel() {

    private val _state = MutableStateFlow(FocusSessionState())
    val state: StateFlow<FocusSessionState> = _state.asStateFlow()

    private var tickerJob: Job? = null

    init {
        refresh()
        startTicker()
    }

    fun setMode(mode: FocusMode) {
        if (_state.value.isActive) return
        viewModelScope.launch {
            _state.value = repository.setMode(mode)
        }
    }

    fun startFocus() {
        viewModelScope.launch {
            _state.value = repository.start(_state.value.mode)
            startTicker()
        }
    }

    fun endSession() {
        viewModelScope.launch {
            _state.value = repository.stop(_state.value.mode)
            startTicker()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = repository.snapshot(_state.value.mode)
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                _state.value = repository.snapshot(_state.value.mode)
                delay(1_000L)
            }
        }
    }

    override fun onCleared() {
        tickerJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(repository: FocusSessionRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FocusSessionViewModel(repository) as T
                }
            }
        }
    }
}
