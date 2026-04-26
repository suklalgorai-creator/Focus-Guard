package com.focusguard.app.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import com.focusguard.app.R

/**
 * Manages the full-screen blocking overlay.
 *
 * CRITICAL DESIGN: The overlay view is PRE-INITIALIZED at service start
 * so that showing it is a single WindowManager.addView() call with zero
 * inflation delay. This ensures the overlay appears BEFORE the user can
 * interact with the blocked app.
 */
class OverlayManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Pre-inflated overlay — ready to show instantly
    private var overlayView: View? = null
    private var isShowing = false

    // Layout params for full-screen blocking overlay
    private val overlayParams: WindowManager.LayoutParams by lazy {
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE is intentionally REMOVED so overlay intercepts all input
            // FLAG_LAYOUT_IN_SCREEN ensures it covers status bar area too
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Ensure overlay appears on top of everything
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    /**
     * Pre-initialize the overlay view at service startup.
     * This eliminates inflation delay when we need to show it.
     */
    @SuppressLint("InflateParams")
    fun preInitialize() {
        if (overlayView == null) {
            overlayView = LayoutInflater.from(context)
                .inflate(R.layout.overlay_friction, null)
            Log.d(TAG, "Overlay pre-initialized and ready")
        }
    }

    /**
     * Show the blocking overlay INSTANTLY.
     * Because the view is pre-inflated, this is just a WindowManager.addView() call.
     */
    fun show(): Boolean {
        val existingView = overlayView
        if (isShowing && existingView?.isAttachedToWindow == true) return true
        if (isShowing) {
            Log.w(TAG, "Overlay state said showing, but view is detached. Re-attaching.")
            detachStaleOverlay(existingView)
            isShowing = false
            overlayView = null
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.e(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission missing")
            return false
        }
        return try {
            val view = overlayView ?: run {
                preInitialize()
                overlayView!!
            }
            if (view.isAttachedToWindow) {
                isShowing = true
                return true
            }
            if (view.parent != null) {
                detachStaleOverlay(view)
                overlayView = null
                preInitialize()
            }
            val viewToAdd = overlayView ?: return false
            windowManager.addView(viewToAdd, overlayParams)
            isShowing = true
            Log.d(TAG, "Overlay shown — app blocked")
            true
        } catch (e: Exception) {
            isShowing = false
            Log.e(TAG, "Failed to show overlay: ${e.message}", e)
            false
        }
    }

    /**
     * Hide the overlay (when friction is passed or app is closed).
     */
    fun hide() {
        if (!isShowing) return
        try {
            overlayView?.let { windowManager.removeView(it) }
            Log.d(TAG, "Overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide overlay: ${e.message}", e)
        } finally {
            isShowing = false
        }
    }

    /**
     * Get the overlay root view for friction layers to manipulate.
     */
    fun getOverlayView(): View? = overlayView

    /**
     * Get the friction container (where layer UIs are displayed).
     */
    fun getFrictionContainer(): ViewGroup? =
        overlayView?.findViewById(R.id.friction_container)

    // ── Convenience accessors for overlay UI elements ──

    fun getPrimaryMessage(): TextView? =
        overlayView?.findViewById(R.id.text_primary_message)

    fun getSecondaryMessage(): TextView? =
        overlayView?.findViewById(R.id.text_secondary_message)

    fun getProgressBar(): ProgressBar? =
        overlayView?.findViewById(R.id.progress_bar)

    fun getCountdownText(): TextView? =
        overlayView?.findViewById(R.id.text_countdown)

    fun getTaskContainer(): FrameLayout? =
        overlayView?.findViewById(R.id.task_container)

    fun getOptionsContainer(): LinearLayout? =
        overlayView?.findViewById(R.id.options_container)

    fun getOptionButton(option: String): Button? = when(option.uppercase()) {
        "A" -> overlayView?.findViewById(R.id.btn_option_a)
        "B" -> overlayView?.findViewById(R.id.btn_option_b)
        "C" -> overlayView?.findViewById(R.id.btn_option_c)
        "D" -> overlayView?.findViewById(R.id.btn_option_d)
        else -> null
    }

    fun getInputField(): EditText? =
        overlayView?.findViewById(R.id.input_answer)

    fun getSubmitButton(): Button? =
        overlayView?.findViewById(R.id.btn_submit)

    fun getAttemptInfo(): TextView? =
        overlayView?.findViewById(R.id.text_attempt_info)

    fun isOverlayShowing(): Boolean = isShowing

    /**
     * Destroy overlay completely (service shutdown).
     */
    fun destroy() {
        hide()
        overlayView = null
    }

    private fun detachStaleOverlay(view: View?) {
        if (view == null) return
        try {
            if (view.isAttachedToWindow || view.parent != null) {
                windowManager.removeViewImmediate(view)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to detach stale overlay before re-add: ${e.message}")
        }
    }

    /**
     * Reset all overlay UI elements to default state.
     * Called before starting a new friction session.
     */
    fun resetUI() {
        getPrimaryMessage()?.text = ""
        getSecondaryMessage()?.text = ""
        getProgressBar()?.apply {
            progress = 0
            visibility = View.GONE
        }
        getCountdownText()?.apply {
            text = ""
            visibility = View.GONE
        }
        getTaskContainer()?.apply {
            removeAllViews()
            visibility = View.GONE
        }
        getOptionsContainer()?.visibility = View.GONE
        listOf("A", "B", "C", "D").forEach { opt ->
            getOptionButton(opt)?.apply {
                isEnabled = true
                setBackgroundColor(0xFF212130.toInt()) // Reset color
            }
        }
        getInputField()?.apply {
            setText("")
            visibility = View.GONE
        }
        getSubmitButton()?.visibility = View.GONE
    }

    companion object {
        private const val TAG = "OverlayManager"
    }
}
