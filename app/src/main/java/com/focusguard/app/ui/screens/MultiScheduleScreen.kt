package com.focusguard.app.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.FocusGuardApp
import com.focusguard.app.persistence.StudyBlockSchedule
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.theme.FrictionColors
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiScheduleScreen(onBack: () -> Unit) {
    val prefs = FocusGuardApp.instance.prefs
    val activeBlock = prefs.getActiveStudyBlock()

    var isEnabled by remember { mutableStateOf(prefs.isScheduleEnabled) }
    var blocks by remember { mutableStateOf(prefs.studyBlocks.ifEmpty { listOf(defaultStudyBlock(1)) }) }
    val isLocked = remember(activeBlock?.id) { activeBlock != null || prefs.isStrictBlockActive() }

    fun save(nextEnabled: Boolean = isEnabled, nextBlocks: List<StudyBlockSchedule> = blocks) {
        isEnabled = nextEnabled
        blocks = nextBlocks
        prefs.isScheduleEnabled = nextEnabled
        prefs.studyBlocks = nextBlocks
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Study Blocks", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(FrictionColors.Background)
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 24.dp,
                    backgroundColor = FrictionColors.GlassBackground
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Multiple focus blocks",
                            color = FrictionColors.TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Create named time blocks. During active blocks, your selected distraction apps stay blocked.",
                            color = FrictionColors.TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        activeBlock?.let {
                            Text(
                                text = "Active now: ${it.title}",
                                color = FrictionColors.Success,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            item {
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
                                "Schedule blocking",
                                color = FrictionColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp
                            )
                            Text(
                                if (isEnabled) "${blocks.count { it.enabled }} block(s) enabled"
                                else "Disabled - no automatic blocks",
                                color = if (isEnabled) FrictionColors.Success else FrictionColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { save(nextEnabled = it) },
                            enabled = !isLocked,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = FrictionColors.Accent,
                                uncheckedThumbColor = FrictionColors.TextMuted,
                                uncheckedTrackColor = FrictionColors.SurfaceElevated
                            )
                        )
                    }
                }
            }

            itemsIndexed(blocks, key = { _, block -> block.id }) { index, block ->
                StudyBlockCard(
                    block = block,
                    index = index,
                    canDelete = blocks.size > 1,
                    isLocked = isLocked,
                    isActive = activeBlock?.id == block.id,
                    onChange = { updated ->
                        save(nextBlocks = blocks.map { if (it.id == block.id) updated else it })
                    },
                    onDelete = {
                        save(nextBlocks = blocks.filterNot { it.id == block.id })
                    }
                )
            }

            item {
                Button(
                    onClick = {
                        val next = blocks + defaultStudyBlock(blocks.size + 1)
                        save(nextBlocks = next)
                    },
                    enabled = !isLocked,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.Accent,
                        contentColor = FrictionColors.TextOnAccent,
                        disabledContainerColor = FrictionColors.SurfaceElevated,
                        disabledContentColor = FrictionColors.TextMuted
                    )
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Add study block", fontWeight = FontWeight.Bold)
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = FrictionColors.WarningSoft)
                ) {
                    Text(
                        "Each enabled block turns on Focus Guard for its selected days and time. Manage which apps are blocked from Apps to Block.",
                        color = FrictionColors.Warning,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyBlockCard(
    block: StudyBlockSchedule,
    index: Int,
    canDelete: Boolean,
    isLocked: Boolean,
    isActive: Boolean,
    onChange: (StudyBlockSchedule) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dayLabels = listOf(
        Calendar.SUNDAY to "S",
        Calendar.MONDAY to "M",
        Calendar.TUESDAY to "T",
        Calendar.WEDNESDAY to "W",
        Calendar.THURSDAY to "T",
        Calendar.FRIDAY to "F",
        Calendar.SATURDAY to "S"
    )

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        backgroundColor = FrictionColors.GlassBackground,
        isActive = isActive
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
                Text(
                    text = if (isActive) "Active now" else "Block ${index + 1}",
                    color = if (isActive) FrictionColors.Success else FrictionColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = block.enabled,
                        onCheckedChange = { onChange(block.copy(enabled = it)) },
                        enabled = !isLocked,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = FrictionColors.Accent,
                            uncheckedThumbColor = FrictionColors.TextMuted,
                            uncheckedTrackColor = FrictionColors.SurfaceElevated
                        )
                    )
                    IconButton(
                        onClick = onDelete,
                        enabled = !isLocked && canDelete
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete block",
                            tint = if (!isLocked && canDelete) FrictionColors.Warning else FrictionColors.TextMuted
                        )
                    }
                }
            }

            OutlinedTextField(
                value = block.title,
                onValueChange = { onChange(block.copy(title = it)) },
                enabled = !isLocked,
                singleLine = true,
                label = { Text("Block title") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = FrictionColors.TextPrimary,
                    unfocusedTextColor = FrictionColors.TextPrimary,
                    disabledTextColor = FrictionColors.TextSecondary,
                    focusedBorderColor = FrictionColors.Accent,
                    unfocusedBorderColor = FrictionColors.CardBorder,
                    focusedLabelColor = FrictionColors.Accent,
                    unfocusedLabelColor = FrictionColors.TextSecondary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimeChip(
                    label = "From",
                    hour = block.startHour,
                    minute = block.startMinute,
                    color = FrictionColors.Success,
                    enabled = !isLocked,
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            onChange(block.copy(startHour = h, startMinute = m))
                        }, block.startHour, block.startMinute, false).show()
                    }
                )

                Text(
                    "-",
                    color = FrictionColors.TextMuted,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                TimeChip(
                    label = "Until",
                    hour = block.endHour,
                    minute = block.endMinute,
                    color = FrictionColors.Accent,
                    enabled = !isLocked,
                    onClick = {
                        TimePickerDialog(context, { _, h, m ->
                            onChange(block.copy(endHour = h, endMinute = m))
                        }, block.endHour, block.endMinute, false).show()
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                dayLabels.forEach { (dayValue, dayName) ->
                    val selected = dayValue in block.days
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (selected) FrictionColors.Accent else FrictionColors.SurfaceElevated)
                            .clickable(enabled = !isLocked) {
                                val days = block.days.toMutableSet().apply {
                                    if (selected) remove(dayValue) else add(dayValue)
                                }
                                onChange(block.copy(days = days))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            dayName,
                            color = if (selected) Color.White else FrictionColors.TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = "${formatBlockClock(block.startHour, block.startMinute)} - " +
                    "${formatBlockClock(block.endHour, block.endMinute)} | " +
                    "${durationText(block)}",
                color = FrictionColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun TimeChip(
    label: String,
    hour: Int,
    minute: Int,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit
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
                formatBlockClock(hour, minute),
                color = color,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

private fun defaultStudyBlock(index: Int): StudyBlockSchedule {
    val startHour = if (index == 1) 9 else 13
    val endHour = if (index == 1) 12 else 15
    return StudyBlockSchedule(
        id = UUID.randomUUID().toString(),
        title = "Study Block $index",
        startHour = startHour,
        startMinute = 0,
        endHour = endHour,
        endMinute = 0,
        days = setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY
        ),
        enabled = true
    )
}

private fun durationText(block: StudyBlockSchedule): String {
    val startMinutes = block.startHour * 60 + block.startMinute
    val endMinutes = block.endHour * 60 + block.endMinute
    val durationMinutes = if (endMinutes > startMinutes) {
        endMinutes - startMinutes
    } else {
        (24 * 60 - startMinutes) + endMinutes
    }
    val hours = durationMinutes / 60
    val minutes = durationMinutes % 60
    return "${hours}h ${minutes}m"
}

private fun formatBlockClock(hour: Int, minute: Int): String {
    val amPm = if (hour < 12) "AM" else "PM"
    val displayHour = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format("%d:%02d %s", displayHour, minute, amPm)
}
