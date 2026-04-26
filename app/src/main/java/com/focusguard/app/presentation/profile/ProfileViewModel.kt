package com.focusguard.app.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.profile.ProfileRepository
import com.focusguard.app.domain.profile.UserProfile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    val profileFlow: StateFlow<UserProfile> = profileRepository.profile.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = profileRepository.getLegacySnapshot()
    )

    fun saveProfile(
        exam: String,
        targetDate: Long,
        preferredSubjects: List<String>,
        onSaved: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            profileRepository.saveProfile(
                exam = exam,
                targetDate = targetDate,
                preferredSubjects = preferredSubjects,
                isOnboardingComplete = true
            )
            onSaved?.invoke()
        }
    }

    fun skipOnboarding(onSkipped: (() -> Unit)? = null) {
        viewModelScope.launch {
            profileRepository.skipOnboarding()
            onSkipped?.invoke()
        }
    }

    companion object {
        fun factory(profileRepository: ProfileRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ProfileViewModel(profileRepository) as T
                }
            }
        }
    }
}
