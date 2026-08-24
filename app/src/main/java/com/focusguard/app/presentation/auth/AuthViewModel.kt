package com.focusguard.app.presentation.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.auth.AuthRepository
import com.focusguard.app.domain.auth.AuthUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val state: StateFlow<AuthUiState> = authRepository.state

    fun signIn(activity: Activity) {
        launchAuthAction {
            authRepository.signIn(activity)
        }
    }

    fun signInWithEmail(email: String, password: String) {
        launchAuthAction {
            authRepository.signInWithEmail(email, password)
        }
    }

    fun signUpWithEmail(email: String, password: String, name: String?) {
        launchAuthAction {
            authRepository.signUpWithEmail(email, password, name)
        }
    }

    fun continueWithoutAccount() {
        authRepository.continueWithoutAccount()
    }

    fun showLogin() {
        authRepository.showLoginPrompt()
    }

    fun signOut() {
        launchAuthAction {
            authRepository.signOut()
        }
    }

    fun syncUserSettings() {
        launchAuthAction {
            authRepository.syncUserSettings()
        }
    }

    fun clearError() {
        authRepository.clearError()
    }

    private fun launchAuthAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching {
                action()
            }.onFailure { exception ->
                authRepository.reportUnexpectedError(exception)
            }
        }
    }

    companion object {
        fun factory(authRepository: AuthRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AuthViewModel(authRepository) as T
                }
            }
        }
    }
}
