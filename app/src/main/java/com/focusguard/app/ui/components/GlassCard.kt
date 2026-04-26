package com.focusguard.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusguard.app.ui.theme.FrictionColors

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    isActive: Boolean = false,
    backgroundColor: Color = FrictionColors.CardBackground,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "glass-card-press"
    )
    val bg = if (isActive) FrictionColors.AccentMuted else backgroundColor
    val borderColor = if (isActive) FrictionColors.Accent.copy(alpha = 0.36f) else FrictionColors.GlassBorder
    val elevation = if (FrictionColors.useDarkPalette) 16.dp else 10.dp
    val shadowColor = if (FrictionColors.useDarkPalette) {
        FrictionColors.Accent.copy(alpha = if (isActive) 0.16f else 0.08f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }
    val cardBrush = Brush.linearGradient(
        colors = if (FrictionColors.useDarkPalette) {
            listOf(
                Color.White.copy(alpha = 0.075f),
                bg,
                FrictionColors.Surface.copy(alpha = 0.82f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.96f),
                bg,
                FrictionColors.SurfaceLight.copy(alpha = 0.88f)
            )
        }
    )

    val baseModifier = modifier
        .scale(scale)
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = shadowColor,
            spotColor = shadowColor
        )
        .clip(shape)
        .background(cardBrush)
        .border(0.7.dp, borderColor, shape)

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = null
        ) { onClick() }
    } else {
        baseModifier
    }

    Box(
        modifier = finalModifier.padding(0.dp),
        content = content
    )
}
