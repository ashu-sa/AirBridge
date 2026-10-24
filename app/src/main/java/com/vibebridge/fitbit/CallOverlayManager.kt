package com.vibebridge.fitbit

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object CallOverlayManager {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false

    fun showOverlay(context: Context, callerNumber: String? = null) {
        if (isOverlayShowing) return

        try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

            val wmParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = 80 // Float right below status bar
            }

            // Create clean, compact floating Pill HUD
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // Sleek translucent glass pill with rounded corners
                background = GradientDrawable().apply {
                    setColor(0xEE1E293B.toInt()) // Slate-800 translucent
                    cornerRadius = 60f
                    setStroke(2, 0x4438BDF8.toInt()) // Subtle cyan border
                }
                setPadding(36, 18, 44, 18)
                elevation = 16f
            }

            // Pulsing Phone Icon
            val icon = ImageView(context).apply {
                setImageResource(android.R.drawable.stat_sys_phone_call)
                setColorFilter(0xFF38BDF8.toInt()) // Cyan
                val lp = LinearLayout.LayoutParams(48, 48).apply {
                    rightMargin = 20
                }
                layoutParams = lp
            }

            // Text Label
            val label = TextView(context).apply {
                val caller = if (!callerNumber.isNullOrEmpty()) callerNumber else "Incoming Call"
                text = "$caller  •  ⚡ Vibrating Fitbit"
                textSize = 13f
                setTextColor(Color.WHITE)
                paint.isFakeBoldText = true
            }

            container.addView(icon)
            container.addView(label)

            overlayView = container
            windowManager?.addView(overlayView, wmParams)
            isOverlayShowing = true

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hideOverlay() {
        if (!isOverlayShowing || overlayView == null) return

        try {
            windowManager?.removeView(overlayView)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            isOverlayShowing = false
        }
    }
}
