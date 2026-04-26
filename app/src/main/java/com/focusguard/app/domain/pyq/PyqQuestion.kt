package com.focusguard.app.domain.pyq

/**
 * Domain model for a previous-year exam question.
 *
 * This model is intentionally independent from the current JSON loader so the
 * adaptive engine can later source questions from JSON, Room, or sync without
 * changing the friction pipeline.
 */
data class PyqQuestion(
    val id: Int,
    val exam: String,
    val subject: String,
    val topic: String? = null,
    val difficulty: Int,
    val year: Int,
    val question: String,
    val options: Map<String, String>,
    val correctAnswer: String,
    val explanation: String
)
