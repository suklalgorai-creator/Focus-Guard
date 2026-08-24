package com.focusguard.app.ui.screens

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.antibypass.PermissionMonitor
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.GradientButton
import com.focusguard.app.ui.components.PremiumIconButton
import com.focusguard.app.ui.components.SectionHeader
import com.focusguard.app.ui.theme.FrictionColors
import kotlinx.coroutines.delay

@Composable
fun PermissionsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val permissionMonitor = remember { PermissionMonitor(context) }

    var accessibilityEnabled by remember { mutableStateOf(false) }
    var overlayEnabled by remember { mutableStateOf(false) }
    var usageStatsEnabled by remember { mutableStateOf(false) }
    var batteryProtected by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            accessibilityEnabled = permissionMonitor.isAccessibilityEnabled()
            overlayEnabled = permissionMonitor.isOverlayPermitted()
            usageStatsEnabled = permissionMonitor.isUsageStatsPermitted()
            batteryProtected = isIgnoringBatteryOptimizations(context)
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            delay(2_000)
        }
    }

    val allCoreReady = accessibilityEnabled

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FrictionColors.Background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "App Access",
                        color = FrictionColors.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Turn on the blocker basics.",
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                PremiumIconButton(
                    icon = Icons.Rounded.AdminPanelSettings,
                    contentDescription = "Back",
                    onClick = onBack
                )
            }
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                isActive = allCoreReady
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
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (allCoreReady) FrictionColors.AccentSoft
                                    else FrictionColors.WarningSoft
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (allCoreReady) FrictionColors.Accent else FrictionColors.Warning
                            )
                        }
                        Column {
                            Text(
                                text = if (allCoreReady) "Protection ready" else "Protection incomplete",
                                color = FrictionColors.TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (allCoreReady) {
                                    "Blocking can return distractions to Home. Overlay adds the challenge screen."
                                } else {
                                    "Turn on Accessibility first."
                                },
                                color = FrictionColors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }

                    StatusExplainRow(
                        label = "Accessibility",
                        ready = accessibilityEnabled
                    )
                    StatusExplainRow(
                        label = "Overlay screen",
                        ready = overlayEnabled
                    )
                    StatusExplainRow(
                        label = "Usage backup",
                        ready = usageStatsEnabled
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "Main Access",
                subtitle = "Accessibility starts the real blocker."
            )
        }

        item {
            PermissionInfoCard(
                icon = Icons.Outlined.Accessibility,
                title = "Accessibility Service",
                summary = "Detects blocked apps and sends them Home.",
                isGranted = accessibilityEnabled,
                isRequired = true,
                buttonLabel = "Turn on Accessibility",
                onClick = {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }

        item {
            SectionHeader(
                title = "Recommended",
                subtitle = "Better overlay UI, backup detection, and reliability."
            )
        }

        item {
            PermissionInfoCard(
                icon = Icons.Outlined.Layers,
                title = "Overlay Screen",
                summary = "Shows PYQ challenges. Optional but better.",
                isGranted = overlayEnabled,
                buttonLabel = "Turn on Overlay",
                onClick = {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            )
        }

        item {
            PermissionInfoCard(
                icon = Icons.Outlined.QueryStats,
                title = "Usage Access",
                summary = "Backup detection and cleaner analytics.",
                isGranted = usageStatsEnabled,
                buttonLabel = "Turn on Usage",
                onClick = {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )
        }

        item {
            PermissionInfoCard(
                icon = Icons.Outlined.BatteryAlert,
                title = "Battery Optimization",
                summary = "Keeps background monitoring alive.",
                isGranted = batteryProtected,
                buttonLabel = "Open Battery",
                onClick = {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            )
        }

        item {
            PermissionInfoCard(
                icon = Icons.Outlined.Autorenew,
                title = "Auto Start / Auto Launch",
                summary = "Restarts the guard after reboot.",
                isGranted = false,
                buttonLabel = "Open Auto Start",
                onClick = {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                    openAutoStartSettings(context)
                }
            )
        }

        item {
            PermissionInfoCard(
                icon = Icons.Outlined.Notifications,
                title = "Notifications",
                summary = "Fallback and permission alerts.",
                isGranted = notificationsEnabled,
                buttonLabel = "Open Notifications",
                onClick = {
                    FocusGuardApp.instance.prefs.allowPermissionSetupWindow()
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun PermissionInfoCard(
    icon: ImageVector,
    title: String,
    summary: String,
    isGranted: Boolean,
    isRequired: Boolean = false,
    buttonLabel: String,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        isActive = isGranted
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
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isGranted) FrictionColors.AccentSoft
                                else FrictionColors.WarningSoft
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isGranted) FrictionColors.Accent else FrictionColors.Warning
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            color = FrictionColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = summary,
                            color = FrictionColors.TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
                StatusPill(isGranted = isGranted, isRequired = isRequired)
            }

            GradientButton(
                text = buttonLabel,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusPill(isGranted: Boolean, isRequired: Boolean) {
    val label = when {
        isGranted -> "Ready"
        isRequired -> "Required"
        else -> "Optional"
    }
    val color = when {
        isGranted -> FrictionColors.Success
        isRequired -> FrictionColors.Warning
        else -> FrictionColors.TextMuted
    }
    val background = when {
        isGranted -> FrictionColors.SuccessSoft
        isRequired -> FrictionColors.WarningSoft
        else -> FrictionColors.SurfaceElevated
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatusExplainRow(label: String, ready: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = FrictionColors.TextSecondary,
            fontSize = 13.sp
        )
        Text(
            text = if (ready) "On" else "Off",
            color = if (ready) FrictionColors.Success else FrictionColors.Warning,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
}

private fun openAutoStartSettings(context: android.content.Context) {
    val intents = listOf(
        Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
        },
        Intent().apply {
            component = ComponentName(
                "com.oneplus.security",
                "com.oneplus.security.chainlaunch.view.AllowAutoDetectActivity"
            )
        },
        Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.startupapp.StartupAppListActivity"
            )
        }
    )

    val opened = intents.any { intent ->
        runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    if (!opened) {
        Toast.makeText(
            context,
            "Phone Manager me Auto Launch ya Auto Start dhundo.",
            Toast.LENGTH_LONG
        ).show()
    }
}
