package com.focusguard.app.domain.pyq

/**
 * Domain record for one PYQ answer attempt shown during a blocked app flow.
 */
data class PyqAttempt(
    val id: Long = 0,
    val questionId: Int,
    val subject: String,
    val isCorrect: Boolean,
    val selectedOption: String,
    val correctOption: String,
    val timeTakenMs: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val blockedPackage: String
)
