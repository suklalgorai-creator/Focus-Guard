package com.focusguard.app.domain.profile

data class UserProfile(
    val deviceId: String = "",
    val exam: String = "neet",
    val targetDate: Long = 0L,
    val preferredSubjects: List<String> = defaultSubjectsForExam("neet"),
    val isOnboardingComplete: Boolean = false,
    val createdAt: Long = 0L
) {
    val normalizedExam: String
        get() = exam.lowercase()

    companion object {
        fun defaultSubjectsForExam(exam: String): List<String> {
            return when (exam.lowercase()) {
                "jee" -> listOf("Physics", "Chemistry", "Mathematics")
                "upsc" -> listOf("Polity", "History", "Geography", "Economy")
                else -> listOf("Physics", "Chemistry", "Biology")
            }
        }
    }
}
