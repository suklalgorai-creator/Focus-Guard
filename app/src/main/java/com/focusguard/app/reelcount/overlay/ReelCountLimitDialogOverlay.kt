package com.focusguard.app.reelcount.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.reelcount.integration.ReelCountCoordinator
import com.focusguard.app.reelcount.integration.ReelLimitPrompt
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.theme.GlassTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Displays the one-time-per-day "Daily Reels limit reached" decision dialog over Instagram
 * when the user first crosses their configured daily limit (`ASK` behavior).
 */
class ReelCountLimitDialogOverlay(
    private val context: Context,
    private val coordinator: ReelCountCoordinator,
    private val prefs: FocusGuardPrefs,
    private val onExitReelsRequested: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var observeJob: Job? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayComposeLifecycleOwner? = null
    private var isAttached = false

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun start() {
        if (observeJob != null) return
        observeJob = scope.launch {
            coordinator.pendingLimitPrompt.collect { prompt ->
                if (prompt != null) {
                    showPrompt(prompt)
                } else {
                    hide()
                }
            }
        }
    }

    private fun showPrompt(prompt: ReelLimitPrompt) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission missing; cannot show daily Reels limit dialog")
            return
        }
        hide()

        val owner = OverlayComposeLifecycleOwner().also { it.onCreate() }
        lifecycleOwner = owner
        composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                FocusGuardTheme(darkTheme = prefs.isDarkThemeEnabled) {
                    LimitDecisionDialogCard(
                        prompt = prompt,
                        onBlockReels = {
                            coordinator.onUserChoseBlockReels(prompt.dateKey)
                            hide()
                            onExitReelsRequested()
                        },
                        onContinueWatching = {
                            coordinator.onUserChoseContinueWatching(prompt.dateKey)
                            hide()
                        }
                    )
                }
            }
        }

        try {
            windowManager.addView(composeView, layoutParams)
            isAttached = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to show daily Reels limit dialog: ${e.message}")
            hide()
        }
    }

    fun hide() {
        val view = composeView
        if (isAttached && view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
                .onFailure { runCatching { windowManager.removeView(view) } }
        }
        isAttached = false
        composeView = null
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
    }

    fun destroy() {
        observeJob?.cancel()
        observeJob = null
        hide()
        scope.cancel()
    }

    companion object {
        private const val TAG = "ReelLimitDialog"
    }
}

@Composable
private fun LimitDecisionDialogCard(
    prompt: ReelLimitPrompt,
    onBlockReels: () -> Unit,
    onContinueWatching: () -> Unit
) {
    val accent = GlassTokens.Accents.ReelCount
    val remaining = (prompt.dailyLimit - prompt.viewedCount).coerceAtLeast(0)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(FrictionColors.Surface)
                .border(1.25.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Daily Reels limit reached",
                    color = FrictionColors.TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "You've reached today's Reels goal. Choose how you want to handle the rest of today.",
                color = FrictionColors.TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FrictionColors.SurfaceLight)
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricColumn("Viewed", prompt.viewedCount.toString(), accent)
                MetricColumn("Limit", prompt.dailyLimit.toString(), FrictionColors.TextPrimary)
                MetricColumn("Remaining", remaining.toString(), FrictionColors.TextSecondary)
            }

            Button(
                onClick = onBlockReels,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "  Block Reels for Today",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            OutlinedButton(
                onClick = onContinueWatching,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Continue Watching",
                    color = FrictionColors.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = valueColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = FrictionColors.TextMuted,
            fontSize = 12.sp
        )
    }
}
