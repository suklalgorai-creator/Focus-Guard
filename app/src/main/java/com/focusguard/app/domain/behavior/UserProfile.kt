package com.focusguard.app.domain.behavior

data class UserProfile(
    val exam: String,
    val targetDate: Long,
    val preferredSubjects: List<String>,
    val createdAt: Long
)
