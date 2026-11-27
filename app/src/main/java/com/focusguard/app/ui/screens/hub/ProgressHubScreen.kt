package com.focusguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.presentation.usage.UsageStatsViewModel
import com.focusguard.app.reelcount.ui.ReelCountProgressCard
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.GradientButton
import com.focusguard.app.ui.components.SecondaryButton
import com.focusguard.app.ui.components.SectionHeader
import com.focusguard.app.ui.components.StatItem
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.theme.GlassTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

        val hasProgressData = usageState.today.timeSavedMs > 0L ||
            usageState.today.sessionsBlocked > 0 ||
            weeklyBlockedMinutes > 0L ||
            pyqsToday > 0

        if (!hasProgressData) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    accentColor = GlassTokens.Accents.ProgressStats,
                    backgroundColor = FrictionColors.GlassBackground
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No activity yet",
                            color = FrictionColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Your progress will appear here as you use Focus Guard.",
                            color = FrictionColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        } else {
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
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    accentColor = GlassTokens.Accents.ProgressStats,
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
                            label = "Streak",
                            value = "${streakDays}d"
                        )
                        ProgressInsightRow(
                            label = "Top trigger",
                            value = topTrigger?.appName ?: "No clear trigger yet"
                        )
                        ProgressInsightRow(
                            label = "Distracting time",
                            value = if (weeklyBlockedMinutes > 0L) "${weeklyBlockedMinutes}m this week" else "Calm week so far"
                        )
                    }
                }
            }
        }

        item {
            if (app.reelCountRepository.currentSettings().enabled) {
                ReelCountProgressCard(app = app)
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
