package com.focusguard.app.domain.notification

data class SmartNotificationDecision(
    val type: NotificationType,
    val subject: String? = null,
    val reason: String
)
