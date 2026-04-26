package com.focusguard.app.data.pyq

import com.focusguard.app.domain.pyq.AttemptConfidenceSignal
import com.focusguard.app.domain.pyq.AttemptInsight
import com.focusguard.app.domain.pyq.PyqAttempt
import com.focusguard.app.domain.pyq.PyqQuestion
import com.focusguard.app.domain.pyq.PyqQuestionSource
import com.focusguard.app.domain.pyq.PyqSelection
import com.focusguard.app.domain.pyq.PyqSelector
import com.focusguard.app.domain.pyq.PyqSelectorConfig
import com.focusguard.app.domain.pyq.SubjectPerformanceCategory
import com.focusguard.app.domain.pyq.SubjectPerformanceStats
import com.focusguard.app.persistence.PyqAttemptDao
import com.focusguard.app.persistence.PyqAttemptEntity

class PyqRepository(
    private val attemptDao: PyqAttemptDao,
    private val questionSource: PyqQuestionSource = JsonPyqQuestionSource()
) {

    suspend fun logAttempt(attempt: PyqAttempt): Long {
        return attemptDao.insertAttempt(PyqAttemptEntity.fromDomain(attempt))
    }

    suspend fun logAttempt(
        questionId: Int,
        subject: String,
        isCorrect: Boolean,
        selectedOption: String,
        correctOption: String,
        timeTakenMs: Long,
        blockedPackage: String,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        return logAttempt(
            PyqAttempt(
                questionId = questionId,
                subject = subject,
                isCorrect = isCorrect,
                selectedOption = selectedOption,
                correctOption = correctOption,
                timeTakenMs = timeTakenMs,
                timestamp = timestamp,
                blockedPackage = blockedPackage
            )
        )
    }

    suspend fun getSubjectPerformance(
        rollingWindow: Int = DEFAULT_ROLLING_WINDOW,
        minAttempts: Int = DEFAULT_MIN_ATTEMPTS
    ): List<SubjectPerformanceStats> {
        return attemptDao.getAttemptedSubjects().mapNotNull { subject ->
            val attempts = attemptDao.getLastAttemptsForSubject(subject, rollingWindow)
            if (attempts.size < minAttempts) return@mapNotNull null

            val correct = attempts.count { it.isCorrect }
            val accuracy = (correct.toDouble() / attempts.size.toDouble()) * 100.0

            SubjectPerformanceStats(
                subject = subject,
                totalAttempts = attempts.size,
                correctAttempts = correct,
                accuracyPercent = accuracy,
                category = categorizeAccuracy(accuracy)
            )
        }.sortedBy { it.accuracyPercent }
    }

    suspend fun getWeakSubjects(
        rollingWindow: Int = DEFAULT_ROLLING_WINDOW,
        minAttempts: Int = DEFAULT_MIN_ATTEMPTS
    ): List<SubjectPerformanceStats> {
        return getSubjectPerformance(rollingWindow, minAttempts)
            .filter { it.category == SubjectPerformanceCategory.WEAK }
    }

    suspend fun getRecentAttempts(limit: Int = 50): List<PyqAttempt> {
        return attemptDao.getRecentAttempts(limit).map { it.toDomain() }
    }

    fun getLocalFallbackQuestion(subjectHint: String? = null): PyqQuestion? {
        val questions = runCatching { questionSource.getAllQuestions() }.getOrDefault(emptyList())
        if (questions.isEmpty()) return null

        return subjectHint
            ?.let { hint -> questions.firstOrNull { it.subject.equals(hint, ignoreCase = true) } }
            ?: questions.minWithOrNull(compareBy<PyqQuestion> { it.difficulty }.thenBy { it.id })
    }

    suspend fun getNextQuestion(
        subjectHint: String? = null,
        rollingWindow: Int = DEFAULT_ROLLING_WINDOW,
        minAttempts: Int = DEFAULT_MIN_ATTEMPTS,
        cooldownAttemptCount: Int = DEFAULT_COOLDOWN_ATTEMPTS
    ): PyqQuestion? {
        return getNextQuestionSelection(
            subjectHint = subjectHint,
            rollingWindow = rollingWindow,
            minAttempts = minAttempts,
            cooldownAttemptCount = cooldownAttemptCount
        ).question
    }

    suspend fun getNextQuestionSelection(
        subjectHint: String? = null,
        rollingWindow: Int = DEFAULT_ROLLING_WINDOW,
        minAttempts: Int = DEFAULT_MIN_ATTEMPTS,
        cooldownAttemptCount: Int = DEFAULT_COOLDOWN_ATTEMPTS
    ): PyqSelection {
        val config = PyqSelectorConfig(questionCooldownAttempts = cooldownAttemptCount)
        val questions = questionSource.getAllQuestions()
        val subjectStats = getSubjectPerformance(rollingWindow, minAttempts)
        val recentAttempts = attemptDao.getRecentAttempts(maxOf(cooldownAttemptCount, 20))
            .map { it.toDomain() }
        val recentIncorrectAttempts = attemptDao.getRecentIncorrectAttempts(rollingWindow)
            .map { it.toDomain() }
        val attemptedQuestionIds = attemptDao.getDistinctAttemptedQuestionIds().toSet()

        return PyqSelector.selectQuestion(
            questions = questions,
            subjectStats = subjectStats,
            recentAttempts = recentAttempts,
            recentIncorrectAttempts = recentIncorrectAttempts,
            attemptedQuestionIds = attemptedQuestionIds,
            subjectHint = subjectHint,
            config = config
        )
    }

    fun calculateAttemptInsight(
        isCorrect: Boolean,
        timeTakenMs: Long,
        slowCorrectThresholdMs: Long = 45_000,
        fastWrongThresholdMs: Long = 4_000
    ): AttemptInsight {
        return when {
            isCorrect && timeTakenMs >= slowCorrectThresholdMs -> AttemptInsight(
                confidenceScore = 65,
                signal = AttemptConfidenceSignal.SLOW_CORRECT_LOW_CONFIDENCE
            )
            !isCorrect && timeTakenMs <= fastWrongThresholdMs -> AttemptInsight(
                confidenceScore = 20,
                signal = AttemptConfidenceSignal.FAST_WRONG_GUESSING
            )
            isCorrect -> AttemptInsight(
                confidenceScore = 85,
                signal = AttemptConfidenceSignal.NORMAL
            )
            else -> AttemptInsight(
                confidenceScore = 45,
                signal = AttemptConfidenceSignal.NORMAL
            )
        }
    }

    private fun categorizeAccuracy(accuracyPercent: Double): SubjectPerformanceCategory {
        return when {
            accuracyPercent > 75.0 -> SubjectPerformanceCategory.STRONG
            accuracyPercent >= 60.0 -> SubjectPerformanceCategory.MODERATE
            else -> SubjectPerformanceCategory.WEAK
        }
    }

    companion object {
        const val DEFAULT_ROLLING_WINDOW = 20
        const val DEFAULT_MIN_ATTEMPTS = 5
        const val DEFAULT_COOLDOWN_ATTEMPTS = 10
    }
}
