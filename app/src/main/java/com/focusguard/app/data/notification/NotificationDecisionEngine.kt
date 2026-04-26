package com.focusguard.app.data.notification

import com.focusguard.app.domain.notification.NotificationType
import com.focusguard.app.domain.notification.SmartNotificationDecision
import com.focusguard.app.domain.pyq.PyqAttempt
import com.focusguard.app.domain.pyq.SubjectPerformanceCategory
import com.focusguard.app.domain.pyq.SubjectPerformanceStats

class NotificationDecisionEngine {

    fun decideForPeriodicCheck(
        subjectStats: List<SubjectPerformanceStats>,
        recentAttempts: List<PyqAttempt>,
        lastActiveTimeMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): SmartNotificationDecision? {
        val inactiveMs = nowMs - lastActiveTimeMs
        if (lastActiveTimeMs > 0 && inactiveMs >= INACTIVE_THRESHOLD_MS) {
            return SmartNotificationDecision(
                type = NotificationType.COMEBACK,
                reason = "User inactive for ${inactiveMs / HOUR_MS} hours"
            )
        }

        val wrongStreak = recentAttempts.take(3)
        if (wrongStreak.size == 3 && wrongStreak.all { !it.isCorrect }) {
            return SmartNotificationDecision(
                type = NotificationType.STRUGGLE,
                subject = wrongStreak.groupBy { it.subject }.maxByOrNull { it.value.size }?.key,
                reason = "Three recent incorrect attempts"
            )
        }

        val weakest = subjectStats.firstOrNull { it.category == SubjectPerformanceCategory.WEAK }
        if (weakest != null) {
            return SmartNotificationDecision(
                type = NotificationType.STRUGGLE,
                subject = weakest.subject,
                reason = "Rolling accuracy below weak threshold"
            )
        }

        val improvedSubject = detectImprovement(recentAttempts)
        if (improvedSubject != null) {
            return SmartNotificationDecision(
                type = NotificationType.IMPROVEMENT,
                subject = improvedSubject,
                reason = "Recent accuracy improved compared with previous attempts"
            )
        }

        val correctStreak = recentAttempts.take(3)
        if (correctStreak.size == 3 && correctStreak.all { it.isCorrect }) {
            return SmartNotificationDecision(
                type = NotificationType.PRAISE,
                subject = correctStreak.groupBy { it.subject }.maxByOrNull { it.value.size }?.key,
                reason = "Three recent correct attempts"
            )
        }

        val strong = subjectStats.firstOrNull { it.category == SubjectPerformanceCategory.STRONG }
        if (strong != null) {
            return SmartNotificationDecision(
                type = NotificationType.PRAISE,
                subject = strong.subject,
                reason = "Strong rolling accuracy"
            )
        }

        return null
    }

    private fun detectImprovement(recentAttempts: List<PyqAttempt>): String? {
        return recentAttempts
            .groupBy { it.subject }
            .mapNotNull { (subject, attempts) ->
                if (attempts.size < 8) return@mapNotNull null
                val latest = attempts.take(4)
                val previous = attempts.drop(4).take(4)
                if (previous.size < 4) return@mapNotNull null

                val latestAccuracy = latest.count { it.isCorrect }.toDouble() / latest.size
                val previousAccuracy = previous.count { it.isCorrect }.toDouble() / previous.size
                if (latestAccuracy - previousAccuracy >= IMPROVEMENT_DELTA) subject else null
            }
            .firstOrNull()
    }

    companion object {
        private const val HOUR_MS = 60L * 60L * 1000L
        private const val INACTIVE_THRESHOLD_MS = 24L * HOUR_MS
        private const val IMPROVEMENT_DELTA = 0.25
    }
}
