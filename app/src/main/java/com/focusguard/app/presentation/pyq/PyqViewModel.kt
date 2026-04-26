package com.focusguard.app.presentation.pyq

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.focusguard.app.data.analytics.AnalyticsRepository
import com.focusguard.app.data.behavior.BehaviorRepository
import com.focusguard.app.data.pyq.PyqRepository
import com.focusguard.app.data.pyq.PyqSelectorRepository
import com.focusguard.app.domain.pyq.PyqAttempt
import com.focusguard.app.domain.pyq.PyqQuestion
import com.focusguard.app.domain.pyq.PyqSelectionReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PyqViewModel(
    private val pyqRepository: PyqRepository,
    private val selectorRepository: PyqSelectorRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val behaviorRepository: BehaviorRepository,
    private val clock: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(PyqUiState())
    val uiState: StateFlow<PyqUiState> = _uiState.asStateFlow()

    private var questionStartedAtMs: Long = 0L

    init {
        refreshProgress()
    }

    fun loadNextQuestion(subjectHint: String? = null, force: Boolean = false) {
        if (!force && _uiState.value.question != null) return
        viewModelScope.launch {
            _uiState.value = PyqUiState(isLoading = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    selectorRepository.getNextQuestionSelection(subjectHint)
                }
            }.onSuccess { selection ->
                questionStartedAtMs = clock()
                _uiState.value = PyqUiState(
                    question = selection.question,
                    selectionReason = selection.reason,
                    isLoading = false,
                    errorMessage = if (selection.question == null) {
                        "No PYQ available for this exam yet."
                    } else {
                        null
                    }
                )
            }.onFailure { error ->
                val fallbackQuestion = withContext(Dispatchers.Default) {
                    pyqRepository.getLocalFallbackQuestion(subjectHint)
                }
                questionStartedAtMs = clock()
                _uiState.value = PyqUiState(
                    question = fallbackQuestion,
                    selectionReason = if (fallbackQuestion == null) {
                        PyqSelectionReason.NO_QUESTIONS
                    } else {
                        PyqSelectionReason.FALLBACK_RANDOM
                    },
                    isLoading = false,
                    errorMessage = if (fallbackQuestion == null) {
                        "PYQ engine failed to load questions. ${error.safeMessage()}"
                    } else {
                        "Adaptive selector had an issue, so a safe PYQ was loaded."
                    }
                )
            }
        }
    }

    fun selectOption(optionKey: String) {
        _uiState.update { state ->
            if (state.isSubmitted) state else state.copy(selectedOption = optionKey)
        }
    }

    fun submitAnswer(blockedPackage: String = MANUAL_PYQ_SOURCE) {
        val state = _uiState.value
        val question = state.question ?: return
        val selectedOption = state.selectedOption ?: return
        if (state.isSubmitted || state.isSubmitting) return

        viewModelScope.launch {
            val isCorrect = selectedOption.equals(question.correctAnswer, ignoreCase = true)
            val timeTakenMs = (clock() - questionStartedAtMs).coerceAtLeast(0L)
            _uiState.update {
                it.copy(
                    isSubmitting = true,
                    isCorrect = isCorrect,
                    timeTakenMs = timeTakenMs
                )
            }

            val submissionSnapshot = withContext(Dispatchers.IO) {
                val attemptLogged = runCatching {
                    pyqRepository.logAttempt(
                        PyqAttempt(
                            questionId = question.id,
                            subject = question.subject,
                            isCorrect = isCorrect,
                            selectedOption = selectedOption,
                            correctOption = question.correctAnswer,
                            timeTakenMs = timeTakenMs,
                            blockedPackage = blockedPackage
                        )
                    )
                }.isSuccess

                SubmissionSnapshot(
                    attemptLogged = attemptLogged,
                    todayAttempts = runCatching { analyticsRepository.getTodayAttempts() }.getOrDefault(0),
                    streak = runCatching { analyticsRepository.getStreak() }.getOrDefault(0),
                    dailyGoal = runCatching { behaviorRepository.getDailyGoal() }.getOrDefault(0)
                )
            }

            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    isSubmitted = true,
                    isCorrect = isCorrect,
                    explanation = question.explanation,
                    timeTakenMs = timeTakenMs,
                    todayAttempts = submissionSnapshot.todayAttempts,
                    dailyGoal = submissionSnapshot.dailyGoal,
                    streak = submissionSnapshot.streak,
                    rewardMessage = buildRewardMessage(
                        isCorrect = isCorrect,
                        todayAttempts = submissionSnapshot.todayAttempts,
                        dailyGoal = submissionSnapshot.dailyGoal,
                        streak = submissionSnapshot.streak
                    ),
                    errorMessage = if (submissionSnapshot.attemptLogged) {
                        null
                    } else {
                        "Answer checked, but attempt history could not be saved."
                    }
                )
            }
        }
    }

    private fun refreshProgress() {
        viewModelScope.launch {
            val progressSnapshot = withContext(Dispatchers.IO) {
                ProgressSnapshot(
                    todayAttempts = runCatching { analyticsRepository.getTodayAttempts() }.getOrDefault(0),
                    dailyGoal = runCatching { behaviorRepository.getDailyGoal() }.getOrDefault(0),
                    streak = runCatching { analyticsRepository.getStreak() }.getOrDefault(0)
                )
            }
            _uiState.update {
                it.copy(
                    todayAttempts = progressSnapshot.todayAttempts,
                    dailyGoal = progressSnapshot.dailyGoal,
                    streak = progressSnapshot.streak
                )
            }
        }
    }

    private fun buildRewardMessage(
        isCorrect: Boolean,
        todayAttempts: Int,
        dailyGoal: Int,
        streak: Int
    ): String {
        return when {
            isCorrect && dailyGoal > 0 && todayAttempts >= dailyGoal ->
                "Daily goal done. Ab momentum waste mat karo."
            isCorrect && streak >= 3 ->
                "Good. Streak alive hai, pressure bana ke rakho."
            isCorrect ->
                "Good. 1 done. Next one simple rakho."
            !isCorrect && dailyGoal > 0 ->
                "Mistake logged. Goal abhi bhi reachable hai."
            else ->
                "Wrong answer useful hota hai, agar abhi fix karo."
        }
    }

    companion object {
        private const val MANUAL_PYQ_SOURCE = "manual_pyq_screen"

        fun factory(
            pyqRepository: PyqRepository,
            selectorRepository: PyqSelectorRepository,
            analyticsRepository: AnalyticsRepository,
            behaviorRepository: BehaviorRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PyqViewModel(
                        pyqRepository = pyqRepository,
                        selectorRepository = selectorRepository,
                        analyticsRepository = analyticsRepository,
                        behaviorRepository = behaviorRepository
                    ) as T
                }
            }
        }
    }
}

private fun Throwable.safeMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: "Please reopen the screen."
}

private data class SubmissionSnapshot(
    val attemptLogged: Boolean,
    val todayAttempts: Int,
    val dailyGoal: Int,
    val streak: Int
)

private data class ProgressSnapshot(
    val todayAttempts: Int,
    val dailyGoal: Int,
    val streak: Int
)

data class PyqUiState(
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val question: PyqQuestion? = null,
    val selectionReason: PyqSelectionReason = PyqSelectionReason.NO_QUESTIONS,
    val selectedOption: String? = null,
    val isSubmitted: Boolean = false,
    val isCorrect: Boolean? = null,
    val explanation: String? = null,
    val timeTakenMs: Long = 0L,
    val todayAttempts: Int = 0,
    val dailyGoal: Int = 0,
    val streak: Int = 0,
    val rewardMessage: String? = null,
    val errorMessage: String? = null
)
