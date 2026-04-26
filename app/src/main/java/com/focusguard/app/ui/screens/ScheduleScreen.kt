package com.focusguard.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.theme.FrictionColors
import java.util.Calendar

/**
 * Schedule Configuration Screen — Modern Redesign.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = FocusGuardApp.instance.prefs

    var isEnabled by remember { mutableStateOf(prefs.isScheduleEnabled) }
    var startHour by remember { mutableIntStateOf(prefs.scheduleStartHour) }
    var startMinute by remember { mutableIntStateOf(prefs.scheduleStartMinute) }
    var endHour by remember { mutableIntStateOf(prefs.scheduleEndHour) }
    var endMinute by remember { mutableIntStateOf(prefs.scheduleEndMinute) }
    var activeDays by remember { mutableStateOf(prefs.scheduleDays) }
    val isLocked = remember { prefs.isGuardActiveNow() }

    val dayLabels = listOf(
        Calendar.SUNDAY to "S",
        Calendar.MONDAY to "M",
        Calendar.TUESDAY to "T",
        Calendar.WEDNESDAY to "W",
        Calendar.THURSDAY to "T",
        Calendar.FRIDAY to "F",
        Calendar.SATURDAY to "S"
    )

    val dayFullNames = listOf(
        Calendar.SUNDAY to "Sun",
        Calendar.MONDAY to "Mon",
        Calendar.TUESDAY to "Tue",
        Calendar.WEDNESDAY to "Wed",
        Calendar.THURSDAY to "Thu",
        Calendar.FRIDAY to "Fri",
        Calendar.SATURDAY to "Sat"
    )

    val startMinutes = startHour * 60 + startMinute
    val endMinutes = endHour * 60 + endMinute
    val durationMinutes = if (endMinutes > startMinutes) {
        endMinutes - startMinutes
    } else {
        (24 * 60 - startMinutes) + endMinutes
    }
    val durationHours = durationMinutes / 60
    val durationRemaining = durationMinutes % 60

    fun save() {
        prefs.isScheduleEnabled = isEnabled
        prefs.scheduleStartHour = startHour
        prefs.scheduleStartMinute = startMinute
        prefs.scheduleEndHour = endHour
        prefs.scheduleEndMinute = endMinute
        prefs.scheduleDays = activeDays
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Schedule", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = {
                        save()
                        onBack()
                    }) {
                        Icon(Icons.Rounded.ArrowBack, "Back", tint = FrictionColors.TextPrimary)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                backgroundColor = FrictionColors.GlassBackground
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Focus schedule",
                        color = FrictionColors.TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Set the hours where distractions need resistance.",
                        color = FrictionColors.TextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                    if (isLocked) {
                        Text(
                            text = "Active focus window. Existing blocks stay locked.",
                            color = FrictionColors.Warning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Enable toggle
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                backgroundColor = FrictionColors.GlassBackground
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Daily Schedule",
                            color = FrictionColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (isEnabled) "Active — blocking during set hours"
                            else "Disabled — no auto-blocking",
                            color = if (isEnabled) FrictionColors.Success else FrictionColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            save()
                        },
                        enabled = !isLocked,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = FrictionColors.Accent,
                            uncheckedThumbColor = FrictionColors.TextMuted,
                            uncheckedTrackColor = FrictionColors.SurfaceElevated,
                            disabledCheckedThumbColor = FrictionColors.TextSecondary,
                            disabledCheckedTrackColor = FrictionColors.Accent.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // Time range
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                backgroundColor = FrictionColors.GlassBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        "Block Window",
                        color = FrictionColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeChip(
                            label = "From",
                            hour = startHour,
                            minute = startMinute,
                            color = FrictionColors.Success,
                            enabled = !isLocked,
                            onClick = {
                                TimePickerDialog(context, { _, h, m ->
                                    startHour = h
                                    startMinute = m
                                    save()
                                }, startHour, startMinute, false).show()
                            }
                        )

                        Text(
                            "→",
                            color = FrictionColors.TextMuted,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        TimeChip(
                            label = "Until",
                            hour = endHour,
                            minute = endMinute,
                            color = FrictionColors.Accent,
                            enabled = !isLocked,
                            onClick = {
                                TimePickerDialog(context, { _, h, m ->
                                    endHour = h
                                    endMinute = m
                                    save()
                                }, endHour, endMinute, false).show()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(FrictionColors.SecondarySoft)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${durationHours}h ${durationRemaining}m block duration",
                            color = FrictionColors.Secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Day selector
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                backgroundColor = FrictionColors.GlassBackground
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        "Active Days",
                        color = FrictionColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        dayLabels.forEach { (dayValue, dayName) ->
                            val isActive = activeDays.contains(dayValue)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isActive && !isLocked -> FrictionColors.Accent
                                            isActive && isLocked -> FrictionColors.Accent.copy(alpha = 0.3f)
                                            else -> FrictionColors.SurfaceElevated
                                        }
                                    )
                                    .clickable(enabled = !isLocked) {
                                        activeDays = activeDays.toMutableSet().apply {
                                            if (isActive) remove(dayValue) else add(dayValue)
                                        }
                                        save()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    dayName,
                                    color = when {
                                        isActive -> Color.White
                                        else -> FrictionColors.TextMuted
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Status preview
            if (isEnabled) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (prefs.isWithinSchedule()) FrictionColors.SuccessSoft
                                         else FrictionColors.CardBackground
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val isCurrentlyActive = prefs.isWithinSchedule()
                        Text(
                            if (isCurrentlyActive) "🟢 Currently Blocking" else "Schedule Set",
                            color = if (isCurrentlyActive) FrictionColors.Success else FrictionColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "${formatHour(startHour, startMinute)} → ${formatHour(endHour, endMinute)}",
                            color = FrictionColors.TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val activeDayNames = dayFullNames
                            .filter { it.first in activeDays }
                            .joinToString(", ") { it.second }
                        Text(
                            activeDayNames.ifEmpty { "No days selected" },
                            color = FrictionColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Warning
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = FrictionColors.WarningSoft)
            ) {
                Text(
                    "Focus schedules block distracting apps during selected hours. Settings and app-removal screens are interrupted only if you separately enable Exit Delay Protection in Strict Mode.",
                    color = FrictionColors.Warning,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun TimeChip(
    label: String,
    hour: Int,
    minute: Int,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier
    ) {
        Text(
            label,
            color = FrictionColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = FrictionColors.SurfaceElevated)
        ) {
            Text(
                formatHour(hour, minute),
                color = color,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            )
        }
    }
}

private fun formatHour(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%d:%02d %s", displayHour, minute, amPm)
}
