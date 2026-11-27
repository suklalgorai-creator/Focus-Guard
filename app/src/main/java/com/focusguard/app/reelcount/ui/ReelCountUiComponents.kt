package com.focusguard.app.reelcount.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.reelcount.domain.ReelCountDailyStats
import com.focusguard.app.reelcount.domain.ReelCountLimitState
import com.focusguard.app.reelcount.domain.ReelCountLimitStatus
import com.focusguard.app.reelcount.domain.ReelCountSettings
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.SecondaryButton
import com.focusguard.app.ui.components.StatItem
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.theme.GlassTokens

/**
 * Compact Reel Count summary card for TodayHubScreen.
 */
@Composable
fun ReelCountTodayCard(
    app: FocusGuardApp,
    onOpenSettings: () -> Unit
) {
    val settings by app.reelCountRepository.settingsFlow.collectAsStateWithLifecycle()
    val stats by app.reelCountRepository.todayStatsFlow.collectAsStateWithLifecycle(
        initialValue = ReelCountDailyStats(dateKey = app.reelCountRepository.currentDateKey())
    )
    val accent = GlassTokens.Accents.ReelCount
    val hasLimit = settings.limitEnabled && settings.dailyLimit > 0
    val remaining = if (hasLimit) (settings.dailyLimit - stats.viewedCount).coerceAtLeast(0) else null
    val progress = if (hasLimit) {
        (stats.viewedCount.toFloat() / settings.dailyLimit.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings),
        cornerRadius = 18.dp,
        isActive = settings.enabled,
        accentColor = accent,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Reel Count",
                        color = FrictionColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Text(
                    text = if (settings.enabled) "Active" else "Off",
                    color = if (settings.enabled) accent else FrictionColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!settings.enabled && stats.viewedCount == 0 && stats.blockedAttempts == 0) {
                Text(
                    text = "Track Instagram Reels viewed and set a daily limit in You.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatItem(
                        label = if (hasLimit) "Viewed / ${settings.dailyLimit}" else "Viewed today",
                        value = stats.viewedCount.toString(),
                        icon = Icons.Rounded.Visibility,
                        modifier = Modifier.weight(1f),
                        accent = accent
                    )
                    StatItem(
                        label = "Blocked attempts",
                        value = stats.blockedAttempts.toString(),
                        icon = Icons.Rounded.Block,
                        modifier = Modifier.weight(1f),
                        accent = FrictionColors.Error
                    )
                }

                if (hasLimit) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(FrictionColors.SurfaceElevated)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(accent)
                            )
                        }
                        Text(
                            text = if (remaining != null && remaining > 0) {
                                "$remaining Reels remaining before today's check-in"
                            } else {
                                "Daily limit reached"
                            },
                            color = FrictionColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * ShieldHubScreen status card distinguishing Focus Guard's independent protection
 * from Reel Count's daily-limit policy.
 */
@Composable
fun ReelCountShieldCard(app: FocusGuardApp) {
    val settings by app.reelCountRepository.settingsFlow.collectAsStateWithLifecycle()
    val limitState by app.reelCountRepository.todayLimitStateFlow.collectAsStateWithLifecycle(
        initialValue = ReelCountLimitState(dateKey = app.reelCountRepository.currentDateKey())
    )
    val accent = GlassTokens.Accents.ReelCount
    val focusGuardReelsBlocked = app.prefs.isGuardActiveNow() &&
        app.prefs.blockedContentSurfaces.contains(com.focusguard.app.persistence.FocusGuardPrefs.SURFACE_INSTAGRAM_REELS)

    val reelCountPolicyLabel = when {
        !settings.enabled -> "Reel Count disabled"
        !settings.limitEnabled -> "Tracking only (no daily limit)"
        limitState.status == ReelCountLimitStatus.BLOCK_TODAY -> "Blocking Reels until midnight"
        limitState.status == ReelCountLimitStatus.CONTINUE_TODAY -> "Limit crossed • Watching allowed today"
        limitState.status == ReelCountLimitStatus.AWAITING_CHOICE -> "Daily limit reached • Choice pending"
        else -> "Monitoring (${settings.dailyLimit}/day limit)"
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        isActive = settings.enabled && limitState.status == ReelCountLimitStatus.BLOCK_TODAY,
        accentColor = accent,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.92f)
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
                Text(
                    text = "Reel Count Protection",
                    color = FrictionColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = if (settings.enabled) "ON" else "OFF",
                    color = if (settings.enabled) accent else FrictionColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            Text(
                text = "Daily Limit Policy: $reelCountPolicyLabel",
                color = FrictionColors.TextSecondary,
                fontSize = 13.sp
            )
            Text(
                text = if (focusGuardReelsBlocked) {
                    "Focus Guard Reels Shield is also active and takes priority."
                } else {
                    "Focus Guard schedules and Strict Mode remain independent."
                },
                color = FrictionColors.TextMuted,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * ProgressHubScreen historical Reels insights card backed by Room data.
 */
@Composable
fun ReelCountProgressCard(app: FocusGuardApp) {
    val recentStats by app.reelCountRepository.recentStatsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val limitDaysCount by app.reelCountRepository.limitReachedDaysCountFlow.collectAsStateWithLifecycle(initialValue = 0)
    val accent = GlassTokens.Accents.ReelCount

    val last7Days = recentStats.take(7)
    val weeklyViewed = last7Days.sumOf { it.viewedCount }
    val weeklyBlocked = last7Days.sumOf { it.blockedAttempts }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        accentColor = accent,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Reels Insights (Last 7 Days)",
                color = FrictionColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            if (last7Days.isEmpty()) {
                Text(
                    text = "No Reels history recorded yet.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 13.sp
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatItem(
                        label = "Reels viewed",
                        value = weeklyViewed.toString(),
                        icon = Icons.Rounded.Visibility,
                        modifier = Modifier.weight(1f),
                        accent = accent
                    )
                    StatItem(
                        label = "Blocked",
                        value = weeklyBlocked.toString(),
                        icon = Icons.Rounded.Block,
                        modifier = Modifier.weight(1f),
                        accent = FrictionColors.Error
                    )
                }
                Text(
                    text = "Limit reached on $limitDaysCount recorded day(s).",
                    color = FrictionColors.TextSecondary,
                    fontSize = 12.sp
                )
                last7Days.take(5).forEach { day ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = day.dateKey,
                            color = FrictionColors.TextMuted,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "${day.viewedCount} viewed • ${day.blockedAttempts} blocked",
                            color = FrictionColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * YouHubScreen Settings card for configuring Reel Count.
 */
@Composable
fun ReelCountSettingsCard(app: FocusGuardApp) {
    val context = LocalContext.current
    val settings by app.reelCountRepository.settingsFlow.collectAsStateWithLifecycle()
    val overlayPermitted = remember(settings.floatingCounterEnabled, settings.enabled) {
        PermissionMonitor(context).isOverlayPermitted()
    }
    val accent = GlassTokens.Accents.ReelCount

    var customLimitText by remember(settings.dailyLimit) {
        mutableStateOf(settings.dailyLimit.toString())
    }
    var customLimitError by remember { mutableStateOf<String?>(null) }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        isActive = settings.enabled,
        accentColor = accent,
        backgroundColor = FrictionColors.Surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Reel Count",
                        color = FrictionColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "Track Instagram Reels viewed and set a daily limit.",
                        color = FrictionColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = settings.enabled,
                    onCheckedChange = { app.reelCountCoordinator.onReelCountMasterToggled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accent
                    )
                )
            }

            if (settings.enabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Floating Counter",
                            color = FrictionColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Display compact pill while Instagram is open.",
                            color = FrictionColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = settings.floatingCounterEnabled,
                        onCheckedChange = { app.reelCountRepository.setFloatingCounterEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent
                        )
                    )
                }

                if (settings.floatingCounterEnabled && !overlayPermitted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(FrictionColors.WarningSoft)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = FrictionColors.Warning,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overlay permission missing",
                                color = FrictionColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Tracking still works, but the floating counter needs Display over other apps.",
                                color = FrictionColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    SecondaryButton(
                        text = "Grant Overlay Permission",
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Daily Limit",
                            color = FrictionColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (settings.limitEnabled) {
                                "Ask at ${settings.dailyLimit} Reels • Resets at midnight"
                            } else {
                                "Unlimited (tracking only)"
                            },
                            color = FrictionColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = settings.limitEnabled,
                        onCheckedChange = { app.reelCountRepository.setLimitEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accent
                        )
                    )
                }

                if (settings.limitEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ReelCountSettings.PRESET_LIMITS.forEach { preset ->
                            val selected = settings.dailyLimit == preset
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (selected) accent.copy(alpha = 0.22f)
                                        else FrictionColors.SurfaceElevated
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) accent else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        customLimitError = null
                                        customLimitText = preset.toString()
                                        app.reelCountRepository.setDailyLimit(preset)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = preset.toString(),
                                    color = if (selected) accent else FrictionColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customLimitText,
                            onValueChange = { input ->
                                val digits = input.filter { it.isDigit() }.take(3)
                                customLimitText = digits
                                val parsed = digits.toIntOrNull()
                                if (parsed == null || parsed !in ReelCountSettings.MIN_DAILY_LIMIT..ReelCountSettings.MAX_DAILY_LIMIT) {
                                    customLimitError = "Enter ${ReelCountSettings.MIN_DAILY_LIMIT}-${ReelCountSettings.MAX_DAILY_LIMIT}"
                                } else {
                                    customLimitError = null
                                    app.reelCountRepository.setDailyLimit(parsed)
                                }
                            },
                            label = { Text("Custom limit") },
                            singleLine = true,
                            isError = customLimitError != null,
                            supportingText = customLimitError?.let { msg -> @Composable { Text(msg) } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
