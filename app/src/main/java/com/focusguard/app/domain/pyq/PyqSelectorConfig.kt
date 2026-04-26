package com.focusguard.app.domain.pyq

data class PyqSelectorConfig(
    val questionCooldownAttempts: Int = 10,
    val topicCooldownAttempts: Int = 3,
    val subjectDiversityWindow: Int = 5,
    val revisionMinGapAttempts: Int = 5,
    val revisionMaxGapAttempts: Int = 10
)
