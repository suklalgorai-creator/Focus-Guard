package com.focusguard.app.data.pyq

import com.focusguard.app.data.behavior.BehaviorRepository
import com.focusguard.app.domain.behavior.BehaviorState
import com.focusguard.app.domain.pyq.PyqQuestion
import com.focusguard.app.domain.pyq.PyqQuestionSource
import com.focusguard.app.domain.pyq.PyqSelection
import com.focusguard.app.domain.pyq.PyqSelector
import com.focusguard.app.domain.pyq.PyqSelectorConfig
import com.focusguard.app.persistence.PyqAttemptDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class PyqSelectorRepository(
    private val attemptDao: PyqAttemptDao,
    private val pyqRepository: PyqRepository,
    private val behaviorRepository: BehaviorRepository,
    private val questionSource: PyqQuestionSource = JsonPyqQuestionSource()
) {

    suspend fun getNextQuestion(
        subjectHint: String? = null,
        config: PyqSelectorConfig = PyqSelectorConfig()
    ): PyqQuestion? {
        return getNextQuestionSelection(subjectHint, config).question
    }

    suspend fun getNextQuestionSelection(
        subjectHint: String? = null,
        config: PyqSelectorConfig = PyqSelectorConfig()
    ): PyqSelection {
        return selectWithBehavior(
            behaviorState = behaviorRepository.getBehaviorState(),
            subjectHint = subjectHint,
            config = config
        )
    }

    suspend fun getWeakSubjectQuestions(
        limit: Int = 30
    ): List<PyqQuestion> {
        val weakSubjects = behaviorRepository.getBehaviorState()
            .weakSubjects
            .map { it.lowercase() }
            .toSet()

        if (weakSubjects.isEmpty()) return emptyList()

        val recentIds = attemptDao.getRecentlyAttemptedQuestionIds(limit = 10).toSet()
        return questionSource.getAllQuestions()
            .asSequence()
            .filter { it.subject.lowercase() in weakSubjects }
            .filter { it.id !in recentIds }
            .sortedWith(compareBy<PyqQuestion> { it.difficulty }.thenBy { it.id })
            .take(limit)
            .toList()
    }

    suspend fun getRevisionQuestions(
        limit: Int = 20,
        config: PyqSelectorConfig = PyqSelectorConfig()
    ): List<PyqQuestion> {
        val recentAttempts = attemptDao.getRecentAttempts(
            config.revisionMaxGapAttempts + config.questionCooldownAttempts + limit
        ).map { it.toDomain() }
        val recentIncorrectIds = attemptDao.getRecentIncorrectAttempts(limit = limit * 2)
            .map { it.questionId }
            .toSet()
        val cooldownIds = recentAttempts
            .take(config.questionCooldownAttempts)
            .map { it.questionId }
            .toSet()

        return questionSource.getAllQuestions()
            .filter { it.id in recentIncorrectIds && it.id !in cooldownIds }
            .sortedWith(compareBy<PyqQuestion> { it.subject }.thenBy { it.id })
            .take(limit)
    }

    suspend fun selectWithBehavior(
        behaviorState: BehaviorState,
        subjectHint: String? = null,
        config: PyqSelectorConfig = PyqSelectorConfig()
    ): PyqSelection = coroutineScope {
        val recentLimit = maxOf(
            config.questionCooldownAttempts,
            config.revisionMaxGapAttempts + config.questionCooldownAttempts,
            20
        )

        val recentAttemptsDeferred = async(Dispatchers.IO) {
            attemptDao.getRecentAttempts(recentLimit).map { it.toDomain() }
        }
        val recentIncorrectDeferred = async(Dispatchers.IO) {
            attemptDao.getRecentIncorrectAttempts(40).map { it.toDomain() }
        }
        val attemptedIdsDeferred = async(Dispatchers.IO) {
            attemptDao.getDistinctAttemptedQuestionIds().toSet()
        }
        val subjectStatsDeferred = async(Dispatchers.IO) {
            pyqRepository.getSubjectPerformance()
        }
        val questions = questionSource.getAllQuestions()

        withContext(Dispatchers.Default) {
            PyqSelector.selectQuestion(
                questions = questions,
                subjectStats = subjectStatsDeferred.await(),
                recentAttempts = recentAttemptsDeferred.await(),
                recentIncorrectAttempts = recentIncorrectDeferred.await(),
                attemptedQuestionIds = attemptedIdsDeferred.await(),
                behaviorState = behaviorState,
                subjectHint = subjectHint,
                config = config
            )
        }
    }
}
