package com.focusguard.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Lightweight app palette used by legacy and new Compose screens.
 * Values switch between dark and light mode from FocusGuardTheme.
 */
object FrictionColors {
    var useDarkPalette: Boolean = true

    val Background: Color
        get() = if (useDarkPalette) Color(0xFF0B0F1A) else Color(0xFFF6F8FC)
    val Surface: Color
        get() = if (useDarkPalette) Color(0xFF111827) else Color(0xFFFFFFFF)
    val SurfaceLight: Color
        get() = if (useDarkPalette) Color(0xFF172036) else Color(0xFFF9FAFB)
    val SurfaceElevated: Color
        get() = if (useDarkPalette) Color(0xFF202B45) else Color(0xFFEFF3F8)

    val GlassBackground: Color
        get() = if (useDarkPalette) Color(0x24FFFFFF) else Color(0xD9FFFFFF)
    val GlassBorder: Color
        get() = if (useDarkPalette) Color(0x1FFFFFFF) else Color(0x1A111827)

    val Accent: Color
        get() = if (useDarkPalette) Color(0xFF2DD4FF) else Color(0xFF2563EB)
    val AccentPurple: Color
        get() = if (useDarkPalette) Color(0xFF8B5CF6) else Color(0xFF7C3AED)
    val AccentGradient: List<Color>
        get() = listOf(Accent, AccentPurple)
    val AccentSoft: Color
        get() = if (useDarkPalette) Color(0x332DD4FF) else Color(0x1A2563EB)
    val AccentMuted: Color
        get() = if (useDarkPalette) Color(0x1A2DD4FF) else Color(0x142563EB)

    val Secondary: Color
        get() = if (useDarkPalette) Color(0xFFFBBF24) else Color(0xFFD97706)
    val SecondarySoft: Color
        get() = if (useDarkPalette) Color(0x33FBBF24) else Color(0x1AD97706)

    val Success: Color
        get() = if (useDarkPalette) Color(0xFF34D399) else Color(0xFF059669)
    val SuccessSoft: Color
        get() = if (useDarkPalette) Color(0x2234D399) else Color(0x1A059669)
    val Warning: Color
        get() = if (useDarkPalette) Color(0xFFFBBF24) else Color(0xFFD97706)
    val WarningSoft: Color
        get() = if (useDarkPalette) Color(0x22FBBF24) else Color(0x1AD97706)
    val Error: Color
        get() = if (useDarkPalette) Color(0xFFF87171) else Color(0xFFDC2626)
    val ErrorSoft: Color
        get() = if (useDarkPalette) Color(0x22F87171) else Color(0x1ADC2626)

    val TextPrimary: Color
        get() = if (useDarkPalette) Color(0xFFF8FAFC) else Color(0xFF111827)
    val TextSecondary: Color
        get() = if (useDarkPalette) Color(0xFFB7C0D1) else Color(0xFF6B7280)
    val TextMuted: Color
        get() = if (useDarkPalette) Color(0xFF6B7280) else Color(0xFF9CA3AF)
    val TextOnAccent: Color
        get() = Color.White

    val StatBlocked: Color
        get() = Error
    val StatGaveUp: Color
        get() = Success
    val StatBypassed: Color
        get() = Warning

    val GradientStart: Color
        get() = if (useDarkPalette) Color(0xFF0B0F1A) else Color(0xFFF6F8FC)
    val GradientMid: Color
        get() = if (useDarkPalette) Color(0xFF111827) else Color(0xFFEFF6FF)
    val GradientEnd: Color
        get() = if (useDarkPalette) Color(0xFF070A12) else Color(0xFFFFFFFF)

    val CardBackground: Color
        get() = if (useDarkPalette) Color(0xFF111827) else Color(0xFFFFFFFF)
    val CardBorder: Color
        get() = if (useDarkPalette) Color(0x1AFFFFFF) else Color(0x1A111827)

    val NeonRed: Color
        get() = Error
    val NeonOrange: Color
        get() = Secondary
    val NeonGreen: Color
        get() = Success
    val PulseGlow: Color
        get() = AccentSoft
    val SuccessGlow: Color
        get() = SuccessSoft
    val Denied: Color
        get() = if (useDarkPalette) Color(0xFFD50000) else Color(0xFFB91C1C)
}
