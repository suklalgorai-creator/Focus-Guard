package com.focusguard.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.app.ui.theme.FrictionColors

@Composable
fun PulsingShield(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "ShieldPulse")

    // Outer ring pulse
    val ringScale by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingScale"
    )

    // Glow alpha
    val glowAlpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    // Secondary ring (delayed)
    val ring2Scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Ring2Scale"
    )

    val activeColor = FrictionColors.Success
    val inactiveColor = FrictionColors.Accent

    val color = if (isActive) activeColor else inactiveColor

    Box(
        modifier = modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // Outer glow ring
            drawCircle(
                color = color.copy(alpha = glowAlpha * 0.3f),
                radius = (size.minDimension / 2) * ring2Scale,
                center = center
            )

            // Middle ring (stroked)
            drawCircle(
                color = color.copy(alpha = glowAlpha * 0.6f),
                radius = (size.minDimension / 2) * ringScale,
                style = Stroke(width = 2.dp.toPx()),
                center = center
            )

            // Inner filled circle with gradient feel
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.9f),
                        color.copy(alpha = 0.5f),
                        color.copy(alpha = 0.1f)
                    ),
                    center = center,
                    radius = size.minDimension / 4.5f
                ),
                radius = size.minDimension / 4f,
                center = center
            )

            // Core dot
            drawCircle(
                color = color,
                radius = size.minDimension / 7f,
                center = center
            )
        }

        // Status text
        Text(
            text = if (isActive) "ON" else "OFF",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            letterSpacing = 2.sp
        )
    }
}
