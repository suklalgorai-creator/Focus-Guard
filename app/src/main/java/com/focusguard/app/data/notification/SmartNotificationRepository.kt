package com.focusguard.app.data.notification

import android.content.Context
import com.focusguard.app.data.behavior.BehaviorRepository
import com.focusguard.app.data.pyq.PyqRepository
import com.focusguard.app.domain.behavior.RiskLevel
import com.focusguard.app.domain.behavior.UserType
import com.focusguard.app.domain.notification.NotificationType
import com.focusguard.app.domain.notification.SmartNotificationDecision
import com.focusguard.app.notification.FocusNotificationHelper

class SmartNotificationRepository(
    private val context: Context,
    private val pyqRepository: PyqRepository,
    private val behaviorRepository: BehaviorRepository? = null,
    private val decisionEngine: NotificationDecisionEngine = NotificationDecisionEngine(),
    private val messageProvider: SmartMessageProvider = SmartMessageProvider(),
    private val stateStore: SmartNotificationStateStore = SmartNotificationStateStore(context),
    private val notificationHelper: FocusNotificationHelper = FocusNotificationHelper(context)
) {

    suspend fun runPeriodicCheck(nowMs: Long = System.currentTimeMillis()): Boolean {
        val recentAttempts = pyqRepository.getRecentAttempts(limit = RECENT_ATTEMPT_LIMIT)
        val subjectStats = pyqRepository.getSubjectPerformance()
        val lastActiveTimeMs = recentAttempts.firstOrNull()?.timestamp ?: 0L

        val baseDecision = decisionEngine.decideForPeriodicCheck(
            subjectStats = subjectStats,
            recentAttempts = recentAttempts,
            lastActiveTimeMs = lastActiveTimeMs,
            nowMs = nowMs
        )
        val decision = if (baseDecision != null) {
            personalizeDecision(baseDecision)
        } else {
            comebackDecisionFromBehavior() ?: return false
        }

        val message = messageProvider.generate(
            type = decision.type,
            subject = decision.subject,
            avoidMessage = stateStore.getLastMessage()
        )

        if (!stateStore.canSend(decision.type, message, nowMs)) return false

        val sent = notificationHelper.showSmartNotification(decision.type, message)
        if (sent) {
            stateStore.recordSent(decision.type, message, nowMs)
        }
        return sent
    }

    private suspend fun personalizeDecision(
        decision: SmartNotificationDecision
    ): SmartNotificationDecision {
        val behaviorState = behaviorRepository?.getBehaviorState() ?: return decision
        return when {
            behaviorState.riskLevel == RiskLevel.HIGH -> decision.copy(
                type = NotificationType.COMEBACK,
                subject = behaviorState.weakSubjects.firstOrNull() ?: decision.subject,
                reason = "${decision.reason}; high behavior risk"
            )
            behaviorState.userType == UserType.CONSISTENT &&
                decision.type == NotificationType.STRUGGLE -> decision.copy(
                    subject = behaviorState.weakSubjects.firstOrNull() ?: decision.subject,
                    reason = "${decision.reason}; consistent user needs targeted correction"
                )
            else -> decision.copy(
                subject = decision.subject ?: behaviorState.weakSubjects.firstOrNull()
            )
        }
    }

    private suspend fun comebackDecisionFromBehavior(): SmartNotificationDecision? {
        val behaviorState = behaviorRepository?.getBehaviorState() ?: return null
        if (behaviorState.riskLevel != RiskLevel.HIGH) return null
        return SmartNotificationDecision(
            type = NotificationType.COMEBACK,
            subject = behaviorState.weakSubjects.firstOrNull(),
            reason = "High behavior risk from personalization engine"
        )
    }

    companion object {
        private const val RECENT_ATTEMPT_LIMIT = 40
    }
}
