package com.focusguard.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.domain.AppUsageData
import com.focusguard.app.domain.settings.FocusSettings
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.presentation.settings.SettingsViewModel
import com.focusguard.app.presentation.usage.UsageStatsViewModel
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.PremiumIconButton
import com.focusguard.app.ui.components.StableLinearProgress
import com.focusguard.app.ui.theme.FrictionColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
@Suppress("UNUSED_PARAMETER")
fun DashboardScreen(
    usageStatsViewModel: UsageStatsViewModel,
    focusSettings: FocusSettings,
    settingsViewModel: SettingsViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenMenu: () -> Unit = {},
    onNavigateBlacklist: () -> Unit = {},
    onNavigateFocus: () -> Unit = {},
    onNavigateUsage: () -> Unit = {},
    onNavigateSchedule: () -> Unit = {},
    onNavigatePyq: () -> Unit = {},
    onNavigateProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as FocusGuardApp
    val usageState by usageStatsViewModel.state.collectAsStateWithLifecycle()

    var focusStreak by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        while (true) {
            focusStreak = withContext(Dispatchers.IO) {
                runCatching { app.analyticsRepository.getStreak() }.getOrDefault(focusStreak)
            }
            delay(30_000)
        }
    }

    val today = usageState.today
    val blacklistedPackages = app.prefs.blacklistedApps
    val focusedSurfaceBlocks = app.prefs.blockedContentSurfaces
    val protectedApps = remember(today.allApps, blacklistedPackages) {
        val byPackage = today.allApps.associateBy { it.packageName }
        blacklistedPackages.take(3).mapNotNull { byPackage[it] }
    }.ifEmpty {
        today.allApps.filter { it.isBlacklisted }.take(3)
    }
    val totalProtected = blacklistedPackages.size
    val youtubeShieldEnabled = remember(blacklistedPackages, focusedSurfaceBlocks) {
        FocusGuardPrefs.SURFACE_YOUTUBE_SHORTS in focusedSurfaceBlocks ||
            blacklistedPackages.any { it.contains("youtube", ignoreCase = true) }
    }
    val distractionChange = percentageDelta(
        current = today.distractionTimeMs,
        previous = yesterdayDistractionMs(usageState.weeklyUsage)
    )
    val blockedChange = percentageDelta(
        current = today.sessionsBlocked.toLong(),
        previous = yesterdayBlockedCount(usageState.weeklyUsage)
    )
    val focusTimeMs = (today.totalUsageTimeMs - today.distractionTimeMs).coerceAtLeast(0L)
    val focusChange = percentageDelta(
        current = focusTimeMs,
        previous = yesterdayFocusTimeMs(usageState.weeklyUsage)
    )
    val scheduleSummary = remember(app.prefs.isScheduleEnabled, app.prefs.studyBlocks) {
        if (!app.prefs.isScheduleEnabled) {
            "Off"
        } else {
            val block = app.prefs.getActiveStudyBlock() ?: app.prefs.studyBlocks.firstOrNull { it.enabled }
            block?.let {
                "${formatScheduleClock(it.startHour, it.startMinute)} - ${formatScheduleClock(it.endHour, it.endMinute)}"
            } ?: "No blocks"
        }
    }
    val scheduleMeta = remember(app.prefs.isScheduleEnabled, app.prefs.studyBlocks) {
        if (!app.prefs.isScheduleEnabled) {
            "Tap to set your focus window"
        } else {
            "${app.prefs.studyBlocks.count { it.enabled }} study block(s)"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        FrictionColors.GradientStart,
                        FrictionColors.Background,
                        FrictionColors.GradientEnd
                    )
                )
            )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(260)) +
                slideInVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 10 }
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HomeHeader(
                        strictModeActive = focusSettings.isStrictModeEnabled,
                        onOpenMenu = onOpenMenu,
                        onOpenSettings = onNavigateProfile
                    )
                }
                item {
                    ProtectionSummaryCard(
                        blockedAttempts = today.sessionsBlocked,
                        distractionTimeMs = today.distractionTimeMs,
                        focusTimeMs = focusTimeMs,
                        timeSavedMs = today.timeSavedMs,
                        blockedChange = blockedChange,
                        distractionChange = distractionChange,
                        focusChange = focusChange,
                        onOpenStats = onNavigateUsage
                    )
                }
                item {
                    ProtectedAppsCard(
                        protectedApps = protectedApps,
                        totalProtected = totalProtected,
                        enabled = youtubeShieldEnabled,
                        onManage = onNavigateBlacklist
                    )
                }
                item {
                    ControlCenterCard(
                        streakDays = focusStreak,
                        timeSavedMs = today.timeSavedMs,
                        scheduleSummary = scheduleSummary,
                        scheduleMeta = scheduleMeta,
                        onProtectedApps = onNavigateBlacklist,
                        onReelsShield = onNavigateBlacklist,
                        onStudy = onNavigatePyq,
                        onSchedule = onNavigateSchedule,
                        onLockdown = onNavigateFocus
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    strictModeActive: Boolean,
    onOpenMenu: () -> Unit,
    onOpenSettings: () -> Unit
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
                    .size(60.dp)
                    .shadow(20.dp, CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                FrictionColors.Accent.copy(alpha = 0.24f),
                                FrictionColors.Surface
                            )
                        )
                    )
                    .border(1.dp, FrictionColors.Accent.copy(alpha = 0.48f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = FrictionColors.Accent,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Focus Shield",
                    color = FrictionColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (strictModeActive) {
                        "Blocking is active."
                    } else {
                        "Ready. Start Focus."
                    },
                    color = FrictionColors.TextSecondary,
                    fontSize = 15.sp
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatusChip(
                title = if (strictModeActive) "Protected" else "Standby",
                subtitle = if (strictModeActive) "Strict mode active" else "Shield available"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PremiumIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings
                )
                PremiumIconButton(
                    icon = Icons.Outlined.MoreVert,
                    contentDescription = "Menu",
                    onClick = onOpenMenu
                )
            }
        }
    }
}

