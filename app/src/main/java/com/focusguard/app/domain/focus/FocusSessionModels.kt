package com.focusguard.app.domain.focus

enum class FocusMode(
    val label: String,
    val durationMinutes: Int,
    val xpPerMinute: Int
) {
    POMODORO("Pomodoro", 25, 4),
    DEEP_FOCUS("Deep Focus", 60, 6)
}

data class FocusBadge(
    val id: String,
    val label: String
)

data class FocusSessionState(
    val mode: FocusMode = FocusMode.POMODORO,
    val isActive: Boolean = false,
    val durationMillis: Long = FocusMode.POMODORO.durationMinutes * 60_000L,
    val remainingMillis: Long = FocusMode.POMODORO.durationMinutes * 60_000L,
    val sessionXp: Int = 0,
    val totalXp: Int = 0,
    val streakDays: Int = 0,
    val badges: List<FocusBadge> = emptyList(),
    val isAccountabilityLockActive: Boolean = false
) {
    val elapsedMillis: Long
        get() = (durationMillis - remainingMillis).coerceAtLeast(0L)

    val progress: Float
        get() = if (durationMillis <= 0L) 0f else (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
}
