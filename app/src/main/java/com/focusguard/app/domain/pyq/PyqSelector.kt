package com.focusguard.app.domain.pyq

import com.focusguard.app.domain.behavior.BehaviorState
import com.focusguard.app.domain.behavior.RiskLevel
import com.focusguard.app.domain.behavior.UserType

/**
 * Priority selector for PYQs.
 *
 * Randomness is not the primary decision maker here. The selector first chooses
 * the highest-value priority bucket, then ranks candidates deterministically.
 */
object PyqSelector {

    fun selectQuestion(
        questions: List<PyqQuestion>,
        subjectStats: List<SubjectPerformanceStats>,
        recentAttempts: List<PyqAttempt>,
        recentIncorrectAttempts: List<PyqAttempt>,
        attemptedQuestionIds: Set<Int>,
        behaviorState: BehaviorState? = null,
        subjectHint: String? = null,
        config: PyqSelectorConfig = PyqSelectorConfig()
    ): PyqSelection {
        if (questions.isEmpty()) {
            return PyqSelection(null, PyqSelectionReason.NO_QUESTIONS)
        }

        val basePool = subjectHint
            ?.let { hint -> questions.filter { it.subject.equals(hint, ignoreCase = true) } }
            ?.takeIf { it.isNotEmpty() }
            ?: questions

        val recentQuestionIds = recentAttempts
            .take(config.questionCooldownAttempts)
            .map { it.questionId }
            .toSet()

        val questionById = questions.associateBy { it.id }
        val recentTopics = recentAttempts
            .take(config.topicCooldownAttempts)
            .mapNotNull { questionById[it.questionId]?.topic?.normalizedKey() }
            .toSet()
        val recentSubjects = recentAttempts
            .take(config.subjectDiversityWindow)
            .map { it.subject.normalizedKey() }
            .toSet()

        val cooledPool = basePool
            .filter { it.id !in recentQuestionIds }
            .ifEmpty { basePool }

        val difficultyPool = cooledPool
            .filter { it.difficulty in allowedDifficulties(behaviorState) }
            .ifEmpty { cooledPool }

        val weakSubjects = behaviorState?.weakSubjects
            ?.map { it.normalizedKey() }
            ?.takeIf { it.isNotEmpty() }
            ?: subjectStats
            .filter { it.category == SubjectPerformanceCategory.WEAK }
            .sortedBy { it.accuracyPercent }
            .map { it.subject.lowercase() }

        selectBest(
            candidates = difficultyPool.filter { it.subject.normalizedKey() in weakSubjects },
            reason = PyqSelectionReason.WEAK_SUBJECT,
            attemptedQuestionIds = attemptedQuestionIds,
            recentTopics = recentTopics,
            recentSubjects = recentSubjects,
            behaviorState = behaviorState,
            recentIncorrectAttempts = recentIncorrectAttempts
        )?.let { return it }

        val revisionQuestionIds = revisionEligibleQuestionIds(
            recentAttempts = recentAttempts,
            recentIncorrectAttempts = recentIncorrectAttempts,
            config = config
        )

        selectBest(
            candidates = difficultyPool.filter { it.id in revisionQuestionIds },
            reason = PyqSelectionReason.REVISION_LOOP,
            attemptedQuestionIds = attemptedQuestionIds,
            recentTopics = recentTopics,
            recentSubjects = recentSubjects,
            behaviorState = behaviorState,
            recentIncorrectAttempts = recentIncorrectAttempts
        )?.let { return it }

        val incorrectTopics = recentIncorrectAttempts
            .mapNotNull { attempt -> questionById[attempt.questionId]?.topic?.takeIf { it.isNotBlank() } }
            .map { it.normalizedKey() }
            .toSet()

        val incorrectSubjects = recentIncorrectAttempts
            .map { it.subject.normalizedKey() }
            .toSet()

        selectBest(
            candidates = difficultyPool.filter { question ->
                question.topic?.normalizedKey() in incorrectTopics ||
                    question.subject.normalizedKey() in incorrectSubjects
            },
            reason = PyqSelectionReason.RECENTLY_INCORRECT_TOPIC,
            attemptedQuestionIds = attemptedQuestionIds,
            recentTopics = recentTopics,
            recentSubjects = recentSubjects,
            behaviorState = behaviorState,
            recentIncorrectAttempts = recentIncorrectAttempts
        )?.let { return it }

        selectBest(
            candidates = difficultyPool.filter { it.id !in attemptedQuestionIds },
            reason = PyqSelectionReason.UNATTEMPTED,
            attemptedQuestionIds = attemptedQuestionIds,
            recentTopics = recentTopics,
            recentSubjects = recentSubjects,
            behaviorState = behaviorState,
            recentIncorrectAttempts = recentIncorrectAttempts
        )?.let { return it }

        return selectBest(
            candidates = difficultyPool,
            reason = PyqSelectionReason.FALLBACK_RANDOM,
            attemptedQuestionIds = attemptedQuestionIds,
            recentTopics = recentTopics,
            recentSubjects = recentSubjects,
            behaviorState = behaviorState,
            recentIncorrectAttempts = recentIncorrectAttempts
        ) ?: PyqSelection(null, PyqSelectionReason.NO_QUESTIONS)
    }

