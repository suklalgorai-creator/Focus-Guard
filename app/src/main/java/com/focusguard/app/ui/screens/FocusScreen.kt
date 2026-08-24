package com.focusguard.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.domain.focus.FocusBadge
import com.focusguard.app.domain.focus.FocusMode
import com.focusguard.app.domain.focus.FocusSessionState
import com.focusguard.app.presentation.focus.FocusSessionViewModel
import com.focusguard.app.ui.components.AnimatedProgress
import com.focusguard.app.ui.components.GlassCard
import com.focusguard.app.ui.components.GradientButton
import com.focusguard.app.ui.components.SecondaryButton
import com.focusguard.app.ui.components.StatItem
import com.focusguard.app.ui.theme.FrictionColors
import java.util.Calendar

@Composable
fun FocusScreen(
    viewModel: FocusSessionViewModel,
    onOpenPyq: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    var visible by remember { mutableStateOf(false) }
    var useAccountabilityLock by remember { mutableStateOf(true) }
    var showLockSetup by remember { mutableStateOf(false) }
    var showUnlock by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
        viewModel.refresh()
    }

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
            enter = fadeIn(tween(260)) +
                slideInVertically(
                    animationSpec = tween(300, easing = FastOutSlowInEasing),
                    initialOffsetY = { it / 10 }
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    FocusHeader(
                        streakDays = state.streakDays
                    )
                }

                item {
                    ModeToggle(
                        selectedMode = state.mode,
                        enabled = !state.isActive,
                        onModeSelected = { mode ->
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setMode(mode)
                        }
                    )
                }

                item {
                    CircularTimer(
                        state = state,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    XPIndicator(
                        sessionXp = state.sessionXp,
                        totalXp = state.totalXp
                    )
                }

                item {
                    AccountabilityLockCard(
                        enabled = useAccountabilityLock,
                        sessionActive = state.isActive,
                        lockActive = state.isAccountabilityLockActive,
                        onEnabledChange = { useAccountabilityLock = it }
                    )
                }

                item {
                    FocusActionButton(
                        isActive = state.isActive,
                        onStart = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (useAccountabilityLock) {
                                showLockSetup = true
                            } else {
                                viewModel.startFocus()
                            }
                        },
                        onStop = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.isAccountabilityLockActive) {
                                showUnlock = true
                            } else {
                                viewModel.endSession()
                            }
                        }
                    )
                }

                item {
                    BottomFocusStats(
                        state = state,
                        onOpenPyq = onOpenPyq
                    )
                }

                item {
                    BadgeRow(badges = state.badges)
                }

                item { Spacer(modifier = Modifier.height(18.dp)) }
            }
        }

        if (showLockSetup) {
            AccountabilityPinDialog(
                title = "Create accountability lock",
                body = "Use a 6–12 digit PIN that you give to a trusted person. Focus Guard cannot recover it. The session stays protected until its timer ends or that PIN is entered.",
                confirmLabel = "Start locked focus",
                requireConfirmation = true,
                onDismiss = { showLockSetup = false },
                onConfirm = { pin ->
                    showLockSetup = false
                    viewModel.startFocus(pin)
                }
            )
        }

        if (showUnlock) {
            AccountabilityPinDialog(
                title = "Accountability PIN required",
                body = "Ask the person holding the PIN before ending this focus session.",
                confirmLabel = "End session",
                onDismiss = { showUnlock = false },
                onConfirm = { pin ->
                    showUnlock = false
                    viewModel.endSession(pin)
                }
            )
        }
    }
}

@Composable
private fun AccountabilityLockCard(
    enabled: Boolean,
    sessionActive: Boolean,
    lockActive: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        backgroundColor = if (lockActive) FrictionColors.WarningSoft else FrictionColors.GlassBackground
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (lockActive) "Accountability lock active" else "Accountability lock",
                color = FrictionColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (lockActive) {
                    "This session can only be ended with its trusted PIN."
                } else {
                    "Require a trusted person's PIN to end this session early."
                },
                color = FrictionColors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            if (!sessionActive) {
                TextButton(onClick = { onEnabledChange(!enabled) }) {
                    Text(if (enabled) "Lock enabled for next session" else "Enable for next session")
                }
            }
        }
    }
}

