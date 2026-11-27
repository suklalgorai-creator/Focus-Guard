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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.focusguard.app.persistence.FocusGuardPrefs
import com.focusguard.app.reelcount.data.ReelCountRepository
import com.focusguard.app.reelcount.domain.ReelCountDailyStats
import com.focusguard.app.reelcount.domain.ReelCountSettings
import com.focusguard.app.reelcount.integration.ReelCountCoordinator
import com.focusguard.app.ui.theme.FocusGuardTheme
import com.focusguard.app.ui.theme.FrictionColors
import com.focusguard.app.ui.theme.GlassTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Lightweight LifecycleOwner + SavedStateRegistryOwner for ComposeView overlays
 * hosted inside AccessibilityService without requiring a separate foreground service.
 */
internal class OverlayComposeLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

/**
 * Compact, draggable, theme-aware glassmorphic floating counter shown ONLY while
 * Instagram is foregrounded and both Reel Count & Floating Counter toggles are enabled.
 */
class ReelCountFloatingOverlay(
    private val context: Context,
    private val repository: ReelCountRepository,
    private val coordinator: ReelCountCoordinator,
    private val prefs: FocusGuardPrefs
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var observeJob: Job? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayComposeLifecycleOwner? = null
    private var isAttached = false

    // Preserve drag position for the active service session.
    private var lastX = 24
    private var lastY = 180

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        x = lastX
        y = lastY
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    fun start() {
        if (observeJob != null) return
        observeJob = scope.launch {
            combine(
                repository.settingsFlow,
                coordinator.isInstagramForeground
            ) { settings, isInstagram ->
                settings.enabled && settings.floatingCounterEnabled && isInstagram
            }.collect { shouldShow ->
                if (shouldShow) {
                    show()
                } else {
                    hide()
                }
            }
        }
    }

    fun show() {
        if (isAttached && composeView?.isAttachedToWindow == true) return
        if (!Settings.canDrawOverlays(context)) {
            hide()
            return
        }

        if (composeView == null) {
            val owner = OverlayComposeLifecycleOwner().also { it.onCreate() }
            lifecycleOwner = owner
            composeView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setContent {
                    val settings by repository.settingsFlow.collectAsStateWithLifecycle()
                    val stats by repository.todayStatsFlow.collectAsStateWithLifecycle(
                        initialValue = ReelCountDailyStats(dateKey = repository.currentDateKey())
                    )
                    FocusGuardTheme(darkTheme = prefs.isDarkThemeEnabled) {
                        FloatingCounterPill(
                            viewedCount = stats.viewedCount,
                            settings = settings,
                            onDrag = { dx, dy -> updatePosition(dx, dy) }
                        )
                    }
                }
            }
        }

        try {
            layoutParams.x = lastX
            layoutParams.y = lastY
            windowManager.addView(composeView, layoutParams)
            isAttached = true
        } catch (e: Exception) {
            Log.w(TAG, "Unable to attach floating counter overlay: ${e.message}")
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

    private fun updatePosition(dx: Float, dy: Float) {
        val view = composeView ?: return
        if (!isAttached) return
        // Gravity is TOP | END, so dragging right decreases x offset from the right edge.
        lastX = (lastX - dx.roundToInt()).coerceIn(0, 600)
        lastY = (lastY + dy.roundToInt()).coerceIn(60, 1800)
        layoutParams.x = lastX
        layoutParams.y = lastY
        runCatching { windowManager.updateViewLayout(view, layoutParams) }
    }

    companion object {
        private const val TAG = "ReelCountOverlay"
    }
}

@Composable
private fun FloatingCounterPill(
    viewedCount: Int,
    settings: ReelCountSettings,
    onDrag: (dx: Float, dy: Float) -> Unit
) {
    val accent = GlassTokens.Accents.ReelCount
    val hasLimit = settings.limitEnabled && settings.dailyLimit > 0
    val progress = if (hasLimit) {
        (viewedCount.toFloat() / settings.dailyLimit.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val counterText = if (hasLimit) "$viewedCount / ${settings.dailyLimit}" else "$viewedCount"

    Column(
        modifier = Modifier
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
            .clip(RoundedCornerShape(999.dp))
            .background(FrictionColors.GlassBackground)
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.55f),
                shape = RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = "Reel Count",
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = counterText,
                color = FrictionColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (hasLimit) {
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.14f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent)
                )
            }
        }
    }
}
