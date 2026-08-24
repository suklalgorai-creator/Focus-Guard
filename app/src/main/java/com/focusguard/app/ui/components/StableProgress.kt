package com.focusguard.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.focusguard.app.ui.theme.FrictionColors

@Composable
fun StableLinearProgress(
    modifier: Modifier = Modifier,
    progress: Float? = null,
    color: Color = FrictionColors.Accent,
    trackColor: Color = FrictionColors.SurfaceElevated
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((progress ?: 0.42f).coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color)
        )
    }
}