@Composable
private fun AccountabilityPinDialog(
    title: String,
    body: String,
    confirmLabel: String,
    requireConfirmation: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val validPin = pin.matches(Regex("\\d{6,12}"))
    val canConfirm = validPin && (!requireConfirmation || pin == confirmation)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = { Text("6–12 digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                if (requireConfirmation) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it.filter(Char::isDigit).take(12) },
                        label = { Text("Confirm PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
                if ((pin.isNotEmpty() && !validPin) ||
                    (requireConfirmation && confirmation.isNotEmpty() && confirmation != pin)
                ) {
                    Text(
                        text = if (!validPin) "Use 6–12 digits." else "PINs do not match.",
                        color = FrictionColors.Error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pin) }, enabled = canConfirm) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FocusHeader(streakDays: Int) {
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
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Start clean. Stay locked.",
                color = FrictionColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        GlassCard(
            cornerRadius = 100.dp,
            backgroundColor = FrictionColors.GlassBackground
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Whatshot,
                    contentDescription = null,
                    tint = FrictionColors.Warning,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "${streakDays} day streak",
                    color = FrictionColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CircularTimer(
    state: FocusSessionState,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "focus-timer-progress"
    )
    val pulse = rememberInfiniteTransition(label = "timer-pulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.10f,
        targetValue = if (state.isActive) 0.26f else 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "timer-glow"
    )

    GlassCard(
        modifier = modifier,
        cornerRadius = 32.dp,
        backgroundColor = FrictionColors.GlassBackground,
        isActive = state.isActive
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 18.dp.toPx()
                    val inset = stroke / 2f + 6.dp.toPx()
                    val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                    val topLeft = Offset(inset, inset)

                    drawCircle(
                        color = FrictionColors.Accent.copy(alpha = glowAlpha),
                        radius = size.minDimension * 0.48f,
                        center = Offset(size.width / 2f, size.height / 2f)
                    )
                    drawArc(
                        color = FrictionColors.SurfaceElevated,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                FrictionColors.Accent,
                                FrictionColors.AccentPurple,
                                FrictionColors.Accent
                            )
                        ),
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = FrictionColors.Accent,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = formatClock(state.remainingMillis),
                        color = FrictionColors.TextPrimary,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp
                    )
                    Text(
                        text = if (state.isActive) "Stay focused" else "${state.mode.label} ready",
                        color = FrictionColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun XPIndicator(
    sessionXp: Int,
    totalXp: Int
) {
    val animatedXp by animateIntAsState(
        targetValue = sessionXp,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "session-xp"
    )
    val scale by animateFloatAsState(
        targetValue = if (sessionXp > 0) 1.03f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
        label = "xp-scale"
    )

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        cornerRadius = 24.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(FrictionColors.AccentGradient)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color.White)
                }
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text(
                        text = "+$animatedXp XP",
                        color = FrictionColors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "earned this session",
                        color = FrictionColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Text(
                text = "$totalXp total",
                color = FrictionColors.Accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ModeToggle(
    selectedMode: FocusMode,
    enabled: Boolean,
    onModeSelected: (FocusMode) -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        backgroundColor = FrictionColors.GlassBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FocusMode.values().forEach { mode ->
                ModeToggleItem(
                    mode = mode,
                    selected = selectedMode == mode,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onModeSelected(mode) }
                )
            }
        }
    }
}

@Composable
private fun ModeToggleItem(
    mode: FocusMode,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.98f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "mode-scale"
    )
    Box(
        modifier = modifier
            .height(48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) Brush.linearGradient(FrictionColors.AccentGradient)
                else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .border(
                width = if (selected) 0.dp else 0.6.dp,
                color = if (selected) Color.Transparent else FrictionColors.GlassBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mode.label,
            color = if (selected) Color.White else FrictionColors.TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun FocusActionButton(
    isActive: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    if (isActive) {
        SecondaryButton(
            text = "End Session",
            leadingIcon = Icons.Rounded.Stop,
            modifier = Modifier.fillMaxWidth(),
            onClick = onStop
        )
    } else {
        GradientButton(
            text = "Start Focus",
            leadingIcon = Icons.Rounded.PlayArrow,
            modifier = Modifier.fillMaxWidth(),
            onClick = onStart
        )
    }
}

@Composable
private fun BottomFocusStats(
    state: FocusSessionState,
    onOpenPyq: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatItem(
                label = "Session time",
                value = "${state.mode.durationMinutes}m",
                icon = Icons.Rounded.HourglassTop,
                modifier = Modifier.weight(1f),
                accent = FrictionColors.Accent
            )
            StatItem(
                label = "XP earned",
                value = state.sessionXp.toString(),
                icon = Icons.Rounded.Bolt,
                modifier = Modifier.weight(1f),
                accent = FrictionColors.Secondary
            )
        }
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            backgroundColor = FrictionColors.GlassBackground,
            onClick = onOpenPyq
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Progress",
                            color = FrictionColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Open PYQs when you want active recall.",
                            color = FrictionColors.TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        color = FrictionColors.Accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                AnimatedProgress(progress = state.progress, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun BadgeRow(badges: List<FocusBadge>) {
    if (badges.isEmpty()) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp,
            backgroundColor = FrictionColors.GlassBackground
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Star, contentDescription = null, tint = FrictionColors.TextMuted)
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = "Badges unlock as you hold focus.",
                    color = FrictionColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        badges.forEach { badge ->
            Box(modifier = Modifier.weight(1f)) {
                BadgeChip(
                    badge = badge,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        repeat((4 - badges.size).coerceAtLeast(0)) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BadgeChip(
    badge: FocusBadge,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 18.dp,
        backgroundColor = FrictionColors.SecondarySoft
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = badgeIcon(badge.id),
                contentDescription = null,
                tint = FrictionColors.Secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = badge.label,
                color = FrictionColors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun badgeIcon(id: String): ImageVector {
    return when (id) {
        "streak" -> Icons.Rounded.Whatshot
        "deep-flow" -> Icons.Rounded.Timer
        else -> Icons.Rounded.Star
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
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
