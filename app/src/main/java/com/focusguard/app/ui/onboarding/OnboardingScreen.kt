package com.focusguard.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.presentation.profile.ProfileViewModel
import com.focusguard.app.ui.components.FocusGuardIcon
import com.focusguard.app.ui.components.FocusGuardIconId
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.GradientButton
import com.focusguard.app.ui.components.SecondaryButton
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.theme.GlassTokens
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
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionMonitor = remember { PermissionMonitor(context) }

    val today = remember { startOfTodayMillis() }
    var step by remember { mutableIntStateOf(0) }
    var selectedExam by remember { mutableStateOf("neet") }
    var targetDate by remember { mutableLongStateOf(0L) }
    val selectedSubjects = remember { mutableStateListOf<String>() }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }

    var accessibilityReady by remember { mutableStateOf(permissionMonitor.isAccessibilityEnabled()) }
    var overlayReady by remember { mutableStateOf(permissionMonitor.isOverlayPermitted()) }
    var usageReady by remember { mutableStateOf(permissionMonitor.isUsageStatsPermitted()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityReady = permissionMonitor.isAccessibilityEnabled()
                overlayReady = permissionMonitor.isOverlayPermitted()
                usageReady = permissionMonitor.isUsageStatsPermitted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(selectedExam) {
        selectedSubjects.clear()
        selectedSubjects.addAll(
            examOptions.first { it.id == selectedExam }.subjects
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = targetDate.takeIf { it > 0L } ?: (today + 90L * DAY_MS)
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
                    Text("Select", color = GlassTokens.Accents.FocusSession)
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
            .background(
                Brush.verticalGradient(
                    listOf(
                        FrictionColors.GradientStart,
                        FrictionColors.Background,
                        FrictionColors.GradientEnd
                    )
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "header") {
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
                        onClick = { profileViewModel.skipOnboarding(onComplete) }
                    ) {
                        Text("Skip", color = FrictionColors.TextSecondary)
                    }
                }
                StepIndicator(currentStep = step)
            }

            item(key = "step_content") {
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        fadeIn(tween(GlassTokens.Motion.standardMs)) togetherWith
                            fadeOut(tween(GlassTokens.Motion.fastMs))
                    },
                    label = "onboarding-step"
                ) { currentStep ->
                    when (currentStep) {
                        0 -> WelcomeStep()
                        1 -> HowProtectionWorksStep()
                        2 -> PermissionSetupStep(
                            accessibilityReady = accessibilityReady,
                            overlayReady = overlayReady,
                            usageReady = usageReady,
                            onOpenAccessibility = {
                                FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onOpenOverlay = {
                                FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            },
                            onOpenUsage = {
                                FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        )
                        3 -> PersonalizationStep(
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = onToggleTheme,
                            selectedExam = selectedExam,
                            onExamSelected = { selectedExam = it },
                            targetDate = targetDate,
                            dateError = dateError,
                            onPickDate = { showDatePicker = true },
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
                        else -> CompletionStep(
                            selectedExam = selectedExam,
                            accessibilityReady = accessibilityReady
                        )
                    }
                }
            }

            item(key = "actions") {
                val primaryLabel = when (step) {
                    0 -> "Get Started"
                    LAST_STEP -> "Go to Dashboard"
                    else -> "Continue"
                }
                GradientButton(
                    text = primaryLabel,
                    accentColor = GlassTokens.Accents.FocusSession,
                    leadingIcon = if (step == LAST_STEP) Icons.Rounded.CheckCircle else Icons.Rounded.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        when {
                            step < LAST_STEP -> step += 1
                            else -> profileViewModel.saveProfile(
                                exam = selectedExam,
                                targetDate = targetDate,
                                preferredSubjects = selectedSubjects.toList(),
                                onSaved = onComplete
                            )
                        }
                    }
                )

                if (step > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
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
private fun WelcomeStep() {
    OnboardingGlassCard(
        icon = Icons.Rounded.Security,
        accent = GlassTokens.Accents.FocusSession,
        title = "Protect your attention",
        body = "Focus Guard blocks short-form video traps while keeping your useful chats, lessons, and tools open."
    ) {
        FeatureHighlightRow("Block Instagram Reels & YouTube Shorts", GlassTokens.Accents.AppBlocking)
        FeatureHighlightRow("Trade distractions for active PYQ recall", GlassTokens.Accents.ProgressStats)
        FeatureHighlightRow("Build consistent daily focus streaks", GlassTokens.Accents.Schedules)
    }
}

@Composable
private fun HowProtectionWorksStep() {
    OnboardingGlassCard(
        icon = Icons.Rounded.AutoAwesome,
        accent = GlassTokens.Accents.AppBlocking,
        title = "How protection works",
        body = "Protection activates during Focus Sessions or Schedules when required Android permissions are enabled."
    ) {
        FeatureHighlightRow("Reels & Shorts Detection: on-device Accessibility check", GlassTokens.Accents.AppBlocking)
        FeatureHighlightRow("Focus Sessions & Schedules: automatic time-block enforcement", GlassTokens.Accents.Schedules)
        FeatureHighlightRow("Accountability Lock: trusted PIN protection across reboots", GlassTokens.Accents.AccountabilityLock)
    }
}

@Composable
private fun PermissionSetupStep(
    accessibilityReady: Boolean,
    overlayReady: Boolean,
    usageReady: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenUsage: () -> Unit
) {
    OnboardingGlassCard(
        icon = Icons.Outlined.Accessibility,
        accent = GlassTokens.Accents.AppBlocking,
        title = "Permission setup",
        body = "Accessibility is required for core blocking. Overlay and Usage Access are optional and enhance PYQ challenges and analytics."
    ) {
        PermissionActionRow(
            icon = Icons.Outlined.Accessibility,
            title = "Accessibility Service (Required)",
            subtitle = "Detects Reels, Shorts, and blocked apps on device.",
            ready = accessibilityReady,
            onClick = onOpenAccessibility
        )
        PermissionActionRow(
            icon = Icons.Outlined.Layers,
            title = "Display Over Other Apps (Optional)",
            subtitle = "Displays interactive PYQ challenges over blocked surfaces.",
            ready = overlayReady,
            onClick = onOpenOverlay
        )
        PermissionActionRow(
            icon = Icons.Outlined.QueryStats,
            title = "Usage Access (Optional)",
            subtitle = "Enables backup detection and screen-time analytics.",
            ready = usageReady,
            onClick = onOpenUsage
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PersonalizationStep(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    selectedExam: String,
    onExamSelected: (String) -> Unit,
    targetDate: Long,
    dateError: Boolean,
    onPickDate: () -> Unit,
    subjects: List<String>,
    selectedSubjects: List<String>,
    onToggleSubject: (String) -> Unit
) {
    OnboardingGlassCard(
        icon = Icons.Outlined.School,
        accent = GlassTokens.Accents.StudyFlow,
        title = "Personalize Focus Guard",
        body = "Pick your theme, exam track, and optional target date."
    ) {
        SecondaryButton(
            text = if (isDarkTheme) "Theme: Dark Glass" else "Theme: Light Glass",
            accentColor = GlassTokens.Accents.FocusSession,
            leadingIcon = if (isDarkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
            modifier = Modifier.fillMaxWidth(),
            onClick = onToggleTheme
        )

        examOptions.forEach { option ->
            SelectableRow(
                title = option.label,
                subtitle = option.subjects.joinToString(", "),
                selected = selectedExam == option.id,
                onClick = { onExamSelected(option.id) }
            )
        }

        OutlinedButton(
            onClick = onPickDate,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(GlassTokens.Radius.sm)
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarMonth,
                contentDescription = null,
                tint = GlassTokens.Accents.Schedules,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (targetDate > 0L) "Target: ${formatDate(targetDate)}" else "Set target date (Optional)",
                color = FrictionColors.TextPrimary
            )
        }
        if (dateError) {
            Text(
                text = "Choose a future date.",
                color = GlassTokens.Accents.AccountabilityLock,
                fontSize = 12.sp
            )
        }

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
private fun CompletionStep(
    selectedExam: String,
    accessibilityReady: Boolean
) {
    OnboardingGlassCard(
        icon = Icons.Rounded.CheckCircle,
        accent = GlassTokens.Accents.ProgressStats,
        title = "You're ready to focus",
        body = "Your ${selectedExam.uppercase(Locale.getDefault())} profile is configured. You can adjust permissions, schedules, and blocked apps anytime."
    ) {
        FeatureHighlightRow(
            text = if (accessibilityReady) {
                "Accessibility is active — ready to arm protection."
            } else {
                "Next: enable Accessibility on the setup gate to arm blocking."
            },
            accent = if (accessibilityReady) GlassTokens.Accents.ProgressStats else GlassTokens.Accents.Schedules
        )
    }
}

@Composable
private fun OnboardingGlassCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    body: String,
    content: @Composable () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = GlassTokens.Radius.lg,
        accentColor = accent,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon == Icons.Rounded.Security) {
                FocusGuardIcon(
                    iconId = FocusGuardIconId.BrandShieldBrain,
                    contentDescription = null,
                    tileSize = 48.dp,
                    showGlassTile = true,
                    selected = true
                )
            } else {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
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
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun FeatureHighlightRow(text: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassTokens.Radius.sm))
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(accent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = FrictionColors.TextPrimary,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun PermissionActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ready: Boolean,
    onClick: () -> Unit
) {
    val accent = if (ready) GlassTokens.Accents.ProgressStats else GlassTokens.Accents.AppBlocking
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GlassTokens.Radius.sm))
            .background(FrictionColors.SurfaceElevated.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = FrictionColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = FrictionColors.TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (ready) "Ready" else "Setup",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
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
            .clip(RoundedCornerShape(GlassTokens.Radius.sm))
            .clickable(onClick = onClick)
            .background(
                color = if (selected) GlassTokens.Accents.StudyFlow.copy(alpha = 0.16f) else FrictionColors.SurfaceElevated,
                shape = RoundedCornerShape(GlassTokens.Radius.sm)
            )
            .padding(14.dp)
    ) {
        Column {
            Text(
                text = title,
                color = if (selected) GlassTokens.Accents.StudyFlow else FrictionColors.TextPrimary,
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
                        color = if (index <= currentStep) {
                            GlassTokens.Accents.FocusSession
                        } else {
                            FrictionColors.SurfaceElevated
                        },
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

private const val LAST_STEP = 4
private const val DAY_MS = 24L * 60L * 60L * 1000L
