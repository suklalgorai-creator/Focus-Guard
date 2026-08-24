package com.focusguard.app.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.domain.auth.AuthUiState
import com.focusguard.app.presentation.analytics.AnalyticsViewModel
import com.focusguard.app.presentation.auth.AuthViewModel
import com.focusguard.app.presentation.profile.ProfileViewModel
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.GradientButton
import com.focusguard.app.ui.components.SecondaryButton
import com.focusguard.app.ui.theme.FrictionColors

@Composable
fun ProfileSettingsScreen(
    profileViewModel: ProfileViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as FocusGuardApp
    val profile by profileViewModel.profileFlow.collectAsStateWithLifecycle()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val analyticsViewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModel.factory(app.analyticsRepository)
    )
    val analytics by analyticsViewModel.state.collectAsStateWithLifecycle()
    val daysUntilExam = app.prefs.getDaysUntilExam()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(FrictionColors.Background),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FrictionColors.TextPrimary
                    )
                }
                Column {
                    Text(
                        text = "Profile",
                        color = FrictionColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Exam settings and focus preferences",
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }

        item {
            AccountCard(
                state = authState,
                onSignIn = authViewModel::showLogin,
                onSignOut = authViewModel::signOut
            )
        }

        item {
            GlassSettingsCard(
                title = "Exam",
                value = profile.exam.uppercase(),
                subtitle = profile.preferredSubjects.joinToString(", "),
                icon = Icons.Outlined.School
            )
        }

        item {
            GlassSettingsCard(
                title = "Target date",
                value = if (daysUntilExam >= 0) "$daysUntilExam days" else "Not set",
                subtitle = "Countdown keeps the app behavior exam-focused.",
                icon = Icons.Outlined.CalendarMonth
            )
        }

        item {
            GlassSettingsCard(
                title = "Daily goal",
                value = "${analytics.todayAttempts} solved today",
                subtitle = "Dynamic goal adjusts from your consistency and risk level.",
                icon = Icons.Outlined.TrackChanges
            )
        }

        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                backgroundColor = FrictionColors.GlassBackground
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = null,
                            tint = FrictionColors.Accent
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(
                                text = "Notifications",
                                color = FrictionColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Smart nudges are controlled by Android notification permission.",
                                color = FrictionColors.TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                    GradientButton(
                        text = "Notification settings",
                        onClick = {
                            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            } else {
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    state: AuthUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    val user = state.user
    val displayName = user?.name?.takeIf { it.isNotBlank() } ?: "Signed in"
    val email = user?.email?.takeIf { it.isNotBlank() }
    val isGuest = user == null && state.hasSkippedLogin

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    tint = FrictionColors.Accent
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = "Account",
                        color = FrictionColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when {
                            user != null -> displayName
                            !state.isConfigured -> "Sync unavailable"
                            isGuest -> "Guest mode"
                            else -> "Not signed in"
                        },
                        color = FrictionColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            email != null -> email
                            user != null -> "Cloud sync is connected."
                            !state.isConfigured -> "Auth is not configured for this build."
                            else -> "Local data stays on this phone until you sign in."
                        },
                        color = FrictionColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when {
                user != null -> {
                    SecondaryButton(
                        text = "Sign out",
                        leadingIcon = Icons.Outlined.Logout,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onSignOut
                    )
                }
                state.isConfigured -> {
                    GradientButton(
                        text = "Sign in",
                        leadingIcon = Icons.Outlined.Login,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading,
                        onClick = onSignIn
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassSettingsCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = FrictionColors.Accent
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp)
            ) {
                Text(
                    text = title,
                    color = FrictionColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    color = FrictionColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = FrictionColors.TextMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
