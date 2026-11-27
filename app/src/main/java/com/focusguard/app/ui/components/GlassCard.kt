package com.focusguard.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.theme.GlassTokens

/**
 * Balanced Glassmorphism card with frosted translucent surface, individual accent-tinted
 * 1dp border, subtle inner highlight, and restrained outer glow.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = GlassTokens.Radius.lg,
    isActive: Boolean = false,
    accentColor: Color = FrictionColors.Accent,
    enabled: Boolean = true,
    backgroundColor: Color = FrictionColors.CardBackground,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && onClick != null) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "glass-card-press"
    )

    val isDark = FrictionColors.useDarkPalette
    val bg = when {
        isActive -> accentColor.copy(alpha = if (isDark) 0.14f else 0.10f)
        else -> backgroundColor
    }
    val borderWidth = if (isActive) GlassTokens.Border.active else GlassTokens.Border.normal
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            accentColor.copy(
                alpha = when {
                    !enabled -> 0.12f
                    isActive -> if (isDark) 0.58f else 0.48f
                    else -> if (isDark) 0.28f else 0.24f
                }
            ),
            FrictionColors.GlassBorder
        )
    )

    val elevation = when {
        !enabled -> 0.dp
        isActive -> if (isDark) 14.dp else 10.dp
        else -> if (isDark) 10.dp else 7.dp
    }
    val shadowColor = when {
        !enabled -> Color.Transparent
        isDark -> accentColor.copy(alpha = if (isActive) 0.22f else 0.11f)
        else -> accentColor.copy(alpha = if (isActive) 0.16f else 0.08f)
    }

    val cardBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.055f),
                bg,
                FrictionColors.Surface.copy(alpha = 0.92f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.96f),
                bg,
                FrictionColors.SurfaceLight.copy(alpha = 0.90f)
            )
        }
    )

    val baseModifier = modifier
        .alpha(if (enabled) 1f else 0.55f)
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
        .border(borderWidth, borderBrush, shape)

    val finalModifier = if (onClick != null) {
        baseModifier.clickable(
            enabled = enabled,
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
