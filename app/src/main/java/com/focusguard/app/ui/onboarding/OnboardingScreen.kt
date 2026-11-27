package com.focusguard.app.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.presentation.profile.ProfileViewModel
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.GradientButton
import com.focusguard.app.ui.components.SecondaryButton
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.theme.GlassTokens

private enum class ProtectionGoal(val label: String, val subtitle: String) {
    REELS("Instagram Reels", "Block or set a daily Reels limit."),
    SHORTS("YouTube Shorts", "Block Shorts while keeping long videos open."),
    BOTH("Both Reels & Shorts", "Protect your focus across both apps.")
}

@Composable
fun OnboardingScreen(
    profileViewModel: ProfileViewModel,
    isDarkTheme: Boolean = true,
    onToggleTheme: () -> Unit = {},
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as FocusGuardApp
    val lifecycleOwner = LocalLifecycleOwner.current
    val permissionMonitor = remember { PermissionMonitor(context) }

    var step by remember { mutableIntStateOf(0) }
    var selectedGoal by remember { mutableStateOf(ProtectionGoal.BOTH) }

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

    val applySelectedGoal: () -> Unit = {
        val surfaces = when (selectedGoal) {
            ProtectionGoal.REELS -> setOf(FocusGuardPrefs.SURFACE_INSTAGRAM_REELS)
            ProtectionGoal.SHORTS -> setOf(FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS)
            ProtectionGoal.BOTH -> setOf(
                FocusGuardPrefs.SURFACE_INSTAGRAM_REELS,
                FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS
            )
        }
        app.prefs.blockedContentSurfaces = surfaces
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
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Step ${step + 1} of 5",
                        color = FrictionColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(
                        onClick = {
                            applySelectedGoal()
                            profileViewModel.skipOnboarding(onComplete)
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Skip", color = FrictionColors.TextSecondary, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = (step + 1) / 5f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = GlassTokens.Accents.FocusSession,
                    trackColor = FrictionColors.SurfaceElevated
                )
            }

            item(key = "content") {
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
                        1 -> ProtectionGoalsStep(
                            selectedGoal = selectedGoal,
                            onSelectGoal = { selectedGoal = it }
                        )
                        2 -> EnableProtectionStep(
                            accessibilityReady = accessibilityReady,
                            onOpenAccessibility = {
                                app.prefs.allowPermissionSetupWindow()
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        )
                        3 -> SetupChecklistStep(
                            accessibilityReady = accessibilityReady,
                            overlayReady = overlayReady,
                            usageReady = usageReady,
                            onOpenAccessibility = {
                                app.prefs.allowPermissionSetupWindow()
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            },
                            onOpenOverlay = {
                                app.prefs.allowPermissionSetupWindow()
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            },
                            onOpenUsage = {
                                app.prefs.allowPermissionSetupWindow()
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        )
                        else -> ReadyStep(accessibilityReady = accessibilityReady)
                    }
                }
            }

            item(key = "actions") {
                val primaryLabel = when (step) {
                    0 -> "Get Started"
                    LAST_STEP -> "Open Focus Guard"
                    else -> "Continue"
                }
                GradientButton(
                    text = primaryLabel,
                    accentColor = GlassTokens.Accents.FocusSession,
                    leadingIcon = if (step == LAST_STEP) Icons.Rounded.CheckCircle else Icons.Rounded.PlayArrow,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        when {
                            step < LAST_STEP -> {
                                if (step == 1) applySelectedGoal()
                                step += 1
                            }
                            else -> {
                                applySelectedGoal()
                                if (accessibilityReady) {
                                    app.prefs.isProtectionArmed = true
                                    app.prefs.isServiceEnabled = true
                                }
                                profileViewModel.skipOnboarding(onComplete)
                            }
                        }
                    }
                )

                if (step > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = { step -= 1 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Back", color = FrictionColors.TextSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        accentColor = GlassTokens.Accents.FocusSession,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = GlassTokens.Accents.FocusSession,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = "Take back control of your time.",
                color = FrictionColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 32.sp
            )
            Text(
                text = "Focus Guard helps you reduce distractions and build better focus habits.",
                color = FrictionColors.TextSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
private fun ProtectionGoalsStep(
    selectedGoal: ProtectionGoal,
    onSelectGoal: (ProtectionGoal) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "What would you like to block?",
            color = FrictionColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "You can change this anytime in Shield.",
            color = FrictionColors.TextSecondary,
            fontSize = 14.sp
        )

        ProtectionGoal.entries.forEach { goal ->
            val selected = goal == selectedGoal
            val accent = GlassTokens.Accents.FocusSession
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) accent.copy(alpha = 0.14f) else FrictionColors.Surface)
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) accent else FrictionColors.GlassBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable(role = Role.RadioButton) { onSelectGoal(goal) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (selected) accent else FrictionColors.TextMuted
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = goal.label,
                        color = FrictionColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = goal.subtitle,
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EnableProtectionStep(
    accessibilityReady: Boolean,
    onOpenAccessibility: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        isActive = accessibilityReady,
        accentColor = if (accessibilityReady) FrictionColors.Success else GlassTokens.Accents.AppBlocking,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Turn on Accessibility access",
                color = FrictionColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Focus Guard uses Accessibility only to detect blocked apps and Reels or Shorts on your device.",
                color = FrictionColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Text(
                text = "We never collect messages, captions, or passwords.",
                color = FrictionColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Text(
                text = if (accessibilityReady) "Status: Enabled" else "Status: Not enabled yet",
                color = if (accessibilityReady) FrictionColors.Success else FrictionColors.Warning,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (!accessibilityReady) {
                SecondaryButton(
                    text = "Open Accessibility Settings",
                    onClick = onOpenAccessibility
                )
            }
        }
    }
}

@Composable
private fun SetupChecklistStep(
    accessibilityReady: Boolean,
    overlayReady: Boolean,
    usageReady: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenOverlay: () -> Unit,
    onOpenUsage: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Setup checklist",
            color = FrictionColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Only Accessibility is required. Other permissions are optional.",
            color = FrictionColors.TextSecondary,
            fontSize = 14.sp
        )

        ChecklistRow(
            title = "Accessibility access (Required)",
            subtitle = "Detects blocked apps, Reels, and Shorts.",
            granted = accessibilityReady,
            onFix = onOpenAccessibility
        )
        ChecklistRow(
            title = "Display over apps (Optional)",
            subtitle = "Shows the block challenge and floating Reel counter.",
            granted = overlayReady,
            onFix = onOpenOverlay
        )
        ChecklistRow(
            title = "Usage access (Optional)",
            subtitle = "Improves daily app time insights.",
            granted = usageReady,
            onFix = onOpenUsage
        )
    }
}

@Composable
private fun ChecklistRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onFix: () -> Unit
) {
    val statusText = if (granted) "Ready" else "Set up"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(FrictionColors.Surface)
            .border(1.dp, FrictionColors.GlassBorder, RoundedCornerShape(16.dp))
            .clickable(enabled = !granted, onClick = onFix)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $statusText"
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (granted) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (granted) FrictionColors.Success else FrictionColors.Warning
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = FrictionColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp
            )
        }
        Text(
            text = statusText,
            color = if (granted) FrictionColors.Success else GlassTokens.Accents.FocusSession,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ReadyStep(accessibilityReady: Boolean) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        isActive = accessibilityReady,
        accentColor = FrictionColors.Success,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = FrictionColors.Success,
                modifier = Modifier.size(34.dp)
            )
            Text(
                text = "You're ready.",
                color = FrictionColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (accessibilityReady) {
                    "Protection is active. You can start a focus session anytime from Today."
                } else {
                    "You can finish turning on Accessibility anytime from Today."
                },
                color = FrictionColors.TextSecondary,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )
        }
    }
}

private const val LAST_STEP = 4
