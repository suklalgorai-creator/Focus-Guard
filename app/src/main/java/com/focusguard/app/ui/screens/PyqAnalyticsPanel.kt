package com.focusguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.domain.analytics.AnalyticsDashboardState
import com.focusguard.app.domain.analytics.DailyAttemptCount
import com.focusguard.app.domain.pyq.SubjectPerformanceCategory
import com.focusguard.app.domain.pyq.SubjectPerformanceStats
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.theme.FrictionColors

@Composable
fun PyqAnalyticsPanel(
    state: AnalyticsDashboardState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "PYQ Insights",
            color = FrictionColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AnalyticsMetricCard(
                label = "Today",
                value = state.todayAttempts.toString(),
                modifier = Modifier.weight(1f),
                color = FrictionColors.Success
            )
            AnalyticsMetricCard(
                label = "Streak",
                value = "${state.streak}d",
                modifier = Modifier.weight(1f),
                color = FrictionColors.Warning
            )
            AnalyticsMetricCard(
                label = "Avg Time",
                value = formatAttemptTime(state.averageTimeMs),
                modifier = Modifier.weight(1f),
                color = FrictionColors.Accent
            )
        }

        if (state.subjectAccuracy.isNotEmpty()) {
            AnalyticsSectionCard(title = "Subject Accuracy") {
                state.subjectAccuracy.forEach { subject ->
                    SubjectAccuracyRow(subject)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        AnalyticsSectionCard(title = "Last 7 Days") {
            state.last7Days.forEach { day ->
                DailyAttemptRow(day, maxAttempts = state.last7Days.maxOfOrNull { it.attempts } ?: 1)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (state.weakSubjects.isNotEmpty()) {
            AnalyticsSectionCard(title = "Weak Subjects") {
                Text(
                    text = state.weakSubjects.joinToString(", "),
                    color = FrictionColors.Accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AnalyticsMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 16.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = color,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = FrictionColors.TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AnalyticsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                color = FrictionColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Column(content = content)
        }
    }
}

@Composable
private fun SubjectAccuracyRow(subject: SubjectPerformanceStats) {
    val fraction = (subject.accuracyPercent / 100.0).toFloat().coerceIn(0f, 1f)
    val color = when (subject.category) {
        SubjectPerformanceCategory.STRONG -> FrictionColors.Success
        SubjectPerformanceCategory.MODERATE -> FrictionColors.Warning
        SubjectPerformanceCategory.WEAK -> FrictionColors.Accent
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = subject.subject,
                color = FrictionColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${subject.accuracyPercent.toInt()}%",
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
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
                    .background(color)
            )
        }
    }
}

@Composable
private fun DailyAttemptRow(
    day: DailyAttemptCount,
    maxAttempts: Int
) {
    val fraction = (day.attempts.toFloat() / maxAttempts.coerceAtLeast(1).toFloat()).coerceIn(0.04f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = day.day.takeLast(5),
            color = FrictionColors.TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(48.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(FrictionColors.SurfaceElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(FrictionColors.Success)
            )
        }
        Text(
            text = day.attempts.toString(),
            color = FrictionColors.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(start = 10.dp)
                .width(28.dp)
        )
    }
}

private fun formatAttemptTime(ms: Long): String {
    if (ms <= 0L) return "-"
    val seconds = ms / 1000L
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}
