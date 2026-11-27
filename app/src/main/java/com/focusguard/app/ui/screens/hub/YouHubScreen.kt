package com.focusguard.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.presentation.profile.ProfileViewModel
import com.focusguard.app.reelcount.ui.ReelCountSettingsCard
import com.focusguard.app.ui.components.FocusGuardIconId
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.SecondaryButton
import com.focusguard.app.ui.components.StatItem
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.theme.GlassTokens

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
    val profile = profileViewModel.profileFlow.collectAsStateWithLifecycle().value
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
                accentColor = GlassTokens.Accents.StudyFlow,
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
                accent = FocusGuardIconId.Account.accentColor,
                iconId = FocusGuardIconId.Account,
                onClick = onOpenProfileDetails
            )
        }

        item {
            HubActionCard(
                title = "Permission health",
                subtitle = "Accessibility, overlay, usage, and battery.",
                icon = Icons.Rounded.QueryStats,
                accent = FocusGuardIconId.Permissions.accentColor,
                iconId = FocusGuardIconId.Permissions,
                onClick = onOpenPermissionHealth
            )
        }

        item {
            ReelCountSettingsCard(app = app)
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
                        fontSize = 13.sp,
                        lineHeight = 18.sp
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
                                        data = Uri.parse("package:${context.packageName}")
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
