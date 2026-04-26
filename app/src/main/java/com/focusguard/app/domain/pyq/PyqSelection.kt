package com.focusguard.app.domain.pyq

enum class PyqSelectionReason {
    WEAK_SUBJECT,
    REVISION_LOOP,
    RECENTLY_INCORRECT_TOPIC,
    UNATTEMPTED,
    FALLBACK_RANDOM,
    NO_QUESTIONS
}

data class PyqSelection(
    val question: PyqQuestion?,
    val reason: PyqSelectionReason,
    val candidateCount: Int = 0,
    val score: Int = 0
)
