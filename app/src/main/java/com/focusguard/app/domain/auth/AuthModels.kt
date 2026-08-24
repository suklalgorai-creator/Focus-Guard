package com.focusguard.app.domain.auth

data class AuthUser(
    val id: String,
    val email: String?,
    val name: String?
)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long,
    val user: AuthUser
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis(), skewMillis: Long = 60_000L): Boolean {
        return expiresAtMillis <= nowMillis + skewMillis
    }
}

data class AuthUiState(
    val isInitializing: Boolean = true,
    val isLoading: Boolean = false,
    val user: AuthUser? = null,
    val hasSkippedLogin: Boolean = false,
    val isConfigured: Boolean = true,
    val errorMessage: String? = null
) {
    val isLoggedIn: Boolean
        get() = user != null

    val shouldShowLogin: Boolean
        get() = !isInitializing && user == null && !hasSkippedLogin
}

data class GoogleIdTokenResult(
    val idToken: String,
    val nonce: String,
    val email: String?,
    val name: String?
)

data class LocalUserSettings(
    val blockedApps: Set<String>,
    val focusSchedule: FocusScheduleSnapshot,
    val streak: Int
)

data class FocusScheduleSnapshot(
    val enabled: Boolean,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val days: Set<Int>,
    val blocks: List<StudyBlockScheduleSnapshot> = emptyList()
)

data class StudyBlockScheduleSnapshot(
    val id: String,
    val title: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val days: Set<Int>,
    val enabled: Boolean
)

data class RemoteUserSettings(
    val blockedApps: Set<String>?,
    val focusSchedule: FocusScheduleSnapshot?,
    val streak: Int?
)
