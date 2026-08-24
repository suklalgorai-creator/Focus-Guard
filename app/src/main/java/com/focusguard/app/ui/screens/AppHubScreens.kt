package com.focusguard.app.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.domain.profile.UserProfile
import com.focusguard.app.domain.settings.FocusSettings
import com.focusguard.app.persistence.DistractionRecoverySnapshot
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.presentation.profile.ProfileViewModel
import com.focusguard.app.presentation.settings.SettingsViewModel
import com.focusguard.app.presentation.usage.UsageStatsViewModel
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.GradientButton
import com.focusguard.app.ui.components.PremiumIconButton
import com.focusguard.app.ui.components.SectionHeader
import com.focusguard.app.ui.components.SecondaryButton
import com.focusguard.app.ui.components.StatItem
import com.focusguard.app.ui.theme.FrictionColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun TodayHubScreen(
    usageStatsViewModel: UsageStatsViewModel,
    focusSettings: FocusSettings,
    onOpenMenu: () -> Unit,
    onOpenShield: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenPyq: () -> Unit,
    onOpenYou: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as FocusGuardApp
    val prefs = app.prefs
    val usageState by usageStatsViewModel.state.collectAsStateWithLifecycle()
    val permissionMonitor = remember { PermissionMonitor(context) }

    var accessibilityEnabled by remember { mutableStateOf(false) }
    var overlayEnabled by remember { mutableStateOf(false) }
    var streakDays by remember { mutableIntStateOf(0) }
    var recoverySnapshot by remember { mutableStateOf(prefs.getDistractionRecoverySnapshot()) }

    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = permissionMonitor.isAccessibilityEnabled()
            overlayEnabled = permissionMonitor.isOverlayPermitted()
            recoverySnapshot = prefs.getDistractionRecoverySnapshot()
            streakDays = withContext(Dispatchers.IO) {
                runCatching { app.analyticsRepository.getStreak() }.getOrDefault(streakDays)
            }
            delay(5_000)
        }
    }

    val today = usageState.today
    val guardArmed = prefs.isGuardActiveNow() && accessibilityEnabled
    val coreReady = accessibilityEnabled
    val youtubeShieldEnabled = FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS in prefs.blockedContentSurfaces
    val productiveChannels = prefs.youtubeProductiveChannels.size
    val enforcedAppCount = prefs.blacklistedApps.size
    val topTrigger = today.allApps
        .filter { it.isBlacklisted }
        .maxByOrNull { it.usageTimeMs }

    val statusTitle = when {
        guardArmed -> "Protection Armed"
        coreReady -> "Protection Ready"
        else -> "Protection Incomplete"
    }
    val statusBody = when {
        guardArmed ->
            if (focusSettings.isStrictModeEnabled) {
                "Strict Mode is live. Blocked apps are being intercepted."
            } else {
                "Focus window is live. Blocked apps are being intercepted."
            }
        !accessibilityEnabled ->
            "Turn on Accessibility to start blocking."
        !overlayEnabled ->
            "Overlay is off. Blocking still works, but the challenge screen will not appear."
        prefs.isScheduleEnabled ->
            "${prefs.studyBlocks.count { it.enabled }} study block(s) set. Pick apps to block."
        else ->
            "Pick apps, then start Focus."
    }

    val nextWindowSummary = if (prefs.isScheduleEnabled) {
        val block = prefs.getActiveStudyBlock() ?: prefs.studyBlocks.firstOrNull { it.enabled }
        block?.let {
            "${it.title}: ${formatClock(it.startHour, it.startMinute)} - " +
                formatClock(it.endHour, it.endMinute)
        } ?: "No enabled study blocks"
    } else {
        "No focus schedule set"
    }

    HubCanvas {
        item {
            HubHeader(
                eyebrow = "Today",
                title = "Today",
                subtitle = "Protection, study, and progress in one place.",
                onOpenMenu = onOpenMenu
            )
        }

        recoverySnapshot?.let { recovery ->
            item {
                RecoveryNudgeCard(
                    snapshot = recovery,
                    onStartFocus = {
                        prefs.clearDistractionRecoverySnapshot()
                        recoverySnapshot = null
                        onOpenFocus()
                    },
                    onSolvePyq = {
                        prefs.clearDistractionRecoverySnapshot()
                        recoverySnapshot = null
                        onOpenPyq()
                    },
                    onDismiss = {
                        prefs.clearDistractionRecoverySnapshot()
                        recoverySnapshot = null
                    }
                )
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                backgroundColor = FrictionColors.GlassBackground
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = statusTitle,
                        color = when {
                            guardArmed -> FrictionColors.Success
                            coreReady -> FrictionColors.Warning
                            else -> FrictionColors.Accent
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = statusBody,
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatusChip("Accessibility", accessibilityEnabled)
                        StatusChip("Overlay", overlayEnabled)
                        StatusChip("Armed", guardArmed)
                    }
                    ProgressInsightRow(
                        label = "Blocked apps",
                        value = "$enforcedAppCount selected"
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                GradientButton(
                    text = "Start Focus",
                    leadingIcon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFocus
                )
                SecondaryButton(
                    text = "Open Shield",
                    leadingIcon = Icons.Rounded.Security,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenShield
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatItem(
                    label = "Saved today",
                    value = formatDuration(today.timeSavedMs),
                    icon = Icons.Rounded.CheckCircle,
                    modifier = Modifier.weight(1f),
                    accent = FrictionColors.Success
                )
                StatItem(
                    label = "Blocks today",
                    value = today.sessionsBlocked.toString(),
                    icon = Icons.Rounded.Block,
                    modifier = Modifier.weight(1f),
                    accent = FrictionColors.Accent
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatItem(
                    label = "Current streak",
                    value = "${streakDays}d",
                    icon = Icons.Rounded.AutoAwesome,
                    modifier = Modifier.weight(1f),
                    accent = FrictionColors.Warning
                )
                StatItem(
                    label = "Apps blocked",
                    value = enforcedAppCount.toString(),
                    icon = Icons.Rounded.Block,
                    modifier = Modifier.weight(1f),
                    accent = FrictionColors.Accent
                )
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                backgroundColor = FrictionColors.Surface.copy(alpha = 0.92f)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Next steps",
                        color = FrictionColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    ProgressInsightRow(
                        label = "Block rule",
                        value = "Selected apps are blocked on launch."
                    )
                    ProgressInsightRow(
                        label = "Top trigger",
                        value = topTrigger?.appName ?: "No clear trigger yet"
                    )
                    ProgressInsightRow(
                        label = "Schedule window",
                        value = nextWindowSummary
                    )
                    ProgressInsightRow(
                        label = "Study YouTube mode",
                        value = if (youtubeShieldEnabled) {
                            "Shorts blocked. $productiveChannels channels allowed."
                        } else {
                            "Shorts guard is off right now."
                        }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    text = "Solve PYQ",
                    leadingIcon = Icons.Rounded.School,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPyq
                )
                SecondaryButton(
                    text = "Open Progress",
                    leadingIcon = Icons.Rounded.Analytics,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenProgress
                )
            }
        }

        item {
            HubActionCard(
                title = "${prefs.targetExam.uppercase(Locale.getDefault())} setup",
                subtitle = "Exam, profile, and permission setup.",
                icon = Icons.Rounded.Person,
                accent = FrictionColors.Accent,
                onClick = onOpenYou
            )
        }
    }
}

@Composable
private fun RecoveryNudgeCard(
    snapshot: DistractionRecoverySnapshot,
    onStartFocus: () -> Unit,
    onSolvePyq: () -> Unit,
    onDismiss: () -> Unit
) {
    val taskLine = recoveryTaskLine(snapshot)
    val subtitle = recoverySubtitle(snapshot)

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.94f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(FrictionColors.Warning.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = FrictionColors.Warning,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Recovery move",
                            color = FrictionColors.Warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Caught you peeking at ${snapshot.sourceTitle}.",
                            color = FrictionColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = recoveryAgeText(snapshot.occurredAtMs),
                    color = FrictionColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = subtitle,
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            if (taskLine != null) {
                ProgressInsightRow(
                    label = "Next",
                    value = taskLine
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GradientButton(
                    text = "Start Focus",
                    leadingIcon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.weight(1f),
                    height = 50.dp,
                    cornerRadius = 14.dp,
                    onClick = onStartFocus
                )
                SecondaryButton(
                    text = "Solve PYQ",
                    leadingIcon = Icons.Rounded.School,
                    modifier = Modifier.weight(1f),
                    onClick = onSolvePyq
                )
            }

            Text(
                text = "Not now",
                color = FrictionColors.TextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.End)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            )
        }
    }
}

private fun recoverySubtitle(snapshot: DistractionRecoverySnapshot): String {
    val subject = snapshot.pyqSubject?.takeIf { it.isNotBlank() }
    return when (snapshot.pyqWasCorrect) {
        true -> {
            val subjectBit = subject?.let { " in $it" }.orEmpty()
            "PYQ done$subjectBit. Start a short focus sprint."
        }
        false -> {
            val subjectBit = subject?.let { "$it exposed a weak spot" } ?: "That question exposed a weak spot"
            "$subjectBit. Review it while it is fresh."
        }
        null -> "Block worked. Pick the next study step."
    }
}

private fun recoveryTaskLine(snapshot: DistractionRecoverySnapshot): String? {
    val title = snapshot.studyTaskTitle?.takeIf { it.isNotBlank() } ?: return null
    val subject = snapshot.studyTaskSubject?.takeIf { it.isNotBlank() }
    return listOfNotNull(subject, title).joinToString(" - ")
}

private fun recoveryAgeText(occurredAtMs: Long): String {
    val minutes = ((System.currentTimeMillis() - occurredAtMs) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes <= 0L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        else -> "${minutes / 60L}h ago"
    }
}

@Composable
fun ShieldHubScreen(
    focusSettings: FocusSettings,
    settingsViewModel: SettingsViewModel,
    onOpenMenu: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenFocus: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as FocusGuardApp
    val prefs = app.prefs
    val permissionMonitor = remember { PermissionMonitor(context) }

    var accessibilityEnabled by remember { mutableStateOf(false) }
    var overlayEnabled by remember { mutableStateOf(false) }
    var usageStatsEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = permissionMonitor.isAccessibilityEnabled()
            overlayEnabled = permissionMonitor.isOverlayPermitted()
            usageStatsEnabled = permissionMonitor.isUsageStatsPermitted()
            delay(2_500)
        }
    }

    val guardArmed = prefs.isGuardActiveNow() && accessibilityEnabled
    val surfaceBlocks = prefs.blockedContentSurfaces
    val productiveChannelCount = prefs.youtubeProductiveChannels.size
    val enforcedAppCount = prefs.blacklistedApps.size
    val uninstallProtectionReady = focusSettings.isStrictModeExitProtectionEnabled
    val canEditExitProtection = !focusSettings.isStrictModeEnabled || !uninstallProtectionReady

    HubCanvas {
        item {
            HubHeader(
                eyebrow = "Shield",
                title = "Shield",
                subtitle = if (guardArmed) {
                    "Focus Guard is intercepting distractions."
                } else {
                    "Pick apps, then start Focus or set a schedule."
                },
                onOpenMenu = onOpenMenu
            )
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                backgroundColor = FrictionColors.GlassBackground
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (guardArmed) "Blocking now" else "Ready",
                                color = if (guardArmed) FrictionColors.Success else FrictionColors.Warning,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (guardArmed) {
                                    "Blocked apps and focused surfaces are active."
                                } else {
                                    "Accessibility blocks. Overlay adds the challenge screen."
                                },
                                color = FrictionColors.TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            FrictionColors.Accent.copy(alpha = 0.25f),
                                            FrictionColors.Surface
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = FrictionColors.Accent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatItem(
                            label = "Apps blocked",
                            value = enforcedAppCount.toString(),
                            icon = Icons.Rounded.Block,
                            modifier = Modifier.weight(1f),
                            accent = FrictionColors.Accent
                        )
                        StatItem(
                            label = "Surface rules",
                            value = surfaceBlocks.size.toString(),
                            icon = Icons.Rounded.AutoAwesome,
                            modifier = Modifier.weight(1f),
                            accent = FrictionColors.Warning
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatusChip("Accessibility", accessibilityEnabled)
                        StatusChip("Overlay", overlayEnabled)
                        StatusChip("Usage", usageStatsEnabled)
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = "Controls",
                subtitle = "Pick apps, schedules, and permissions."
            )
        }

        item {
            HubActionCard(
                title = "Apps and Focused Blocks",
                subtitle = "Full app blocks, Reels, Shorts, and channel rules.",
                icon = Icons.Rounded.Block,
                accent = FrictionColors.Accent,
                onClick = onOpenApps
            )
        }

        item {
            HubActionCard(
                title = "Schedules",
                subtitle = "Set focus windows and strict sessions.",
                icon = Icons.Rounded.Schedule,
                accent = FrictionColors.Warning,
                onClick = onOpenSchedule
            )
        }

        item {
            HubActionCard(
                title = "Protection Setup",
                subtitle = "Check permission health before relying on the blocker.",
                icon = Icons.Rounded.AdminPanelSettings,
                accent = FrictionColors.Success,
                onClick = onOpenPermissions
            )
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                backgroundColor = FrictionColors.Surface.copy(alpha = 0.92f)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Study YouTube Mode",
                        color = FrictionColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Keep lessons open. Block Shorts.",
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatItem(
                            label = "Productive channels",
                            value = productiveChannelCount.toString(),
                            icon = Icons.Rounded.School,
                            modifier = Modifier.weight(1f),
                            accent = FrictionColors.Success
                        )
                        StatItem(
                            label = "Shorts guard",
                            value = if (FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS in surfaceBlocks) "On" else "Off",
                            icon = Icons.Rounded.PlayArrow,
                            modifier = Modifier.weight(1f),
                            accent = FrictionColors.Accent
                        )
                    }
                    SecondaryButton(
                        text = "Tune YouTube rules",
                        leadingIcon = Icons.Rounded.PlayArrow,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenApps
                    )
                }
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                backgroundColor = FrictionColors.GlassBackground
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Guard self-protection",
                                color = FrictionColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (uninstallProtectionReady) {
                                    "Exit Protection is ready for Strict Mode."
                                } else {
                                    "Optional. Helps prevent self-removal during Strict Mode."
                                },
                                color = FrictionColors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = uninstallProtectionReady,
                            onCheckedChange = {
                                if (canEditExitProtection) {
                                    settingsViewModel.setExitProtection(it)
                                }
                            },
                            enabled = canEditExitProtection,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = FrictionColors.Accent,
                                uncheckedThumbColor = FrictionColors.TextMuted,
                                uncheckedTrackColor = FrictionColors.SurfaceElevated,
                                disabledCheckedThumbColor = FrictionColors.TextSecondary,
                                disabledCheckedTrackColor = FrictionColors.Accent.copy(alpha = 0.35f)
                            )
                        )
                    }
                    Text(
                        text = if (canEditExitProtection) {
                            "Only active during Strict Mode. Other apps and files are untouched."
                        } else {
                            "Exit Protection stays locked while the active Strict Mode session is running."
                        },
                        color = FrictionColors.TextMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    GradientButton(
                        text = "Arm a focus session",
                        leadingIcon = Icons.Rounded.Security,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenFocus
                    )
                }
            }
        }
    }
}

