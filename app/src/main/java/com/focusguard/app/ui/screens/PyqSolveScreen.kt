package com.focusguard.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.domain.pyq.PyqQuestion
import com.focusguard.app.domain.pyq.PyqSelectionReason
import com.focusguard.app.presentation.pyq.PyqUiState
import com.focusguard.app.presentation.pyq.PyqViewModel
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.StableLinearProgress
import com.focusguard.app.ui.theme.FrictionColors

@Composable
fun PyqSolveScreen(
    viewModel: PyqViewModel,
    blockedPackage: String? = null,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(viewModel) {
        viewModel.loadNextQuestion()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrictionColors.Background)
    ) {
        when {
            state.isLoading -> LoadingState()
            state.question == null -> EmptyState(
                message = state.errorMessage ?: "No question available.",
                onRetry = { viewModel.loadNextQuestion(force = true) },
                onBack = onBack
            )
            else -> QuestionContent(
                state = state,
                blockedPackage = blockedPackage,
                onBack = onBack,
                onSelectOption = { option ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.selectOption(option)
                },
                onSubmit = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.submitAnswer(blockedPackage ?: "pyq_screen")
                },
                onNext = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.loadNextQuestion(force = true)
                }
            )
        }
    }
}

@Composable
private fun QuestionContent(
    state: PyqUiState,
    blockedPackage: String?,
    onBack: () -> Unit,
    onSelectOption: (String) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit
) {
    val question = state.question ?: return

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeaderRow(
                question = question,
                reason = state.selectionReason,
                onBack = onBack
            )
        }

        if (!blockedPackage.isNullOrBlank()) {
            item {
                ContextChip("Distraction blocked. Solve one PYQ first.")
            }
        }

        item {
            ProgressStrip(state)
        }

        item {
            GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 18.dp) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = question.question,
                        color = FrictionColors.TextPrimary,
                        fontSize = 19.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    question.topic?.takeIf { it.isNotBlank() }?.let { topic ->
                        ContextChip(topic)
                    }
                }
            }
        }

        question.options.toSortedMap().forEach { (key, value) ->
            item {
                OptionRow(
                    key = key,
                    value = value,
                    state = state,
                    onClick = { onSelectOption(key) }
                )
            }
        }

        item {
            if (state.isSubmitted) {
                FeedbackCard(state)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.Accent,
                        contentColor = FrictionColors.TextOnAccent
                    )
                ) {
                    Text("Next question", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onSubmit,
                    enabled = state.selectedOption != null && !state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.Accent,
                        contentColor = FrictionColors.TextOnAccent,
                        disabledContainerColor = FrictionColors.SurfaceElevated,
                        disabledContentColor = FrictionColors.TextMuted
                    )
                ) {
                    Text(
                        text = if (state.isSubmitting) "Saving..." else "Submit answer",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    question: PyqQuestion,
    reason: PyqSelectionReason,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = FrictionColors.TextPrimary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${question.exam.uppercase()} PYQ",
                color = FrictionColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = question.subject,
                color = FrictionColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Year ${question.year}",
                color = FrictionColors.TextSecondary,
                fontSize = 12.sp
            )
            Text(
                text = reason.label(),
                color = FrictionColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun OptionRow(
    key: String,
    value: String,
    state: PyqUiState,
    onClick: () -> Unit
) {
    val question = state.question ?: return
    val isSelected = state.selectedOption == key
    val isCorrectAnswer = question.correctAnswer.equals(key, ignoreCase = true)
    val isWrongSelected = state.isSubmitted && isSelected && !isCorrectAnswer
    val color = when {
        state.isSubmitted && isCorrectAnswer -> FrictionColors.Success
        isWrongSelected -> FrictionColors.Error
        isSelected -> FrictionColors.Accent
        else -> FrictionColors.CardBorder
    }
    val background = when {
        state.isSubmitted && isCorrectAnswer -> FrictionColors.SuccessSoft
        isWrongSelected -> FrictionColors.ErrorSoft
        isSelected -> FrictionColors.AccentSoft
        else -> FrictionColors.CardBackground
    }
    val animatedBackground by animateColorAsState(
        targetValue = background,
        label = "optionBackground"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.01f else 1f,
        label = "optionScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(animatedScale)
            .clip(RoundedCornerShape(14.dp))
            .background(animatedBackground)
            .clickable(enabled = !state.isSubmitted, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (color == FrictionColors.CardBorder) 0.08f else 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = key,
                color = if (color == FrictionColors.CardBorder) FrictionColors.TextSecondary else color,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Text(
            text = value,
            color = FrictionColors.TextPrimary,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FeedbackCard(state: PyqUiState) {
    val correct = state.isCorrect == true
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        backgroundColor = if (correct) FrictionColors.SuccessSoft else FrictionColors.ErrorSoft
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = state.rewardMessage ?: if (correct) {
                    "Correct. Good, move."
                } else {
                    "Incorrect. Fix it now."
                },
                color = if (correct) FrictionColors.Success else FrictionColors.Error,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            if (state.dailyGoal > 0) {
                Text(
                    text = "${state.todayAttempts}/${state.dailyGoal} PYQs today",
                    color = FrictionColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "Time: ${formatTime(state.timeTakenMs)}",
                color = FrictionColors.TextSecondary,
                fontSize = 12.sp
            )
            state.explanation?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = FrictionColors.TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

@Composable
private fun ProgressStrip(state: PyqUiState) {
    val goal = state.dailyGoal.coerceAtLeast(1)
    val progress = (state.todayAttempts.toFloat() / goal.toFloat()).coerceIn(0f, 1f)

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Today",
                    color = FrictionColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (state.dailyGoal > 0) {
                        "${state.todayAttempts}/${state.dailyGoal}"
                    } else {
                        "${state.todayAttempts} solved"
                    },
                    color = FrictionColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            StableLinearProgress(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(100.dp)),
                color = FrictionColors.Accent,
                trackColor = FrictionColors.SurfaceElevated
            )
            Text(
                text = progressCaption(state),
                color = FrictionColors.TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun ContextChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(FrictionColors.SurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            color = FrictionColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            cornerRadius = 24.dp,
            backgroundColor = FrictionColors.GlassBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Preparing your focus challenge",
                    color = FrictionColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Solve this PYQ instead of wasting time.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                StableLinearProgress(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(100.dp)),
                    color = FrictionColors.Accent,
                    trackColor = FrictionColors.SurfaceElevated
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            color = FrictionColors.TextPrimary,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Outlined.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Try again")
        }
        TextButton(onClick = onBack) {
            Text("Back", color = FrictionColors.TextSecondary)
        }
    }
}

private fun PyqSelectionReason.label(): String {
    return when (this) {
        PyqSelectionReason.WEAK_SUBJECT -> "weak area"
        PyqSelectionReason.REVISION_LOOP -> "revision"
        PyqSelectionReason.RECENTLY_INCORRECT_TOPIC -> "mistake loop"
        PyqSelectionReason.UNATTEMPTED -> "new"
        PyqSelectionReason.FALLBACK_RANDOM -> "mixed"
        PyqSelectionReason.NO_QUESTIONS -> "empty"
    }
}

private fun formatTime(timeTakenMs: Long): String {
    val seconds = (timeTakenMs / 1000).coerceAtLeast(0)
    return if (seconds < 60) {
        "${seconds}s"
    } else {
        "${seconds / 60}m ${seconds % 60}s"
    }
}

private fun progressCaption(state: PyqUiState): String {
    return when {
        state.dailyGoal <= 0 && state.streak > 0 -> "${state.streak}-day streak active."
        state.dailyGoal <= 0 -> "Solve one clean question. Bas."
        state.todayAttempts >= state.dailyGoal -> "Goal complete. Extra questions are rank insurance."
        state.streak >= 3 -> "${state.streak}-day streak. ${state.dailyGoal - state.todayAttempts} left."
        else -> "${state.dailyGoal - state.todayAttempts} PYQs left for today's goal."
    }
}
