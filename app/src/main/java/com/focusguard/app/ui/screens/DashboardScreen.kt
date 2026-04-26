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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.focusguard.app.presentation.settings.SettingsViewModel
import com.focusguard.app.presentation.usage.UsageStatsViewModel
import com.focusguard.app.ui.components.PremiumIconButton
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
    val topDistractingApps = remember(today.allApps) {
        today.allApps.filter { it.isBlacklisted }.take(3)
    }
    val yesterdayDistractionMs = usageState.weeklyUsage[yesterdayKey()]
        ?.filter { it.isBlacklisted }
        ?.sumOf { it.usageTimeMs }
        ?: 0L
    val insightText = buildInsightText(
        todayDistractionMs = today.distractionTimeMs,
        yesterdayDistractionMs = yesterdayDistractionMs,
        timeSavedMs = today.timeSavedMs
    )

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
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(260)) +
                slideInVertically(
                    animationSpec = tween(280, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 8 }
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HeaderSection(
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        onOpenMenu = onOpenMenu,
                        onNavigateProfile = onNavigateProfile
                    )
                }

                item {
                    StatsCard(
                        totalScreenTimeMs = today.totalUsageTimeMs,
                        distractionTimeMs = today.distractionTimeMs,
                        timeSavedMs = today.timeSavedMs
                    )
                }

                item {
                    SectionTitle("Top distracting apps")
                }

                if (topDistractingApps.isEmpty()) {
                    item {
                        EmptyDistractionCard()
                    }
                } else {
                    items(topDistractingApps, key = { it.packageName }) { appUsage ->
                        AppUsageItem(app = appUsage)
                    }
                }

                item {
                    InsightBanner(
                        message = insightText,
                        progress = distractionRatio(
                            distractionMs = today.distractionTimeMs,
                            totalMs = today.totalUsageTimeMs
                        ),
                        onOpenStats = onNavigateUsage
                    )
                }

                item {
                    SmallStatsRow(
                        sessionsBlocked = today.sessionsBlocked,
                        focusStreak = focusStreak
                    )
                }

                item {
                    QuickActionStrip(
                        onStartSolving = onNavigatePyq,
                        onBlocks = onNavigateBlacklist,
                        onSchedule = onNavigateSchedule
                    )
                }

                item { Spacer(modifier = Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun HeaderSection(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onOpenMenu: () -> Unit,
    onNavigateProfile: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = greetingText(),
                color = FrictionColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Stay focused today",
                color = FrictionColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PremiumIconButton(
                icon = Icons.Outlined.Menu,
                contentDescription = "Menu",
                onClick = onOpenMenu
            )
            PremiumIconButton(
                icon = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = "Theme",
                onClick = onToggleTheme
            )
            PremiumIconButton(
                icon = Icons.Outlined.Settings,
                contentDescription = "Settings",
                onClick = onNavigateProfile
            )
        }
    }
}

@Composable
fun StatsCard(
    totalScreenTimeMs: Long,
    distractionTimeMs: Long,
    timeSavedMs: Long
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(22.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        FrictionColors.Accent.copy(alpha = 0.22f),
                        FrictionColors.GlassBackground,
                        FrictionColors.Surface.copy(alpha = 0.86f)
                    )
                )
            )
            .border(0.6.dp, FrictionColors.GlassBorder, shape)
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today",
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Your attention report",
                        color = FrictionColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(FrictionColors.AccentSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Bolt,
                        contentDescription = null,
                        tint = FrictionColors.Accent
                    )
                }
            }

            StatMetric(
                icon = Icons.Rounded.PhoneAndroid,
                label = "Total Screen Time",
                valueMs = totalScreenTimeMs,
                color = FrictionColors.TextPrimary
            )
            StatMetric(
                icon = Icons.Rounded.HourglassTop,
                label = "Distraction Time",
                valueMs = distractionTimeMs,
                color = FrictionColors.Error
            )
            StatMetric(
                icon = Icons.Rounded.Savings,
                label = "Time Saved",
                valueMs = timeSavedMs,
                color = FrictionColors.Success
            )
        }
    }
}

@Composable
private fun StatMetric(
    icon: ImageVector,
    label: String,
    valueMs: Long,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = FrictionColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        CountUpTimeText(valueMs = valueMs, color = color)
    }
}

