package com.focusguard.app.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.domain.profile.UserProfile
import com.focusguard.app.presentation.profile.ProfileViewModel
import com.focusguard.app.ui.theme.FrictionColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ExamOption(
    val id: String,
    val label: String,
    val subjects: List<String>
)

private val examOptions = listOf(
    ExamOption("neet", "NEET", listOf("Physics", "Chemistry", "Biology")),
    ExamOption("jee", "JEE", listOf("Physics", "Chemistry", "Mathematics")),
    ExamOption("upsc", "UPSC", listOf("Polity", "History", "Geography", "Economy"))
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    profileViewModel: ProfileViewModel,
    onComplete: () -> Unit
) {
    val today = remember { startOfTodayMillis() }
    var step by remember { mutableIntStateOf(0) }
    var selectedExam by remember { mutableStateOf("neet") }
    var targetDate by remember { mutableLongStateOf(0L) }
    val selectedSubjects = remember { mutableStateListOf<String>() }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }

    LaunchedEffect(selectedExam) {
        selectedSubjects.clear()
        selectedSubjects.addAll(
            examOptions.first { it.id == selectedExam }.subjects
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetDate.takeIf { it > 0L } ?: today + DAY_MS
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = datePickerState.selectedDateMillis ?: 0L
                        if (picked <= today) {
                            dateError = true
                        } else {
                            targetDate = picked
                            dateError = false
                            showDatePicker = false
                        }
                    }
                ) {
                    Text("Select", color = FrictionColors.Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = FrictionColors.TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FrictionColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Focus Guard",
                        color = FrictionColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            profileViewModel.skipOnboarding(onComplete)
                        }
                    ) {
                        Text("Skip", color = FrictionColors.TextSecondary)
                    }
                }
                StepIndicator(currentStep = step)
            }

            item {
                when (step) {
                    0 -> WelcomeExamStep(
                        selectedExam = selectedExam,
                        onExamSelected = { selectedExam = it }
                    )
                    1 -> TargetDateStep(
                        targetDate = targetDate,
                        dateError = dateError,
                        onPickDate = { showDatePicker = true }
                    )
                    2 -> SubjectPreferenceStep(
                        subjects = examOptions.first { it.id == selectedExam }.subjects,
                        selectedSubjects = selectedSubjects,
                        onToggleSubject = { subject ->
                            if (subject in selectedSubjects) {
                                selectedSubjects.remove(subject)
                            } else {
                                selectedSubjects.add(subject)
                            }
                        }
                    )
                    else -> PermissionIntroStep()
                }
            }

            item {
                Button(
                    onClick = {
                        when {
                            step == 1 && targetDate <= today -> dateError = true
                            step < LAST_STEP -> step += 1
                            else -> profileViewModel.saveProfile(
                                exam = selectedExam,
                                targetDate = targetDate,
                                preferredSubjects = selectedSubjects.toList(),
                                onSaved = onComplete
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.Accent,
                        contentColor = FrictionColors.TextOnAccent
                    )
                ) {
                    Text(
                        text = if (step == LAST_STEP) "Continue to permissions" else "Continue",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (step > 0) {
                    TextButton(
                        onClick = { step -= 1 },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Back", color = FrictionColors.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeExamStep(
    selectedExam: String,
    onExamSelected: (String) -> Unit
) {
    OnboardingCard(
        icon = Icons.Outlined.School,
        title = "Choose exam",
        body = "Questions and reminders adapt to this."
    ) {
        examOptions.forEach { option ->
            SelectableRow(
                title = option.label,
                subtitle = option.subjects.joinToString(", "),
                selected = selectedExam == option.id,
                onClick = { onExamSelected(option.id) }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TargetDateStep(
    targetDate: Long,
    dateError: Boolean,
    onPickDate: () -> Unit
) {
    OnboardingCard(
        icon = Icons.Outlined.CalendarMonth,
        title = "Target date",
        body = "Used for countdown and study reminders."
    ) {
        OutlinedButton(
            onClick = onPickDate,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = if (targetDate > 0L) formatDate(targetDate) else "Pick target date",
                color = FrictionColors.TextPrimary
            )
        }
        if (dateError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose a future date.",
                color = FrictionColors.Accent,
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SubjectPreferenceStep(
    subjects: List<String>,
    selectedSubjects: List<String>,
    onToggleSubject: (String) -> Unit
) {
    OnboardingCard(
        icon = Icons.Outlined.School,
        title = "Subjects",
        body = "Optional. Helps pick better questions."
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            subjects.forEach { subject ->
                FilterChip(
                    selected = subject in selectedSubjects,
                    onClick = { onToggleSubject(subject) },
                    label = { Text(subject) }
                )
            }
        }
    }
}

@Composable
private fun PermissionIntroStep() {
    OnboardingCard(
        icon = Icons.Outlined.Layers,
        title = "Permissions",
        body = "Accessibility blocks apps. Overlay shows challenges."
    ) {
        PermissionPoint(
            icon = Icons.Outlined.Layers,
            title = "Overlay",
            body = "Shows the block screen."
        )
        Spacer(modifier = Modifier.height(12.dp))
        PermissionPoint(
            icon = Icons.Outlined.Accessibility,
            title = "Accessibility",
            body = "Detects blocked apps."
        )
    }
}

@Composable
private fun OnboardingCard(
    icon: ImageVector,
    title: String,
    body: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = FrictionColors.CardBackground)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, contentDescription = null, tint = FrictionColors.Accent)
            Text(
                text = title,
                color = FrictionColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp
            )
            Text(
                text = body,
                color = FrictionColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                color = if (selected) FrictionColors.AccentSoft else FrictionColors.SurfaceElevated,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                color = if (selected) FrictionColors.Accent else FrictionColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = FrictionColors.TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun PermissionPoint(
    icon: ImageVector,
    title: String,
    body: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = FrictionColors.Warning,
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = FrictionColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            Text(
                text = body,
                color = FrictionColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        repeat(LAST_STEP + 1) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(
                        color = if (index <= currentStep) FrictionColors.Accent else FrictionColors.SurfaceElevated,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(timestamp))
}

private fun startOfTodayMillis(): Long {
    val calendar = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

private const val LAST_STEP = 3
private const val DAY_MS = 24L * 60L * 60L * 1000L
