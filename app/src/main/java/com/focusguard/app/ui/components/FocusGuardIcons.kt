package com.focusguard.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.focusguard.app.R
import com.focusguard.app.ui.theme.FrictionColors

/**
 * Semantic icon identifiers matching the Focus Guard glassmorphism icon system.
 */
enum class FocusGuardIconId(
    val accentColor: Color,
    val vector: ImageVector?
) {
    BrandShieldBrain(Color(0xFF22D3EE), null),

    // Main Navigation
    Today(Color(0xFFA78BFA), Icons.Rounded.Home),                 // Violet
    Shield(Color(0xFF22D3EE), Icons.Rounded.VerifiedUser),        // Cyan
    Progress(Color(0xFF3B82F6), Icons.Rounded.TrendingUp),        // Blue
    You(Color(0xFFF472B6), Icons.Rounded.Person),                 // Pink

    // Focus & Protection
    FocusSession(Color(0xFFFBBF24), Icons.Rounded.HourglassTop),  // Amber
    Blocklist(Color(0xFFC084FC), Icons.Rounded.Block),            // Purple
    Schedule(Color(0xFF3B82F6), Icons.Rounded.CalendarMonth),     // Blue
    AccountabilityLock(Color(0xFFFB7185), Icons.Rounded.Lock),    // Rose
    Permissions(Color(0xFF22D3EE), Icons.Rounded.Key),            // Cyan

    // Progress & Learning
    Usage(Color(0xFF34D399), Icons.Rounded.BarChart),             // Emerald
    Pyq(Color(0xFFC4B5FD), Icons.Rounded.MenuBook),               // Lavender
    StudyFlowSync(Color(0xFF22D3EE), Icons.Rounded.Sync),         // Cyan
    CloudSync(Color(0xFF818CF8), Icons.Rounded.CloudUpload),      // Indigo

    // Settings & Account
    Settings(Color(0xFF3B82F6), Icons.Rounded.Settings),          // Blue
    Account(Color(0xFF3B82F6), Icons.Rounded.AccountCircle),      // Blue
    Notifications(Color(0xFFF472B6), Icons.Rounded.Notifications),// Pink
    Theme(Color(0xFFFBBF24), Icons.Rounded.Palette),              // Amber
    About(Color(0xFF818CF8), Icons.Rounded.Info)                  // Indigo
}

/**
 * Centralized glassmorphism icon renderer.
 * Use [showGlassTile] = true for feature cards/actions and false for compact bottom navigation.
 */
@Composable
fun FocusGuardIcon(
    iconId: FocusGuardIconId,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tileSize: Dp = 44.dp,
    showGlassTile: Boolean = false,
    showGlow: Boolean = showGlassTile,
    selected: Boolean = true,
    tintOverride: Color? = null
) {
    val isDark = FrictionColors.useDarkPalette
    val accent = iconId.accentColor
    val effectiveTint = tintOverride ?: when {
        showGlassTile && isDark -> Color.White
        showGlassTile && !isDark -> accent
        selected -> accent
        else -> FrictionColors.TextMuted
    }

    if (!showGlassTile) {
        if (iconId == FocusGuardIconId.BrandShieldBrain) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = contentDescription,
                modifier = modifier.size(size)
            )
        } else {
            Icon(
                imageVector = iconId.vector ?: Icons.Rounded.VerifiedUser,
                contentDescription = contentDescription,
                tint = effectiveTint,
                modifier = modifier.size(size)
            )
        }
        return
    }

    val shape = RoundedCornerShape(14.dp)
    val tileBrush = Brush.linearGradient(
        colors = if (isDark) {
            listOf(
                accent.copy(alpha = if (selected) 0.30f else 0.16f),
                Color(0xFF141C2B).copy(alpha = 0.85f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.95f),
                accent.copy(alpha = if (selected) 0.18f else 0.10f)
            )
        }
    )
    val borderColor = accent.copy(alpha = if (selected) 0.65f else 0.32f)
    val glowModifier = if (showGlow) {
        Modifier.shadow(
            elevation = if (selected) 10.dp else 4.dp,
            shape = shape,
            clip = false,
            ambientColor = accent.copy(alpha = 0.30f),
            spotColor = accent.copy(alpha = 0.30f)
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(tileSize)
            .then(glowModifier)
            .clip(shape)
            .background(tileBrush)
            .border(1.dp, borderColor, shape),
        contentAlignment = Alignment.Center
    ) {
        if (iconId == FocusGuardIconId.BrandShieldBrain) {
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = contentDescription,
                modifier = Modifier.size(tileSize * 0.82f)
            )
        } else {
            Icon(
                imageVector = iconId.vector ?: Icons.Rounded.VerifiedUser,
                contentDescription = contentDescription,
                tint = effectiveTint,
                modifier = Modifier.size(size)
            )
        }
    }
}
