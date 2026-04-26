package com.focusguard.app.domain.behavior

data class BehaviorState(
    val weakSubjects: List<String>,
    val strongSubjects: List<String>,
    val riskLevel: RiskLevel,
    val userType: UserType,
    val dailyGoal: Int
)

enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

enum class UserType {
    CONSISTENT,
    IRREGULAR,
    STRUGGLING
}