@Composable
fun ProgressHubScreen(
    usageStatsViewModel: UsageStatsViewModel,
    onOpenMenu: () -> Unit,
    onOpenUsageDetail: () -> Unit,
    onOpenPyq: () -> Unit,
    onOpenFocus: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as FocusGuardApp
    val usageState by usageStatsViewModel.state.collectAsStateWithLifecycle()

    var streakDays by remember { mutableIntStateOf(0) }
    var pyqsToday by remember { mutableIntStateOf(0) }
    var weeklyBlockedMinutes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(usageState.today, usageState.weeklyUsage) {
        streakDays = withContext(Dispatchers.IO) {
            runCatching { app.analyticsRepository.getStreak() }.getOrDefault(0)
        }
        pyqsToday = withContext(Dispatchers.IO) {
            runCatching { app.analyticsRepository.getTodayAttempts() }.getOrDefault(0)
        }
        weeklyBlockedMinutes = usageState.weeklyUsage.values
            .flatten()
            .filter { it.isBlacklisted }
            .sumOf { it.usageTimeMs } / 60_000L
    }

    val topTrigger = usageState.today.allApps
        .filter { it.isBlacklisted }
        .maxByOrNull { it.usageTimeMs }

    HubCanvas {
        item {
            HubHeader(
                eyebrow = "Progress",
                title = "Progress",
                subtitle = "Study time, blocks, and recovery.",
                onOpenMenu = onOpenMenu
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatItem(
                    label = "Saved today",
                    value = formatDuration(usageState.today.timeSavedMs),
                    icon = Icons.Rounded.CheckCircle,
                    modifier = Modifier.weight(1f),
                    accent = FrictionColors.Success
                )
                StatItem(
                    label = "Blocks today",
                    value = usageState.today.sessionsBlocked.toString(),
                    icon = Icons.Rounded.Block,
                    modifier = Modifier.weight(1f),
                    accent = FrictionColors.Accent
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatItem(
                    label = "Streak",
                    value = "${streakDays}d",
                    icon = Icons.Rounded.AutoAwesome,
                    modifier = Modifier.weight(1f),
                    accent = FrictionColors.Warning
                )
                StatItem(
                    label = "PYQs today",
                    value = pyqsToday.toString(),
                    icon = Icons.Rounded.School,
                    modifier = Modifier.weight(1f),
                    accent = FrictionColors.Accent
                )
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                backgroundColor = FrictionColors.GlassBackground
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "This week",
                        color = FrictionColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    ProgressInsightRow(
                        label = "Top trigger",
                        value = topTrigger?.appName ?: "No clear trigger yet"
                    )
                    ProgressInsightRow(
                        label = "Distracting time",
                        value = if (weeklyBlockedMinutes > 0L) "${weeklyBlockedMinutes}m this week" else "Calm week so far"
                    )
                    ProgressInsightRow(
                        label = "Bounce-back angle",
                        value = if (usageState.today.sessionsBlocked > 0) {
                            "Use a 10 minute sprint right after a block to train recovery."
                        } else {
                            "No recovery drills needed yet today."
                        }
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "Next move",
                subtitle = "Pick one action."
            )
        }

        item {
            GradientButton(
                text = "Open full analytics",
                leadingIcon = Icons.Rounded.Analytics,
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenUsageDetail
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SecondaryButton(
                    text = "Solve PYQ now",
                    leadingIcon = Icons.Rounded.School,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenPyq
                )
                SecondaryButton(
                    text = "Start recovery sprint",
                    leadingIcon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFocus
                )
            }
        }
    }
}

@Composable
fun YouHubScreen(
    profileViewModel: ProfileViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenMenu: () -> Unit,
    onOpenProfileDetails: () -> Unit,
    onOpenPermissionHealth: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as FocusGuardApp
    val profile by profileViewModel.profileFlow.collectAsStateWithLifecycle()
    val daysUntilExam = app.prefs.getDaysUntilExam()
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

    HubCanvas {
        item {
            HubHeader(
                eyebrow = "You",
                title = "You",
                subtitle = "Exam, profile, and app setup.",
                onOpenMenu = onOpenMenu
            )
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                backgroundColor = FrictionColors.GlassBackground
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(FrictionColors.AccentSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = FrictionColors.Accent
                            )
                        }
                        Column {
                            Text(
                                text = examName(profile),
                                color = FrictionColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = if (daysUntilExam >= 0) "$daysUntilExam days to target" else "Target date not set",
                                color = FrictionColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatItem(
                            label = "Subjects",
                            value = profile.preferredSubjects.size.toString(),
                            icon = Icons.Rounded.School,
                            modifier = Modifier.weight(1f),
                            accent = FrictionColors.Success
                        )
                        StatItem(
                            label = "Notifications",
                            value = if (notificationsEnabled) "On" else "Off",
                            icon = Icons.Outlined.Notifications,
                            modifier = Modifier.weight(1f),
                            accent = FrictionColors.Warning
                        )
                    }
                }
            }
        }

        item {
            HubActionCard(
                title = "Exam and profile details",
                subtitle = "Exam target, subjects, and study profile.",
                icon = Icons.Rounded.Person,
                accent = FrictionColors.Accent,
                onClick = onOpenProfileDetails
            )
        }

        item {
            HubActionCard(
                title = "Permission health",
                subtitle = "Accessibility, overlay, usage, and battery.",
                icon = Icons.Rounded.QueryStats,
                accent = FrictionColors.Success,
                onClick = onOpenPermissionHealth
            )
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                backgroundColor = FrictionColors.Surface.copy(alpha = 0.92f)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Personal feel",
                        color = FrictionColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Theme, notifications, and trust setup.",
                        color = FrictionColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SecondaryButton(
                            text = if (isDarkTheme) "Light Theme" else "Dark Theme",
                            leadingIcon = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            modifier = Modifier.weight(1f),
                            onClick = onToggleTheme
                        )
                        SecondaryButton(
                            text = "Notifications",
                            leadingIcon = Icons.Outlined.Notifications,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                } else {
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                }
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HubCanvas(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
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
            ),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun HubHeader(
    eyebrow: String,
    title: String,
    subtitle: String,
    onOpenMenu: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow.uppercase(),
                color = FrictionColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = FrictionColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        PremiumIconButton(
            icon = Icons.Rounded.Menu,
            contentDescription = "Open menu",
            onClick = onOpenMenu
        )
    }
}

@Composable
private fun HubActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        backgroundColor = FrictionColors.GlassBackground,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = FrictionColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = FrictionColors.TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, enabled: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (enabled) FrictionColors.SuccessSoft else FrictionColors.SurfaceElevated
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (enabled) FrictionColors.Success else FrictionColors.TextMuted)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = FrictionColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProgressInsightRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(FrictionColors.SurfaceElevated.copy(alpha = 0.5f))
            .padding(12.dp)
    ) {
        Text(
            text = label,
            color = FrictionColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            color = FrictionColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 19.sp
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

private fun formatClock(hour: Int, minute: Int): String {
    return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
}

private fun examName(profile: UserProfile): String {
    return when (profile.exam.lowercase()) {
        "neet" -> "NEET mode"
        "jee" -> "JEE mode"
        "upsc" -> "UPSC mode"
        else -> profile.exam.uppercase(Locale.getDefault())
    }
}

@Suppress("unused")
private fun formatDate(value: Long): String {
    if (value <= 0L) return "Not set"
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(value))
}
