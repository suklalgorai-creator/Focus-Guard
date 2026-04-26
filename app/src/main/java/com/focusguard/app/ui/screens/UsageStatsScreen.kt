package com.focusguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.focusguard.app.domain.AppUsageData
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.presentation.analytics.AnalyticsViewModel
import com.focusguard.app.presentation.usage.UsageStatsViewModel
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.theme.FrictionColors

/**
 * Usage Statistics Screen — Modern Redesign.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    usageStatsViewModel: UsageStatsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as FocusGuardApp
    val analyticsViewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModel.factory(app.analyticsRepository)
    )
    val pyqAnalyticsState by analyticsViewModel.state.collectAsStateWithLifecycle()
    val usageState by usageStatsViewModel.state.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val tabs = listOf("Today", "This Week")

    val dailyStats = usageState.today.allApps
    val weeklyStats = usageState.weeklyUsage
    val blacklistedToday = dailyStats.filter { it.isBlacklisted }
    val totalTodayMs = usageState.today.totalUsageTimeMs
    val blacklistedTotalMs = usageState.today.distractionTimeMs

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usage Stats", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = FrictionColors.TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FrictionColors.Background,
                    titleContentColor = FrictionColors.TextPrimary
                )
            )
        },
        containerColor = FrictionColors.Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PyqAnalyticsPanel(state = pyqAnalyticsState)
            }

            if (pyqAnalyticsState.errorMessage != null) {
                item {
                    Text(
                        text = pyqAnalyticsState.errorMessage ?: "",
                        color = FrictionColors.Warning,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            if (usageState.errorMessage != null) {
                item {
                    Text(
                        text = usageState.errorMessage ?: "",
                        color = FrictionColors.Error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // Screen time header
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    backgroundColor = FrictionColors.GlassBackground
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Screen time today",
                            color = FrictionColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            formatTime(totalTodayMs),
                            color = FrictionColors.TextPrimary,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        )
                        if (blacklistedTotalMs > 0) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(FrictionColors.ErrorSoft)
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    "${formatTime(blacklistedTotalMs)} on blocked apps",
                                    color = FrictionColors.Error,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MiniStatCard(
                                title = "Saved",
                                value = formatTime(usageState.today.timeSavedMs),
                                modifier = Modifier.weight(1f)
                            )
                            MiniStatCard(
                                title = "Blocked",
                                value = usageState.today.sessionsBlocked.toString(),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Tab selector
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    backgroundColor = FrictionColors.GlassBackground
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) FrictionColors.SurfaceElevated
                                        else Color.Transparent
                                    )
                                    .clickable { selectedTab = index }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    title,
                                    color = if (isSelected) FrictionColors.TextPrimary
                                            else FrictionColors.TextMuted,
                                    fontWeight = if (isSelected) FontWeight.SemiBold
                                                 else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    if (usageState.isLoading) {
                        item {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = FrictionColors.Accent,
                                trackColor = FrictionColors.SurfaceElevated
                            )
                        }
                    }

                    if (blacklistedToday.isNotEmpty()) {
                        item {
                            Text(
                                "Blocked Apps",
                                color = FrictionColors.Accent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                            )
                        }
                        items(blacklistedToday) { app ->
                            UsageBarItem(
                                app = app,
                                maxMs = dailyStats.maxOfOrNull { it.usageTimeMs } ?: 1L,
                                isBlacklisted = true
                            )
                        }
                    }

                    item {
                        Text(
                            "Top Apps",
                            color = FrictionColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }
                    items(dailyStats.filter { !it.isBlacklisted }.take(20)) { app ->
                        UsageBarItem(
                            app = app,
                            maxMs = dailyStats.maxOfOrNull { it.usageTimeMs } ?: 1L,
                            isBlacklisted = false
                        )
                    }
                }
                1 -> {
                    val sortedDays = weeklyStats.entries.sortedBy { it.key }
                    val maxDayMs = sortedDays.maxOfOrNull { entry ->
                        entry.value.sumOf { it.usageTimeMs }
                    } ?: 1L

                    item {
                        Text(
                            "Daily Breakdown",
                            color = FrictionColors.TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }

                    items(sortedDays) { (dayKey, apps) ->
                        val dayTotal = apps.sumOf { it.usageTimeMs }
                        val blacklistedDayTotal = apps.filter { it.isBlacklisted }.sumOf { it.usageTimeMs }
                        WeekDayBar(dayKey, dayTotal, blacklistedDayTotal, maxDayMs)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FrictionColors.SurfaceElevated)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                color = FrictionColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = FrictionColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UsageBarItem(app: AppUsageData, maxMs: Long, isBlacklisted: Boolean) {
    val fraction = (app.usageTimeMs.toFloat() / maxMs.toFloat()).coerceIn(0.03f, 1f)
    val barColor = if (isBlacklisted) FrictionColors.Accent else FrictionColors.Success

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        cornerRadius = 16.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    app.appName,
                    color = if (isBlacklisted) FrictionColors.Accent else FrictionColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    app.formattedTime,
                    color = if (isBlacklisted) FrictionColors.Accent else FrictionColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FrictionColors.SurfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }
        }
    }
}

@Composable
private fun WeekDayBar(dayKey: String, totalMs: Long, blacklistedMs: Long, maxMs: Long) {
    val fraction = (totalMs.toFloat() / maxMs.toFloat()).coerceIn(0.03f, 1f)
    val blacklistedFraction = if (totalMs > 0) (blacklistedMs.toFloat() / totalMs.toFloat()) else 0f

    val dayName = try {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val dayFormat = java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.US)
        dayFormat.format(dateFormat.parse(dayKey)!!)
    } catch (e: Exception) { dayKey }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        cornerRadius = 16.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    dayName,
                    color = FrictionColors.TextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Text(
                    formatTime(totalMs),
                    color = FrictionColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(FrictionColors.SurfaceElevated)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(FrictionColors.Success)
                )
                if (blacklistedMs > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction * blacklistedFraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(FrictionColors.Accent)
                    )
                }
            }
            if (blacklistedMs > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "${formatTime(blacklistedMs)} on blocked apps",
                    color = FrictionColors.Accent,
                    fontSize = 11.sp
                )
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val minutes = ms / (1000 * 60)
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 -> "${hours}h ${remainingMinutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "< 1m"
    }
}
