package com.focusguard.app.persistence

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.focusguard.app.domain.pyq.PyqAttempt

/**
 * Room entity for question-level PYQ attempts.
 */
@Entity(
    tableName = "pyq_attempts",
    indices = [
        Index(value = ["subject"]),
        Index(value = ["subject", "timestamp"]),
        Index(value = ["questionId"]),
        Index(value = ["timestamp"])
    ]
)
data class PyqAttemptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val questionId: Int,
    val subject: String,
    val isCorrect: Boolean,
    val selectedOption: String,
    val correctOption: String,
    val timeTakenMs: Long,
    val timestamp: Long,
    val blockedPackage: String
) {
    fun toDomain(): PyqAttempt {
        return PyqAttempt(
            id = id,
            questionId = questionId,
            subject = subject,
            isCorrect = isCorrect,
            selectedOption = selectedOption,
            correctOption = correctOption,
            timeTakenMs = timeTakenMs,
            timestamp = timestamp,
            blockedPackage = blockedPackage
        )
    }

    companion object {
        fun fromDomain(attempt: PyqAttempt): PyqAttemptEntity {
            return PyqAttemptEntity(
                id = attempt.id,
                questionId = attempt.questionId,
                subject = attempt.subject,
                isCorrect = attempt.isCorrect,
                selectedOption = attempt.selectedOption,
                correctOption = attempt.correctOption,
                timeTakenMs = attempt.timeTakenMs,
                timestamp = attempt.timestamp,
                blockedPackage = attempt.blockedPackage
            )
        }
    }
}
