package com.focusguard.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Balanced Glassmorphism palette for Focus Guard.
 * Supports both Deep Obsidian Dark Mode and Frosted Off-White Light Mode.
 */
object FrictionColors {
    var useDarkPalette: Boolean = true

    val Background: Color
        get() = if (useDarkPalette) Color(0xFF0B0F17) else Color(0xFFF4F7FB)
    val Surface: Color
        get() = if (useDarkPalette) Color(0xFF131A26) else Color(0xFFFFFFFF)
    val SurfaceLight: Color
        get() = if (useDarkPalette) Color(0xFF1A2333) else Color(0xFFF8FAFC)
    val SurfaceElevated: Color
        get() = if (useDarkPalette) Color(0xFF222E42) else Color(0xFFE8EEF6)

    val GlassBackground: Color
        get() = if (useDarkPalette) Color(0xB8162030) else Color(0xD9FFFFFF)
    val GlassBorder: Color
        get() = if (useDarkPalette) Color(0x28FFFFFF) else Color(0x1F0F172A)

    val Accent: Color
        get() = GlassTokens.Accents.ProgressStats
    val AccentPurple: Color
        get() = GlassTokens.Accents.FocusSession
    val AccentGradient: List<Color>
        get() = listOf(GlassTokens.Accents.FocusSession, GlassTokens.Accents.StudyFlow)
    val AccentSoft: Color
        get() = if (useDarkPalette) Color(0x2634D399) else Color(0x1A10B981)
    val AccentMuted: Color
        get() = if (useDarkPalette) Color(0x1E34D399) else Color(0x1410B981)

    val Info: Color
        get() = GlassTokens.Accents.AppBlocking
    val InfoSoft: Color
        get() = if (useDarkPalette) Color(0x1F22D3EE) else Color(0x180891B2)

    val Secondary: Color
        get() = GlassTokens.Accents.Schedules
    val SecondarySoft: Color
        get() = if (useDarkPalette) Color(0x29FBBF24) else Color(0x1AD97706)

    val Success: Color
        get() = if (useDarkPalette) Color(0xFF34D399) else Color(0xFF059669)
    val SuccessSoft: Color
        get() = if (useDarkPalette) Color(0x2234D399) else Color(0x18059669)
    val Warning: Color
        get() = if (useDarkPalette) Color(0xFFFBBF24) else Color(0xFFD97706)
    val WarningSoft: Color
        get() = if (useDarkPalette) Color(0x22FBBF24) else Color(0x1AD97706)
    val Error: Color
        get() = if (useDarkPalette) Color(0xFFFB7185) else Color(0xFFE11D48)
    val ErrorSoft: Color
        get() = if (useDarkPalette) Color(0x24FB7185) else Color(0x1AE11D48)

    val TextPrimary: Color
        get() = if (useDarkPalette) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val TextSecondary: Color
        get() = if (useDarkPalette) Color(0xFFB7C3D6) else Color(0xFF475569)
    val TextMuted: Color
        get() = if (useDarkPalette) Color(0xFF7B89A0) else Color(0xFF64748B)
    val TextOnAccent: Color
        get() = Color.White

    val StatBlocked: Color
        get() = Error
    val StatGaveUp: Color
        get() = Success
    val StatBypassed: Color
        get() = Warning

    val GradientStart: Color
        get() = if (useDarkPalette) Color(0xFF0B0F17) else Color(0xFFF4F7FB)
    val GradientMid: Color
        get() = if (useDarkPalette) Color(0xFF111827) else Color(0xFFEEF4FF)
    val GradientEnd: Color
        get() = if (useDarkPalette) Color(0xFF090D14) else Color(0xFFF8FAFC)

    val CardBackground: Color
        get() = if (useDarkPalette) Color(0xB8141C2A) else Color(0xE6FFFFFF)
    val CardBorder: Color
        get() = if (useDarkPalette) Color(0x22FFFFFF) else Color(0x1A0F172A)

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
        get() = if (useDarkPalette) Color(0xFFE11D48) else Color(0xFFBE123C)
}

@Composable
fun FocusGuardTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    FrictionColors.useDarkPalette = darkTheme
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = GlassTokens.Accents.FocusSession,
            onPrimary = FrictionColors.TextOnAccent,
            secondary = GlassTokens.Accents.AppBlocking,
            tertiary = GlassTokens.Accents.ProgressStats,
            surface = FrictionColors.Surface,
            onSurface = FrictionColors.TextPrimary,
            background = FrictionColors.Background,
            onBackground = FrictionColors.TextPrimary,
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF7C3AED),
            onPrimary = FrictionColors.TextOnAccent,
            secondary = Color(0xFF0891B2),
            tertiary = Color(0xFF059669),
            surface = FrictionColors.Surface,
            onSurface = FrictionColors.TextPrimary,
            background = FrictionColors.Background,
            onBackground = FrictionColors.TextPrimary,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = FocusGuardTypography
    ) {
        content()
    }
}
