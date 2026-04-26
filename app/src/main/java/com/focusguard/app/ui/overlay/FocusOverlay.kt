package com.focusguard.app.ui.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.focusguard.app.ui.theme.FrictionColors
import kotlinx.coroutines.delay

@Composable
fun FocusOverlay(
    message: String,
    onSolve: () -> Unit,
    onSkip: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var entered by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.96f,
        animationSpec = tween(durationMillis = 180),
        label = "focus_overlay_card_scale"
    )

    LaunchedEffect(Unit) {
        entered = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .scale(cardScale),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.10f)
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSolve()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.Accent,
                        contentColor = FrictionColors.TextOnAccent
                    )
                ) {
                    Text("Solve 1 Question", fontWeight = FontWeight.Bold)
                }

                SkipButtonWithDelay(onSkip = onSkip)
            }
        }
    }
}

@Composable
fun SkipButtonWithDelay(
    delayMs: Long = 3_000,
    onSkip: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var enabled by remember { mutableStateOf(false) }

    LaunchedEffect(delayMs) {
        delay(delayMs)
        enabled = true
    }

    TextButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onSkip()
        },
        enabled = enabled
    ) {
        Text(
            text = if (enabled) "Skip" else "Wait...",
            color = if (enabled) FrictionColors.TextSecondary else FrictionColors.TextMuted
        )
    }
}

@Composable
fun ExitAttemptScreen(
    message: String,
    onStay: () -> Unit,
    onExit: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var entered by remember { mutableStateOf(false) }
    val cardScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.96f,
        animationSpec = tween(durationMillis = 180),
        label = "exit_attempt_card_scale"
    )

    LaunchedEffect(Unit) {
        entered = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.70f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .padding(24.dp)
                .scale(cardScale),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.10f)
            ),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("😏", fontSize = 40.sp)

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 18.sp,
                    lineHeight = 25.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStay()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrictionColors.Accent,
                        contentColor = FrictionColors.TextOnAccent
                    )
                ) {
                    Text("Stay Focused", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                DelayedExitButton(onExit = onExit)
            }
        }
    }
}

@Composable
fun DelayedExitButton(
    delayMs: Long = 4_000,
    onExit: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var enabled by remember { mutableStateOf(false) }

    LaunchedEffect(delayMs) {
        delay(delayMs)
        enabled = true
    }

    TextButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onExit()
        },
        enabled = enabled
    ) {
        Text(
            text = if (enabled) "Continue anyway" else "Wait...",
            color = if (enabled) FrictionColors.TextSecondary else FrictionColors.TextMuted
        )
    }
}
