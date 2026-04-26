package com.focusguard.app.data.auth

import android.app.Activity
import com.focusguard.app.data.analytics.AnalyticsRepository
import com.focusguard.app.domain.auth.AuthSession
import com.focusguard.app.domain.auth.AuthUiState
import com.focusguard.app.domain.auth.FocusScheduleSnapshot
import com.focusguard.app.domain.auth.LocalUserSettings
import com.focusguard.app.domain.auth.RemoteUserSettings
import com.focusguard.app.persistence.FocusGuardPrefs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuthRepository(
    private val config: AuthConfig,
    private val sessionStore: AuthSessionStore,
    private val googleAuthManager: GoogleAuthManager,
    private val supabaseAuthApi: SupabaseAuthApi,
    private val prefs: FocusGuardPrefs,
    private val analyticsRepository: AnalyticsRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val _state = MutableStateFlow(
        AuthUiState(
            isInitializing = true,
            isConfigured = config.isConfigured
        )
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        repositoryScope.launch {
            restoreSession()
        }
    }

    suspend fun signIn(activity: Activity) {
        _state.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                isConfigured = config.isConfigured
            )
        }

        try {
            val googleToken = googleAuthManager.getGoogleIdToken(activity)
            val session = supabaseAuthApi.signInWithGoogleIdToken(
                idToken = googleToken.idToken,
                nonce = googleToken.nonce
            )
            withContext(ioDispatcher) {
                sessionStore.saveSession(session)
                supabaseAuthApi.upsertUserAndSettings(session, buildLocalSettings())
                fetchAndApplyRemoteSettings(session)
            }
            _state.value = AuthUiState(
                isInitializing = false,
                isLoading = false,
                user = session.user,
                hasSkippedLogin = false,
                isConfigured = config.isConfigured
            )
        } catch (exception: Exception) {
            _state.update {
                it.copy(
                    isInitializing = false,
                    isLoading = false,
                    errorMessage = userFacingError(exception),
                    isConfigured = config.isConfigured
                )
            }
        }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        authenticateWithEmail(
            email = email,
            password = password,
            name = null,
            createAccount = false
        )
    }

    suspend fun signUpWithEmail(email: String, password: String, name: String?) {
        authenticateWithEmail(
            email = email,
            password = password,
            name = name,
            createAccount = true
        )
    }

    fun continueWithoutAccount() {
        sessionStore.setLoginPromptSkipped(true)
        _state.value = AuthUiState(
            isInitializing = false,
            isLoading = false,
            user = null,
            hasSkippedLogin = true,
            isConfigured = config.isConfigured
        )
    }

    suspend fun signOut() {
        val existing = sessionStore.loadSession()
        existing?.accessToken?.let { token ->
            supabaseAuthApi.logout(token)
        }
        sessionStore.clearSession()
        sessionStore.setLoginPromptSkipped(true)
        _state.value = AuthUiState(
            isInitializing = false,
            hasSkippedLogin = true,
            isConfigured = config.isConfigured
        )
    }

    suspend fun syncUserSettings() {
        val session = getValidSessionOrNull() ?: return
        runCatching {
            supabaseAuthApi.upsertUserAndSettings(session, buildLocalSettings())
        }.onFailure { exception ->
            _state.update { it.copy(errorMessage = userFacingError(exception)) }
        }
    }

    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private suspend fun authenticateWithEmail(
        email: String,
        password: String,
        name: String?,
        createAccount: Boolean
    ) {
        val normalizedEmail = email.trim()
        val trimmedName = name?.trim()?.takeIf { it.isNotBlank() }
        val validationError = validateEmailPassword(normalizedEmail, password)
        if (validationError != null) {
            _state.update { it.copy(errorMessage = validationError) }
            return
        }

        _state.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                isConfigured = config.isConfigured
            )
        }

        try {
            val session = if (createAccount) {
                supabaseAuthApi.signUpWithEmail(
                    email = normalizedEmail,
                    password = password,
                    name = trimmedName
                )
            } else {
                supabaseAuthApi.signInWithEmail(
                    email = normalizedEmail,
                    password = password
                )
            }
            withContext(ioDispatcher) {
                sessionStore.saveSession(session)
                supabaseAuthApi.upsertUserAndSettings(session, buildLocalSettings())
                fetchAndApplyRemoteSettings(session)
            }
            _state.value = AuthUiState(
                isInitializing = false,
                isLoading = false,
                user = session.user,
                hasSkippedLogin = false,
                isConfigured = config.isConfigured
            )
        } catch (exception: Exception) {
            _state.update {
                it.copy(
                    isInitializing = false,
                    isLoading = false,
                    errorMessage = userFacingError(exception),
                    isConfigured = config.isConfigured
                )
            }
        }
    }

    private fun validateEmailPassword(email: String, password: String): String? {
        return when {
            !config.isConfigured -> "Supabase auth is not configured for this build."
            email.isBlank() || !email.contains("@") || !email.contains(".") ->
                "Enter a valid email address."
            password.length < 8 ->
                "Password must be at least 8 characters."
            else -> null
        }
    }

    private suspend fun restoreSession() {
        val skipped = sessionStore.hasSkippedLogin()
        if (!config.isConfigured) {
            _state.value = AuthUiState(
                isInitializing = false,
                hasSkippedLogin = skipped,
                isConfigured = false
            )
            return
        }

        val session = sessionStore.loadSession()
        if (session == null) {
            _state.value = AuthUiState(
                isInitializing = false,
                hasSkippedLogin = skipped,
                isConfigured = true
            )
            return
        }

        val activeSession = getValidSessionOrNull(session)
        if (activeSession == null) {
            sessionStore.clearSession()
            _state.value = AuthUiState(
                isInitializing = false,
                hasSkippedLogin = skipped,
                isConfigured = true
            )
            return
        }

        runCatching { fetchAndApplyRemoteSettings(activeSession) }
        _state.value = AuthUiState(
            isInitializing = false,
            user = activeSession.user,
            hasSkippedLogin = false,
            isConfigured = true
        )
    }

    private suspend fun getValidSessionOrNull(
        currentSession: AuthSession? = sessionStore.loadSession()
    ): AuthSession? {
        val session = currentSession ?: return null
        if (!session.isExpired()) return session

        return runCatching {
            supabaseAuthApi.refreshSession(session)
        }.getOrNull()?.also { refreshed ->
            sessionStore.saveSession(refreshed)
        }
    }

    private suspend fun buildLocalSettings(): LocalUserSettings {
        val streak = runCatching { analyticsRepository.getStreak() }.getOrDefault(0)
        return LocalUserSettings(
            blockedApps = prefs.blacklistedApps,
            focusSchedule = FocusScheduleSnapshot(
                enabled = prefs.isScheduleEnabled,
                startHour = prefs.scheduleStartHour,
                startMinute = prefs.scheduleStartMinute,
                endHour = prefs.scheduleEndHour,
                endMinute = prefs.scheduleEndMinute,
                days = prefs.scheduleDays
            ),
            streak = streak
        )
    }

    private suspend fun fetchAndApplyRemoteSettings(session: AuthSession) {
        val remote = supabaseAuthApi.fetchUserSettings(session) ?: return
        applyRemoteSettings(remote)
    }

    private fun applyRemoteSettings(remote: RemoteUserSettings) {
        remote.blockedApps?.let { prefs.blacklistedApps = it }
        remote.focusSchedule?.let { schedule ->
            prefs.isScheduleEnabled = schedule.enabled
            prefs.scheduleStartHour = schedule.startHour.coerceIn(0, 23)
            prefs.scheduleStartMinute = schedule.startMinute.coerceIn(0, 59)
            prefs.scheduleEndHour = schedule.endHour.coerceIn(0, 23)
            prefs.scheduleEndMinute = schedule.endMinute.coerceIn(0, 59)
            prefs.scheduleDays = schedule.days.filter { it in 1..7 }.toSet().ifEmpty {
                setOf(2, 3, 4, 5, 6, 7)
            }
        }
    }

    private fun userFacingError(exception: Throwable): String {
        return when (exception) {
            is AuthException -> exception.message
            is SupabaseException -> exception.message
            else -> exception.message ?: "Authentication failed. Please try again."
        }
    }
}
