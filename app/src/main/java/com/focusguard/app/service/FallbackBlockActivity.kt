package com.focusguard.app.service

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.app.MainActivity

class FallbackBlockActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cancelFallbackNotification()

        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL)
            ?: intent.getStringExtra(EXTRA_PACKAGE_NAME)
            ?: "Distracting app"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.rgb(12, 12, 20))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Focus Guard blocked $appLabel"
            setTextColor(Color.WHITE)
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 18)
        }

        val body = TextView(this).apply {
            text = "Accessibility is unavailable, so this is the fallback interruption. Re-enable Accessibility for full blocking and PYQ challenges."
            setTextColor(0xFFC9C9D6.toInt())
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 34)
        }

        val openAppButton = Button(this).apply {
            text = "Open Focus Guard"
            isAllCaps = false
            setOnClickListener {
                startActivity(
                    Intent(this@FallbackBlockActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
                finish()
            }
        }

        val homeButton = Button(this).apply {
            text = "Return Home"
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                finish()
            }
        }

        root.addView(title, matchWidthWrapContent())
        root.addView(body, matchWidthWrapContent())
        root.addView(openAppButton, matchWidthWrapContent())
        root.addView(homeButton, matchWidthWrapContent())
        setContentView(root)
    }

    private fun matchWidthWrapContent(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 12
        }
    }

    private fun cancelFallbackNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(FALLBACK_ALERT_ID)
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_LABEL = "extra_app_label"
        private const val FALLBACK_ALERT_ID = 2002
    }
}
