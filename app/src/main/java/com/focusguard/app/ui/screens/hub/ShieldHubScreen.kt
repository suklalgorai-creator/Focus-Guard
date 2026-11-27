package com.focusguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.domain.settings.FocusSettings
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.presentation.settings.SettingsViewModel
import com.focusguard.app.ui.components.FocusGuardIconId
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.GradientButton
import com.focusguard.app.ui.components.SecondaryButton
import com.focusguard.app.ui.components.SectionHeader
import com.focusguard.app.ui.components.StatItem
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.theme.GlassTokens

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
    val lifecycleOwner = LocalLifecycleOwner.current

    var accessibilityEnabled by remember { mutableStateOf(permissionMonitor.isAccessibilityEnabled()) }
    var overlayEnabled by remember { mutableStateOf(permissionMonitor.isOverlayPermitted()) }
    var usageStatsEnabled by remember { mutableStateOf(permissionMonitor.isUsageStatsPermitted()) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityEnabled = permissionMonitor.isAccessibilityEnabled()
                overlayEnabled = permissionMonitor.isOverlayPermitted()
                usageStatsEnabled = permissionMonitor.isUsageStatsPermitted()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                isActive = guardArmed,
                accentColor = GlassTokens.Accents.AppBlocking,
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
                accent = FocusGuardIconId.Blocklist.accentColor,
                iconId = FocusGuardIconId.Blocklist,
                onClick = onOpenApps
            )
        }

        item {
            HubActionCard(
                title = "Schedules",
                subtitle = "Set focus windows and strict sessions.",
                icon = Icons.Rounded.Schedule,
                accent = FocusGuardIconId.Schedule.accentColor,
                iconId = FocusGuardIconId.Schedule,
                onClick = onOpenSchedule
            )
        }

        item {
            HubActionCard(
                title = "Protection Setup",
                subtitle = "Check permission health before relying on the blocker.",
                icon = Icons.Rounded.AdminPanelSettings,
                accent = FocusGuardIconId.Permissions.accentColor,
                iconId = FocusGuardIconId.Permissions,
                onClick = onOpenPermissions
            )
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                accentColor = GlassTokens.Accents.ProgressStats,
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
                isActive = uninstallProtectionReady,
                accentColor = GlassTokens.Accents.AccountabilityLock,
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
