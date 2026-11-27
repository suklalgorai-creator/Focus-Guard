package com.focusguard.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.antibypass.EnforcementLevel
import com.focusguard.app.antibypass.MissingPermissionType
import com.focusguard.app.antibypass.PermissionHealthSummary
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.domain.settings.FocusSettings
import com.focusguard.app.persistence.DistractionRecoverySnapshot
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.presentation.usage.UsageStatsViewModel
import com.focusguard.app.ui.components.FocusGuardIconId
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.GradientButton
import com.focusguard.app.ui.components.SecondaryButton
import com.focusguard.app.ui.components.StatItem
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.theme.GlassTokens
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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
    val lifecycleOwner = LocalLifecycleOwner.current

    var healthSummary by remember { mutableStateOf(permissionMonitor.getHealthSummary()) }
    var accessibilityEnabled by remember { mutableStateOf(healthSummary.snapshot.accessibilityEnabled) }
    var overlayEnabled by remember { mutableStateOf(healthSummary.snapshot.overlayEnabled) }
    var streakDays by remember { mutableIntStateOf(0) }
    var recoverySnapshot by remember { mutableStateOf(prefs.getDistractionRecoverySnapshot()) }

    fun refreshPermissionHealth() {
        permissionMonitor.checkAllPermissions()
        val next = permissionMonitor.getHealthSummary()
        healthSummary = next
        accessibilityEnabled = next.snapshot.accessibilityEnabled
        overlayEnabled = next.snapshot.overlayEnabled
        recoverySnapshot = prefs.getDistractionRecoverySnapshot()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissionHealth()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        streakDays = withContext(Dispatchers.IO) {
            runCatching { app.analyticsRepository.getStreak() }.getOrDefault(0)
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

        if (healthSummary.enforcementLevel != EnforcementLevel.ACTIVE &&
            healthSummary.primaryMissing != null
        ) {
            item {
                PermissionRecoveryCard(
                    summary = healthSummary,
                    lockPreserved = prefs.isAccountabilityLockActive,
                    onFixPermission = { missing ->
                        prefs.allowPermissionSetupWindow()
                        openSettingsForPermission(context, missing)
                    }
                )
            }
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
                isActive = guardArmed,
                accentColor = GlassTokens.Accents.AppBlocking,
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
                    accentColor = GlassTokens.Accents.FocusSession,
                    leadingIcon = Icons.Rounded.PlayArrow,
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFocus
                )
                SecondaryButton(
                    text = "Open Shield",
                    accentColor = GlassTokens.Accents.AppBlocking,
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
                    accent = GlassTokens.Accents.ProgressStats
                )
                StatItem(
                    label = "Blocks today",
                    value = today.sessionsBlocked.toString(),
                    icon = Icons.Rounded.Block,
                    modifier = Modifier.weight(1f),
                    accent = GlassTokens.Accents.AppBlocking
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
                    accent = GlassTokens.Accents.Schedules
                )
                StatItem(
                    label = "Apps blocked",
                    value = enforcedAppCount.toString(),
                    icon = Icons.Rounded.Block,
                    modifier = Modifier.weight(1f),
                    accent = GlassTokens.Accents.AppBlocking
                )
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                accentColor = GlassTokens.Accents.Schedules,
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
                accent = FocusGuardIconId.Account.accentColor,
                iconId = FocusGuardIconId.Account,
                onClick = onOpenYou
            )
        }
    }
}

@Composable
private fun PermissionRecoveryCard(
    summary: PermissionHealthSummary,
    lockPreserved: Boolean,
    onFixPermission: (MissingPermissionType) -> Unit
) {
    val missing = summary.primaryMissing ?: return
    val isUnavailable = summary.enforcementLevel == EnforcementLevel.UNAVAILABLE
    val accentColor = if (isUnavailable) FrictionColors.Error else FrictionColors.Warning

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        backgroundColor = if (isUnavailable) FrictionColors.ErrorSoft else FrictionColors.WarningSoft
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (isUnavailable) {
                    "${missing.label} required"
                } else {
                    "Degraded mode: ${missing.label} off"
                },
                color = accentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = buildString {
                    append(summary.affectedFeaturesSummary)
                    if (lockPreserved && isUnavailable) {
                        append(" Accountability Lock remains preserved.")
                    }
                },
                color = FrictionColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            SecondaryButton(
                text = "Open ${missing.label} settings",
                leadingIcon = Icons.Rounded.AdminPanelSettings,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onFixPermission(missing) }
            )
        }
    }
}

private fun openSettingsForPermission(
    context: Context,
    missing: MissingPermissionType
) {
    val intent = when (missing) {
        MissingPermissionType.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        MissingPermissionType.OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        MissingPermissionType.USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        MissingPermissionType.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }
    context.startActivity(intent)
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
