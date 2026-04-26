package com.focusguard.app.friction.tasks

/**
 * Cognitive task that pulls questions from the JSON-backed QuestionRepository.
 *
 * Works for ANY exam (NEET, JEE, etc.) — just switch the exam in FocusGuardPrefs.
 * The question, options, and explanation are all loaded dynamically.
 */
class ExamQuestionTask(override val difficulty: Int) : CognitiveTask {

    override fun generate(): TaskChallenge {
        val question = QuestionRepository.getRandomQuestion(
            subject = selectSubjectByDifficulty()
        ) ?: return fallbackChallenge()

        // Format options for display
        val formattedQuestion = buildString {
            append("${QuestionRepository.getExamName()} PYQ (${question.year})\n\n")
            append(question.question)
            append("\n\n")
            question.options.entries.sortedBy { it.key }.forEach { (key, value) ->
                append("$key) $value\n")
            }
        }

        return TaskChallenge(
            question = formattedQuestion,
            answer = question.answer,
            taskType = TaskType.EXAM_QUESTION,
            hint = "Type A, B, C, or D",
            explanation = question.explanation
        )
    }

    /**
     * At higher difficulty, serve harder subjects.
     * null = any subject (random pool).
     */
    private fun selectSubjectByDifficulty(): String? {
        return when (difficulty.coerceIn(1, 4)) {
            1 -> "Biology"    // Easiest — mostly recall
            2 -> "Chemistry"  // Medium — needs conceptual thinking
            3 -> "Physics"    // Hard — needs calculation
            else -> null      // Level 4 — random mix from all subjects
        }
    }

    /**
     * Fallback in case QuestionRepository has no questions loaded.
     */
    private fun fallbackChallenge(): TaskChallenge {
        return TaskChallenge(
            question = "ERROR: No questions loaded.\nClose the app and reopen to reload.",
            answer = "X",
            taskType = TaskType.EXAM_QUESTION,
            hint = "No questions available",
            explanation = "The question database failed to load. Please restart FocusGuard."
        )
    }
}