@Composable
private fun CountUpTimeText(valueMs: Long, color: Color) {
    val animatedValue by animateFloatAsState(
        targetValue = valueMs.toFloat(),
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "time-count-up"
    )
    Text(
        text = formatDuration(animatedValue.toLong()),
        color = color,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun AppUsageItem(app: AppUsageData) {
    val context = LocalContext.current
    val iconBitmap = remember(app.packageName) {
        runCatching {
            context.packageManager
                .getApplicationIcon(app.packageName)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FrictionColors.GlassBackground)
            .border(0.5.dp, FrictionColors.GlassBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(FrictionColors.SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = app.appName,
                    modifier = Modifier.size(30.dp)
                )
            } else {
                Text(
                    text = app.appName.take(1).uppercase(),
                    color = FrictionColors.Accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                color = FrictionColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${app.sessionCount} sessions",
                color = FrictionColors.TextMuted,
                fontSize = 12.sp
            )
        }
        Text(
            text = app.formattedTime,
            color = FrictionColors.Error,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun InsightBanner(
    message: String,
    progress: Float,
    onOpenStats: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(FrictionColors.SuccessSoft)
            .border(0.5.dp, FrictionColors.Success.copy(alpha = 0.18f), RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Whatshot,
                contentDescription = null,
                tint = FrictionColors.Success
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                color = FrictionColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
        }
        LinearProgressIndicator(
            progress = progress.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(100.dp)),
            color = FrictionColors.Success,
            trackColor = FrictionColors.SurfaceElevated
        )
        Text(
            text = "Open detailed stats",
            color = FrictionColors.Success,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(FrictionColors.Success.copy(alpha = 0.10f))
                .clickable(onClick = onOpenStats)
                .padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun SmallStatsRow(
    sessionsBlocked: Int,
    focusStreak: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SmallStatPill(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Block,
            label = "Sessions blocked",
            value = sessionsBlocked.toString(),
            color = FrictionColors.Accent
        )
        SmallStatPill(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Whatshot,
            label = "Focus streak",
            value = "${focusStreak}d",
            color = FrictionColors.Warning
        )
    }
}

@Composable
private fun SmallStatPill(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(FrictionColors.GlassBackground)
            .border(0.5.dp, FrictionColors.GlassBorder, RoundedCornerShape(20.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = value, color = FrictionColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(text = label, color = FrictionColors.TextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyDistractionCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(FrictionColors.GlassBackground)
            .border(0.5.dp, FrictionColors.GlassBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(
            text = "No distracting app usage logged today. Clean start.",
            color = FrictionColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun QuickActionStrip(
    onStartSolving: () -> Unit,
    onBlocks: () -> Unit,
    onSchedule: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickAction("Solve", Icons.Rounded.Bolt, onStartSolving, Modifier.weight(1f))
        QuickAction("Blocks", Icons.Rounded.Block, onBlocks, Modifier.weight(1f))
        QuickAction("Schedule", Icons.Rounded.HourglassTop, onSchedule, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(FrictionColors.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = FrictionColors.Accent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = FrictionColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = FrictionColors.TextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp)
    )
}

private fun greetingText(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Late Session"
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000L
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return when {
        hours > 0 -> "${hours}h ${remainingMinutes}m"
        minutes > 0 -> "${minutes}m"
        ms > 0 -> "< 1m"
        else -> "0m"
    }
}

private fun buildInsightText(
    todayDistractionMs: Long,
    yesterdayDistractionMs: Long,
    timeSavedMs: Long
): String {
    if (timeSavedMs > 0L) {
        return "You saved ${formatDuration(timeSavedMs)} today. That is real time back."
    }

    if (yesterdayDistractionMs > 0L && todayDistractionMs < yesterdayDistractionMs) {
        val reduction = (((yesterdayDistractionMs - todayDistractionMs).toFloat() / yesterdayDistractionMs) * 100)
            .toInt()
            .coerceIn(1, 99)
        return "You reduced distraction by $reduction% vs yesterday."
    }

    return "Open less. Solve more. One clean decision at a time."
}

private fun distractionRatio(distractionMs: Long, totalMs: Long): Float {
    if (totalMs <= 0L) return 0f
    return 1f - (distractionMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
}

private fun yesterdayKey(): String {
    val calendar = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -1)
    }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
}
