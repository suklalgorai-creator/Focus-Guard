package com.focusguard.app.friction.tasks

import android.util.Log
import com.focusguard.app.FocusGuardApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object OverlayPyqAttemptLogger {
    private const val TAG = "OverlayPyqLogger"

    fun logAnswer(
        scope: CoroutineScope,
        challenge: TaskChallenge,
        selectedOption: String,
        blockedPackage: String,
        sourceTitle: String? = null
    ) {
        val questionId = challenge.questionId ?: return
        val subject = challenge.subject?.takeIf { it.isNotBlank() } ?: return
        val normalizedSelection = selectedOption.trim().uppercase()
        val normalizedCorrect = challenge.answer.trim().uppercase()
        val timeTakenMs = (System.currentTimeMillis() - challenge.startedAtMs).coerceAtLeast(0L)
        val isCorrect = challenge.checkAnswer(normalizedSelection)
        FocusGuardApp.instance.prefs.recordDistractionRecovery(
            sourceKey = blockedPackage,
            sourceTitle = sourceTitle,
            pyqSubject = subject,
            pyqWasCorrect = isCorrect
        )

        scope.launch(Dispatchers.IO) {
            runCatching {
                FocusGuardApp.instance.pyqRepository.logAttempt(
                    questionId = questionId,
                    subject = subject,
                    isCorrect = isCorrect,
                    selectedOption = normalizedSelection,
                    correctOption = normalizedCorrect,
                    timeTakenMs = timeTakenMs,
                    blockedPackage = blockedPackage
                )
            }.onFailure { error ->
                Log.e(TAG, "Failed to log overlay PYQ attempt: ${error.message}", error)
            }
        }
    }
}
