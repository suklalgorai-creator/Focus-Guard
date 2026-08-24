package com.focusguard.app.integration.studyflow

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object StudyFlowOverlayRenderer {

    fun render(container: FrameLayout?, snapshot: StudyFlowDaySnapshot?) {
        if (container == null) return

        container.removeAllViews()

        val safeSnapshot = snapshot?.takeIf {
            it.headline?.isNotBlank() == true ||
                it.focusPrompt?.isNotBlank() == true ||
                it.pendingItems.isNotEmpty()
        } ?: run {
            container.visibility = View.GONE
            return
        }

        val context = container.context
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 18).toFloat()
                setColor(0xE6172136.toInt())
                setStroke(dp(context, 1), 0x4D60A5FA.toInt())
            }
        }

        card.addView(
            TextView(context).apply {
                text = "StudyFlow says focus here"
                setTextColor(0xFFFFB347.toInt())
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                letterSpacing = 0.06f
            }
        )

        safeSnapshot.headline?.takeIf { it.isNotBlank() }?.let { headline ->
            card.addView(
                TextView(context).apply {
                    text = headline
                    setTextColor(0xFFF0F0F5.toInt())
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, dp(context, 8), 0, 0)
                }
            )
        }

        safeSnapshot.focusPrompt?.takeIf { it.isNotBlank() }?.let { prompt ->
            card.addView(
                TextView(context).apply {
                    text = prompt
                    setTextColor(0xCCF0F0F5.toInt())
                    textSize = 13f
                    setLineSpacing(0f, 1.1f)
                    setPadding(0, dp(context, 6), 0, 0)
                }
            )
        }

        safeSnapshot.pendingItems.take(3).forEachIndexed { index, item ->
            card.addView(
                buildPendingItemRow(context, item).apply {
                    val params = layoutParams as LinearLayout.LayoutParams
                    params.topMargin = if (index == 0) dp(context, 14) else dp(context, 10)
                    layoutParams = params
                }
            )
        }

        container.addView(
            card,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        container.visibility = View.VISIBLE
    }

    private fun buildPendingItemRow(
        context: Context,
        item: StudyFlowPendingItem
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )

            addView(
                TextView(context).apply {
                    minWidth = dp(context, 58)
                    gravity = Gravity.CENTER
                    text = when (item.kind.lowercase()) {
                        "revision" -> "REV"
                        else -> item.subject?.take(3)?.uppercase() ?: "TASK"
                    }
                    textSize = 10.5f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(0xFFDBEAFE.toInt())
                    background = GradientDrawable().apply {
                        cornerRadius = dp(context, 999).toFloat()
                        setColor(0x332563EB.toInt())
                        setStroke(dp(context, 1), 0x665FA8FF.toInt())
                    }
                    setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
                }
            )

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        weight = 1f
                        marginStart = dp(context, 10)
                    }

                    addView(
                        TextView(context).apply {
                            text = item.title
                            setTextColor(0xFFF8FAFC.toInt())
                            textSize = 13.5f
                            setTypeface(typeface, Typeface.BOLD)
                            maxLines = 2
                        }
                    )

                    val meta = buildMetaLine(item)
                    if (meta.isNotBlank()) {
                        addView(
                            TextView(context).apply {
                                text = meta
                                setTextColor(0xFF94A3B8.toInt())
                                textSize = 11.5f
                                setPadding(0, dp(context, 4), 0, 0)
                                maxLines = 2
                            }
                        )
                    }
                }
            )
        }
    }

    private fun buildMetaLine(item: StudyFlowPendingItem): String {
        val parts = mutableListOf<String>()
        item.subject?.takeIf { it.isNotBlank() }?.let(parts::add)
        item.detail?.takeIf { it.isNotBlank() }?.let(parts::add)
        item.etaMinutes?.takeIf { it > 0 }?.let { parts.add("${it}m") }
        return parts.joinToString("  -  ")
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}
