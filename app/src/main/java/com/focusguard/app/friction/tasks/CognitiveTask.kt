package com.focusguard.app.friction.tasks

/**
 * Base interface for all cognitive tasks.
 */
interface CognitiveTask {
    val difficulty: Int
    fun generate(): TaskChallenge
}

/**
 * A generated challenge with question, expected answer, explanation, and validation.
 */
data class TaskChallenge(
    val question: String,
    val answer: String,
    val taskType: TaskType,
    val hint: String = "",
    val explanation: String? = null, // Solution shown on wrong answer
    val questionId: Int? = null,
    val subject: String? = null,
    val startedAtMs: Long = System.currentTimeMillis()
) {
    fun checkAnswer(userAnswer: String): Boolean {
        return when (taskType) {
            TaskType.MATH -> userAnswer.trim() == answer.trim()
            TaskType.TYPING -> userAnswer == answer // Case-sensitive, exact match
            TaskType.MEMORY -> userAnswer.trim().equals(answer.trim(), ignoreCase = true)
            TaskType.EXAM_QUESTION -> userAnswer.trim().equals(answer.trim(), ignoreCase = true)
        }
    }
}

enum class TaskType {
    MATH,
    TYPING,
    MEMORY,
    EXAM_QUESTION
}
