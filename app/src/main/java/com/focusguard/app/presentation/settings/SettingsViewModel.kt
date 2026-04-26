package com.focusguard.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.settings.SettingsRepository
import com.focusguard.app.domain.settings.FocusSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val settingsFlow: StateFlow<FocusSettings> = settingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = settingsRepository.getLegacySnapshot()
        )

    fun toggleStrictMode(enabled: Boolean, durationMinutes: Int, exitProtectionEnabled: Boolean) {
        viewModelScope.launch {
            if (enabled) {
                settingsRepository.enableStrictMode(
                    durationMinutes = durationMinutes,
                    exitProtectionEnabled = exitProtectionEnabled
                )
            } else {
                settingsRepository.disableStrictMode()
            }
        }
    }

    fun setStrictModeDurationMinutes(durationMinutes: Int) {
        viewModelScope.launch {
            settingsRepository.setStrictModeDurationMinutes(durationMinutes)
        }
    }

    fun setExitProtection(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setStrictModeExitProtectionEnabled(enabled)
        }
    }

    fun setDisclosureAccepted(accepted: Boolean, afterSave: (() -> Unit)? = null) {
        viewModelScope.launch {
            settingsRepository.setAccessibilityDisclosureAccepted(accepted)
            afterSave?.invoke()
        }
    }

    companion object {
        fun factory(settingsRepository: SettingsRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                        return SettingsViewModel(settingsRepository) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }
}