    private fun selectBest(
        candidates: List<PyqQuestion>,
        reason: PyqSelectionReason,
        attemptedQuestionIds: Set<Int>,
        recentTopics: Set<String>,
        recentSubjects: Set<String>,
        behaviorState: BehaviorState?,
        recentIncorrectAttempts: List<PyqAttempt>
    ): PyqSelection? {
        if (candidates.isEmpty()) return null

        val incorrectIds = recentIncorrectAttempts.map { it.questionId }.toSet()
        val weakSubjects = behaviorState?.weakSubjects.orEmpty().map { it.normalizedKey() }.toSet()
        val strongSubjects = behaviorState?.strongSubjects.orEmpty().map { it.normalizedKey() }.toSet()

        val ranked = candidates
            .map { question ->
                val score = scoreQuestion(
                    question = question,
                    reason = reason,
                    attemptedQuestionIds = attemptedQuestionIds,
                    recentTopics = recentTopics,
                    recentSubjects = recentSubjects,
                    weakSubjects = weakSubjects,
                    strongSubjects = strongSubjects,
                    incorrectIds = incorrectIds,
                    behaviorState = behaviorState
                )
                question to score
            }
            .sortedWith(
                compareByDescending<Pair<PyqQuestion, Int>> { it.second }
                    .thenBy { it.first.year }
                    .thenBy { it.first.id }
            )

        val (question, score) = ranked.first()
        return PyqSelection(
            question = question,
            reason = reason,
            candidateCount = candidates.size,
            score = score
        )
    }

    private fun scoreQuestion(
        question: PyqQuestion,
        reason: PyqSelectionReason,
        attemptedQuestionIds: Set<Int>,
        recentTopics: Set<String>,
        recentSubjects: Set<String>,
        weakSubjects: Set<String>,
        strongSubjects: Set<String>,
        incorrectIds: Set<Int>,
        behaviorState: BehaviorState?
    ): Int {
        var score = when (reason) {
            PyqSelectionReason.WEAK_SUBJECT -> 1_000
            PyqSelectionReason.REVISION_LOOP -> 900
            PyqSelectionReason.RECENTLY_INCORRECT_TOPIC -> 800
            PyqSelectionReason.UNATTEMPTED -> 700
            PyqSelectionReason.FALLBACK_RANDOM -> 100
            PyqSelectionReason.NO_QUESTIONS -> 0
        }

        val subjectKey = question.subject.normalizedKey()
        val topicKey = question.topic?.normalizedKey()

        if (subjectKey in weakSubjects) score += 120
        if (subjectKey in strongSubjects) score -= 40
        if (question.id in incorrectIds) score += 90
        if (question.id !in attemptedQuestionIds) score += 70
        if (topicKey != null && topicKey in recentTopics) score -= 90
        if (subjectKey in recentSubjects) score -= 35
        if (question.difficulty in allowedDifficulties(behaviorState)) score += 50

        if (behaviorState?.riskLevel == RiskLevel.HIGH && question.difficulty <= 2) {
            score += 25
        }

        return score
    }

    private fun revisionEligibleQuestionIds(
        recentAttempts: List<PyqAttempt>,
        recentIncorrectAttempts: List<PyqAttempt>,
        config: PyqSelectorConfig
    ): Set<Int> {
        val positionByQuestion = recentAttempts
            .mapIndexed { index, attempt -> attempt.questionId to index }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, positions) -> positions.min() }

        return recentIncorrectAttempts
            .map { it.questionId }
            .distinct()
            .filter { questionId ->
                val position = positionByQuestion[questionId] ?: return@filter true
                position in config.revisionMinGapAttempts..config.revisionMaxGapAttempts
            }
            .toSet()
    }

    private fun allowedDifficulties(behaviorState: BehaviorState?): Set<Int> {
        return when (behaviorState?.userType) {
            UserType.CONSISTENT -> setOf(2, 3, 4)
            UserType.STRUGGLING -> setOf(1, 2)
            UserType.IRREGULAR -> setOf(1)
            null -> setOf(1, 2, 3, 4)
        }
    }

    private fun String.normalizedKey(): String {
        return trim().lowercase()
    }
}