@Composable
private fun StatusChip(title: String, subtitle: String) {
    GlassCard(
        cornerRadius = 22.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.86f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(FrictionColors.AccentSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = FrictionColors.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = FrictionColors.Accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = subtitle,
                    color = FrictionColors.TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun ShieldStatusHero(
    isActive: Boolean,
    totalProtected: Int,
    onOpenLogs: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        isActive = isActive,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "SHIELD STATUS",
                        color = FrictionColors.Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Strict Protection",
                        color = FrictionColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isActive) "ACTIVE" else "READY",
                        color = FrictionColors.Accent,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isActive) {
                            "Blocking is active."
                        } else {
                            "Ready. Start Focus."
                        },
                        color = FrictionColors.TextSecondary,
                        fontSize = 16.sp,
                        lineHeight = 24.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    FrictionColors.Accent.copy(alpha = 0.38f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(FrictionColors.Accent.copy(alpha = 0.08f))
                            .border(1.dp, FrictionColors.Accent.copy(alpha = 0.30f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = FrictionColors.Accent,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
            }

            ProtectionPoint(
                title = "Exit Lock",
                body = if (isActive) "Active" else "Ready",
                accent = FrictionColors.Accent
            )
            ProtectionPoint(
                title = "Apps",
                body = "Blocked on launch",
                accent = FrictionColors.Accent
            )
            ProtectionPoint(
                title = "Reels & Shorts",
                body = "Focused surfaces",
                accent = FrictionColors.Accent
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCard(
                    modifier = Modifier.weight(1f),
                    cornerRadius = 18.dp,
                    backgroundColor = FrictionColors.SurfaceLight.copy(alpha = 0.30f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = FrictionColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Protection running in the background",
                            color = FrictionColors.Accent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                GlassCard(
                    cornerRadius = 18.dp,
                    backgroundColor = FrictionColors.SurfaceLight.copy(alpha = 0.30f),
                    onClick = onOpenLogs
                ) {
                    Text(
                        text = "View logs",
                        color = FrictionColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }

            Text(
                text = "$totalProtected apps selected",
                color = FrictionColors.TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ProtectionPoint(
    title: String,
    body: String,
    accent: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = FrictionColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            )
            Text(
                text = body,
                color = FrictionColors.TextSecondary,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = FrictionColors.TextPrimary,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                color = FrictionColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
private fun ProtectionSummaryCard(
    blockedAttempts: Int,
    distractionTimeMs: Long,
    focusTimeMs: Long,
    timeSavedMs: Long,
    blockedChange: Int,
    distractionChange: Int,
    focusChange: Int,
    onOpenStats: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.90f),
        onClick = onOpenStats
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionHeader(
                title = "Today's Protection",
                action = "View details",
                onAction = onOpenStats
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactMetricCell(
                    modifier = Modifier.weight(1f),
                    title = "Blocked",
                    value = blockedAttempts.toString(),
                    delta = deltaText(blockedChange, "vs yesterday"),
                    accent = FrictionColors.Accent
                )
                CompactMetricCell(
                    modifier = Modifier.weight(1f),
                    title = "Distracted",
                    value = formatDuration(distractionTimeMs),
                    delta = deltaText(-distractionChange, "vs yesterday"),
                    accent = FrictionColors.Warning
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactMetricCell(
                    modifier = Modifier.weight(1f),
                    title = "Focus Time",
                    value = formatDuration(focusTimeMs),
                    delta = deltaText(focusChange, "vs yesterday"),
                    accent = FrictionColors.Accent
                )
                CompactMetricCell(
                    modifier = Modifier.weight(1f),
                    title = "Saved",
                    value = formatDuration(timeSavedMs),
                    delta = "This session",
                    accent = FrictionColors.Accent
                )
            }
        }
    }
}

@Composable
private fun CompactMetricCell(
    modifier: Modifier,
    title: String,
    value: String,
    delta: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(FrictionColors.SurfaceLight.copy(alpha = 0.24f))
            .border(0.7.dp, FrictionColors.GlassBorder, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            color = FrictionColors.TextSecondary,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = FrictionColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = delta,
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProtectedAppsCard(
    protectedApps: List<AppUsageData>,
    totalProtected: Int,
    enabled: Boolean,
    onManage: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.90f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeader(
                title = "Apps to Block",
                action = "Select apps",
                onAction = onManage
            )
            Text(
                text = "Pick apps to block.",
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp
            )
            ProtectedAppsRow(
                protectedApps = protectedApps,
                totalProtected = totalProtected,
                onManage = onManage
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FrictionColors.SurfaceLight.copy(alpha = 0.24f))
                    .border(0.7.dp, FrictionColors.GlassBorder, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Study YouTube Mode",
                        color = FrictionColors.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Keep lessons open. Block Shorts.",
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = FrictionColors.Accent,
                        uncheckedThumbColor = FrictionColors.TextMuted,
                        uncheckedTrackColor = FrictionColors.SurfaceElevated
                    )
                )
            }
        }
    }
}

@Composable
private fun ControlCenterCard(
    streakDays: Int,
    timeSavedMs: Long,
    scheduleSummary: String,
    scheduleMeta: String,
    onProtectedApps: () -> Unit,
    onReelsShield: () -> Unit,
    onStudy: () -> Unit,
    onSchedule: () -> Unit,
    onLockdown: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.90f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                backgroundColor = FrictionColors.SurfaceLight.copy(alpha = 0.24f),
                onClick = onSchedule
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(FrictionColors.Accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = FrictionColors.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Schedule",
                                color = FrictionColors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = scheduleSummary,
                                color = FrictionColors.Accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = scheduleMeta,
                                color = FrictionColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Text(
                        text = "Edit",
                        color = FrictionColors.Accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Focus Streak",
                        color = FrictionColors.TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$streakDays days active",
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = "${formatDuration(timeSavedMs)} saved",
                    color = FrictionColors.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            StableLinearProgress(
                progress = streakDays.coerceIn(0, 7) / 7f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(100.dp)),
                color = FrictionColors.Accent,
                trackColor = FrictionColors.SurfaceElevated
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactActionCell(
                    modifier = Modifier.weight(1f),
                    title = "Protected",
                    subtitle = "Manage",
                    icon = Icons.Rounded.Security,
                    onClick = onProtectedApps
                )
                CompactActionCell(
                    modifier = Modifier.weight(1f),
                    title = "Reels",
                    subtitle = "Shield",
                    icon = Icons.Rounded.Block,
                    onClick = onReelsShield
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CompactActionCell(
                    modifier = Modifier.weight(1f),
                    title = "Study",
                    subtitle = "PYQ unlock",
                    icon = Icons.Rounded.School,
                    onClick = onStudy
                )
                CompactActionCell(
                    modifier = Modifier.weight(1f),
                    title = "Schedule",
                    subtitle = "Time blocks",
                    icon = Icons.Rounded.CalendarMonth,
                    onClick = onSchedule
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(FrictionColors.ErrorSoft)
                    .border(0.7.dp, FrictionColors.Error.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                    .clickable(onClick = onLockdown)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Emergency Lockdown",
                        color = FrictionColors.Error,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Instantly block all distractions",
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = FrictionColors.Error
                )
            }
        }
    }
}

@Composable
private fun CompactActionCell(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(FrictionColors.SurfaceLight.copy(alpha = 0.24f))
            .border(0.7.dp, FrictionColors.GlassBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(FrictionColors.Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FrictionColors.Accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = FrictionColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
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
private fun TodayProtectionGrid(
    blockedAttempts: Int,
    distractionTimeMs: Long,
    focusTimeMs: Long,
    timeSavedMs: Long,
    blockedChange: Int,
    distractionChange: Int,
    focusChange: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Block,
                title = "Blocked Attempts",
                value = blockedAttempts.toString(),
                deltaText = deltaText(blockedChange, "vs yesterday"),
                accent = FrictionColors.Accent
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.HourglassTop,
                title = "Distraction Time",
                value = formatDuration(distractionTimeMs),
                deltaText = deltaText(-distractionChange, "vs yesterday"),
                accent = FrictionColors.Warning
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Timer,
                title = "Focus Time",
                value = formatDuration(focusTimeMs),
                deltaText = deltaText(focusChange, "vs yesterday"),
                accent = FrictionColors.Accent
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.CalendarMonth,
                title = "Time Saved",
                value = formatDuration(timeSavedMs),
                deltaText = "This session",
                accent = FrictionColors.Accent
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    deltaText: String,
    accent: Color
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 22.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.88f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                color = FrictionColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
            Text(
                text = value,
                color = FrictionColors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = deltaText,
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ProtectedAppsRow(
    protectedApps: List<AppUsageData>,
    totalProtected: Int,
    onManage: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        protectedApps.take(3).forEach { app ->
            ProtectedAppCard(
                app = app,
                modifier = Modifier.weight(1f)
            )
        }
        ViewAllProtectedCard(
            totalProtected = totalProtected,
            modifier = Modifier.weight(1f),
            onClick = onManage
        )
    }
}

@Composable
private fun ProtectedAppCard(
    app: AppUsageData,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val iconBitmap = remember(app.packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(app.packageName)
                .toBitmap(width = 128, height = 128)
                .asImageBitmap()
        }.getOrNull()
    }
    val statusLabel = when {
        app.appName.contains("youtube", ignoreCase = true) -> "Reels Only"
        else -> "Fully Blocked"
    }
    val statusColor = if (statusLabel == "Reels Only") FrictionColors.Warning else FrictionColors.Accent

    GlassCard(
        modifier = modifier,
        cornerRadius = 22.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.88f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(FrictionColors.Accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = FrictionColors.Accent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(24.dp))
                        .background(FrictionColors.SurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = app.appName,
                            modifier = Modifier.size(54.dp)
                        )
                    } else {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            color = FrictionColors.Accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        )
                    }
                }
            }

            Text(
                text = app.appName,
                color = FrictionColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusLabel,
                color = statusColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(100.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun ViewAllProtectedCard(
    totalProtected: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 22.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.88f),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(FrictionColors.Accent.copy(alpha = 0.10f))
                        .border(1.dp, FrictionColors.Accent.copy(alpha = 0.26f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = FrictionColors.Accent,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
            Text(
                text = "All Apps",
                color = FrictionColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (totalProtected > 0) "$totalProtected selected" else "Select or unselect",
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun StudyYoutubeCard(
    enabled: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.90f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(FrictionColors.Accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.School,
                        contentDescription = null,
                        tint = FrictionColors.Accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Study YouTube Mode",
                        color = FrictionColors.Accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Block distractions on YouTube except educational content.",
                        color = FrictionColors.TextSecondary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }

            Switch(
                checked = enabled,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FrictionColors.Accent,
                    uncheckedThumbColor = FrictionColors.TextMuted,
                    uncheckedTrackColor = FrictionColors.SurfaceElevated
                )
            )
        }
    }
}

@Composable
private fun FocusStreakCard(
    streakDays: Int,
    timeSavedMs: Long
) {
    val activeBars = streakDays.coerceIn(1, 7)
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 26.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.90f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Focus Streak",
                    color = FrictionColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Keep it going!",
                    color = FrictionColors.TextSecondary,
                    fontSize = 14.sp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(FrictionColors.Accent.copy(alpha = 0.08f))
                        .border(3.dp, FrictionColors.Accent.copy(alpha = 0.65f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.Whatshot,
                            contentDescription = null,
                            tint = FrictionColors.Accent,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = streakDays.coerceAtLeast(0).toString(),
                            color = FrictionColors.TextPrimary,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Days",
                            color = FrictionColors.TextSecondary,
                            fontSize = 15.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        dayLabels.forEachIndexed { index, label ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val barHeight by animateFloatAsState(
                                    targetValue = if (index < activeBars) 1f else 0.44f,
                                    animationSpec = tween(500),
                                    label = "streak-bar"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((52 * barHeight).dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (index < activeBars) {
                                                Brush.verticalGradient(
                                                    listOf(
                                                        FrictionColors.Accent.copy(alpha = 0.78f),
                                                        FrictionColors.Accent
                                                    )
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    listOf(
                                                        FrictionColors.SurfaceElevated,
                                                        FrictionColors.SurfaceLight
                                                    )
                                                )
                                            }
                                        )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = label,
                                    color = FrictionColors.TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Best streak: ${streakDays.coerceAtLeast(0)} days",
                            color = FrictionColors.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${formatDuration(timeSavedMs)} saved",
                            color = FrictionColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickControlGrid(
    onProtectedApps: () -> Unit,
    onReelsShield: () -> Unit,
    onStudy: () -> Unit,
    onAppLimits: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickControlCard(
                modifier = Modifier.weight(1f),
                title = "Protected Apps",
                subtitle = "Manage blocking",
                icon = Icons.Rounded.Security,
                onClick = onProtectedApps
            )
            QuickControlCard(
                modifier = Modifier.weight(1f),
                title = "Reels Shield",
                subtitle = "Block reels & shorts",
                icon = Icons.Rounded.Block,
                onClick = onReelsShield
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickControlCard(
                modifier = Modifier.weight(1f),
                title = "Study Tasks",
                subtitle = "Unlock through PYQ",
                icon = Icons.Rounded.School,
                onClick = onStudy
            )
            QuickControlCard(
                modifier = Modifier.weight(1f),
                title = "App Limits",
                subtitle = "Set time windows",
                icon = Icons.Rounded.HourglassTop,
                onClick = onAppLimits
            )
        }
    }
}

@Composable
private fun QuickControlCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 22.dp,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.88f),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(FrictionColors.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = FrictionColors.Accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = title,
                color = FrictionColors.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun EmergencyLockdownCard(
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        backgroundColor = FrictionColors.ErrorSoft,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(FrictionColors.Error.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = FrictionColors.Error,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Emergency Lockdown",
                        color = FrictionColors.Error,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Instantly block all distractions",
                        color = FrictionColors.TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = null,
                tint = FrictionColors.Error,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "0m"
    }
}

private fun formatScheduleClock(hour: Int, minute: Int): String {
    val marker = if (hour >= 12) "PM" else "AM"
    val normalizedHour = when (val h = hour % 12) {
        0 -> 12
        else -> h
    }
    return String.format(Locale.US, "%d:%02d %s", normalizedHour, minute, marker)
}

private fun deltaText(value: Int, suffix: String): String {
    val arrow = if (value >= 0) "↑" else "↓"
    return "$arrow ${kotlin.math.abs(value)}% $suffix"
}

private fun percentageDelta(current: Long, previous: Long): Int {
    if (previous <= 0L) return if (current > 0L) 100 else 0
    val delta = ((current - previous).toDouble() / previous.toDouble()) * 100.0
    return delta.toInt()
}

private fun yesterdayDistractionMs(weeklyUsage: Map<String, List<AppUsageData>>): Long {
    return weeklyUsage[yesterdayKey()]
        ?.filter { it.isBlacklisted }
        ?.sumOf { it.usageTimeMs }
        ?: 0L
}

private fun yesterdayBlockedCount(weeklyUsage: Map<String, List<AppUsageData>>): Long {
    return weeklyUsage[yesterdayKey()]
        ?.filter { it.isBlacklisted }
        ?.sumOf { it.sessionCount.toLong() }
        ?: 0L
}

private fun yesterdayFocusTimeMs(weeklyUsage: Map<String, List<AppUsageData>>): Long {
    val apps = weeklyUsage[yesterdayKey()].orEmpty()
    val total = apps.sumOf { it.usageTimeMs }
    val distraction = apps.filter { it.isBlacklisted }.sumOf { it.usageTimeMs }
    return (total - distraction).coerceAtLeast(0L)
}

private fun yesterdayKey(): String {
    val calendar = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}
