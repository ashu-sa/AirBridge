package com.vibebridge.fitbit

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FitbitVibeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FitbitVibeBridge"
        var instance: FitbitVibeAccessibilityService? = null
        @Volatile var isRinging = false
        var lastForegroundPackage: String? = null
        private val ringHandler = Handler(Looper.getMainLooper())
        private var toggleState = false

        fun startContinuousRinging(context: Context, callerNumber: String? = null) {
            val service = instance
            if (service == null) {
                Log.e(TAG, "Accessibility Service is not connected!")
                return
            }

            if (isRinging) return
            isRinging = true
            Log.i(TAG, "Call ringing! Showing overlay and starting vibration loop...")

            // 1. Show the non-intrusive floating HUD
            CallOverlayManager.showOverlay(context, callerNumber)

            // 2. Bring Fitbit to the front (if not already active/mini view)
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName("com.fitbit.FitbitMobile", "com.fitbit.HealthBrandedAlias")
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    )
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch Fitbit: ${e.message}")
            }

            // 3. Start pulsing loop immediately
            service.startPulsingLoop()
        }

        fun stopContinuousRinging(context: Context) {
            if (!isRinging) return
            isRinging = false
            ringHandler.removeCallbacksAndMessages(null)
            Log.i(TAG, "Call ended or answered. Removing overlay.")

            // Remove the Call HUD
            CallOverlayManager.hideOverlay()

            // CRITICAL: DO NOT SEND GLOBAL_ACTION_BACK!
            // Sending BACK pops the Vibration screen back to Device preferences / Home.
            // Instead, seamlessly restore the user's previously active app (WhatsApp, Telegram, etc.)
            val service = instance
            Handler(Looper.getMainLooper()).postDelayed({
                service?.restoreForeground(context)
            }, 100)
        }

        fun executePattern(context: Context, pattern: AirHapticPattern, label: String? = null) {
            val service = instance
            if (service == null) {
                Log.e(TAG, "Accessibility Service is not connected!")
                return
            }

            if (isRinging) {
                Log.i(TAG, "Call is currently ringing; ignoring notification haptic pattern.")
                return
            }

            Log.i(TAG, "Executing haptic pattern '${pattern.wireName}' (${pattern.displayName}) for: $label")

            // Show floating HUD for notification
            CallOverlayManager.showOverlay(context, label ?: "Incoming Notification")

            // Bring Fitbit to front
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName("com.fitbit.FitbitMobile", "com.fitbit.HealthBrandedAlias")
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    )
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch Fitbit: ${e.message}")
            }

            service.runPatternStep(context, pattern, 0)
        }

        fun parkOnVibrationScreen(context: Context) {
            val service = instance ?: return
            Log.i(TAG, "Pre-navigating to Vibration screen so it's parked and ready...")
            try {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    component = ComponentName("com.fitbit.FitbitMobile", "com.fitbit.HealthBrandedAlias")
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch Fitbit: ${e.message}")
            }
            service.triggerAutoNavigation()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "FitbitVibeAccessibilityService connected and ready.")
        try {
            sendBroadcast(Intent("com.vibebridge.fitbit.SERVICE_STATE_CHANGED").setPackage(packageName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()
        if (!pkg.isNullOrEmpty() &&
            pkg != "com.fitbit.FitbitMobile" &&
            !pkg.contains("systemui")
        ) {
            lastForegroundPackage = pkg
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Service interrupted")
        isRinging = false
        ringHandler.removeCallbacksAndMessages(null)
        CallOverlayManager.hideOverlay()
        try {
            sendBroadcast(Intent("com.vibebridge.fitbit.SERVICE_STATE_CHANGED").setPackage(packageName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        isRinging = false
        ringHandler.removeCallbacksAndMessages(null)
        CallOverlayManager.hideOverlay()
        try {
            sendBroadcast(Intent("com.vibebridge.fitbit.SERVICE_STATE_CHANGED").setPackage(packageName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Finds Fitbit root node across all active windows (works in Samsung pop-up / mini view and split screen)
    private fun getFitbitRoot(): AccessibilityNodeInfo? {
        try {
            for (window in windows) {
                val root = window.root
                if (root != null && root.packageName == "com.fitbit.FitbitMobile") {
                    return root
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enumerating windows: ${e.message}")
        }

        val active = rootInActiveWindow
        if (active != null && active.packageName == "com.fitbit.FitbitMobile") {
            return active
        }
        return null
    }

    // Compose-safe BFS node locator
    private fun findNodeByTextOrDesc(root: AccessibilityNodeInfo, query: String, exactMatch: Boolean = false): AccessibilityNodeInfo? {
        val q = query.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val matches = if (exactMatch) (text == q || desc == q) else (text.contains(q) || desc.contains(q))
            if (matches) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0 && rect.centerX() > 0 && rect.centerY() > 0) {
                    return node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun hasTextOrDesc(root: AccessibilityNodeInfo, query: String): Boolean {
        return findNodeByTextOrDesc(root, query, exactMatch = false) != null
    }

    // Precision auto-navigation through the Fitbit hierarchy to reach Vibration screen
    private fun autoNavigateStep(root: AccessibilityNodeInfo): Boolean {
        // 1. If on Vibration intensity screen
        if (hasTextOrDesc(root, "Vibration intensity")) {
            return true // Already on target Vibration intensity page!
        }

        // 2. If on Device preferences screen -> tap "Vibration" row directly
        if (hasTextOrDesc(root, "Device preferences") && !hasTextOrDesc(root, "Alarms")) {
            val vibNode = findNodeByTextOrDesc(root, "Vibration", exactMatch = true)
            if (vibNode != null) {
                val rect = Rect()
                vibNode.getBoundsInScreen(rect)
                Log.i(TAG, "Navigating: Clicking Vibration row at (${rect.centerX()}, ${rect.centerY()})")
                tapCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat(), 80)
                return false
            }
        }

        // 3. If on Fitbit Air device detail screen -> tap "Device preferences"
        if (hasTextOrDesc(root, "Alarms") || (hasTextOrDesc(root, "Synced") && hasTextOrDesc(root, "Settings"))) {
            val prefNode = findNodeByTextOrDesc(root, "Device preferences", exactMatch = true)
                ?: findNodeByTextOrDesc(root, "Device preferences", exactMatch = false)
            if (prefNode != null) {
                val rect = Rect()
                prefNode.getBoundsInScreen(rect)
                Log.i(TAG, "Navigating: Clicking Device preferences at (${rect.centerX()}, ${rect.centerY()})")
                tapCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat(), 80)
                return false
            }
        }

        // 4. If on Connections screen -> tap "Fitbit Air" row
        if (hasTextOrDesc(root, "Connections") || hasTextOrDesc(root, "Add more devices")) {
            val airNode = findNodeByTextOrDesc(root, "Fitbit Air", exactMatch = true)
                ?: findNodeByTextOrDesc(root, "Fitbit Air", exactMatch = false)
            if (airNode != null) {
                val rect = Rect()
                airNode.getBoundsInScreen(rect)
                Log.i(TAG, "Navigating: Clicking Fitbit Air row at (${rect.centerX()}, ${rect.centerY()})")
                tapCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat(), 80)
                return false
            }
        }

        // 5. If on Today tab -> tap Devices icon (top left)
        if (hasTextOrDesc(root, "Today") && (hasTextOrDesc(root, "Steps") || hasTextOrDesc(root, "Health"))) {
            val devNode = findNodeByTextOrDesc(root, "Devices and apps", exactMatch = false)
            if (devNode != null) {
                val rect = Rect()
                devNode.getBoundsInScreen(rect)
                Log.i(TAG, "Navigating: Clicking Devices and apps at (${rect.centerX()}, ${rect.centerY()})")
                tapCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat(), 80)
                return false
            }
        }

        return false
    }

    fun triggerAutoNavigation() {
        val navRunnable = object : Runnable {
            var attempts = 0
            override fun run() {
                val root = getFitbitRoot()
                if (root != null) {
                    val onVib = autoNavigateStep(root)
                    if (onVib) {
                        Log.i(TAG, "Successfully arrived and parked on Vibration screen!")
                        return
                    }
                }
                attempts++
                if (attempts < 25) {
                    ringHandler.postDelayed(this, 300)
                }
            }
        }
        ringHandler.postDelayed(navRunnable, 200)
    }

    private val pulseRunnable = object : Runnable {
        override fun run() {
            if (!isRinging) return

            try {
                val root = getFitbitRoot()
                if (root != null) {
                    val onVib = autoNavigateStep(root)
                    if (onVib) {
                        // Directly on the Vibration intensity screen!
                        val targetLabel = if (toggleState) "Low" else "High"
                        toggleState = !toggleState
                        tapTargetButton(root, targetLabel)
                        ringHandler.postDelayed(this, 750)
                        return
                    } else {
                        // Navigating to vibration screen in fast hops (200ms)
                        ringHandler.postDelayed(this, 200)
                        return
                    }
                }

                // Fallback direct tap if window tree not yet ready
                tapTargetButtonFallback(if (toggleState) "Low" else "High")
                toggleState = !toggleState
                ringHandler.postDelayed(this, 750)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in pulseRunnable: ${t.message}", t)
                ringHandler.postDelayed(this, 750)
            }
        }
    }

    fun dispatchToggle(): Boolean {
        try {
            val root = getFitbitRoot()
            val targetLabel = if (toggleState) "Low" else "High"
            toggleState = !toggleState
            if (root != null) {
                val onVib = autoNavigateStep(root)
                if (onVib) {
                    tapTargetButton(root, targetLabel)
                    return true
                }
            }
            tapTargetButtonFallback(targetLabel)
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "dispatchToggle error: ${t.message}")
            return false
        }
    }

    private fun runPatternStep(context: Context, pattern: AirHapticPattern, groupIndex: Int) {
        if (isRinging) return
        if (groupIndex >= pattern.groupCount) {
            // All pulse groups completed! Release overlay and restore foreground
            ringHandler.postDelayed({
                CallOverlayManager.hideOverlay()
                restoreForeground(context)
            }, 300)
            return
        }

        // 1. Toggle to alternate intensity (triggers first motor pulse)
        dispatchToggle()

        // 2. Wait 800ms (paceaitian's tested interval) and restore original intensity
        ringHandler.postDelayed({
            if (isRinging) return@postDelayed
            dispatchToggle()

            // 3. Delay before next pulse group
            val delayBeforeNext = if (groupIndex < pattern.betweenGroupDelaysMillis.size) {
                pattern.betweenGroupDelaysMillis[groupIndex]
            } else {
                0L
            }

            ringHandler.postDelayed({
                runPatternStep(context, pattern, groupIndex + 1)
            }, delayBeforeNext)
        }, 800)
    }

    fun restoreForeground(context: Context) {
        try {
            val prevPkg = lastForegroundPackage
            if (!prevPkg.isNullOrEmpty() && prevPkg != "com.fitbit.FitbitMobile") {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(prevPkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                    )
                    context.startActivity(launchIntent)
                    Log.i(TAG, "Restored active app: $prevPkg.")
                    return
                }
            }

            // Fallback: Accessibility Global Action BACK or HOME cleanly dismisses Fitbit on Android 14
            Log.i(TAG, "Dismissing Fitbit via Accessibility Global Action BACK/HOME")
            val backSuccess = performGlobalAction(GLOBAL_ACTION_BACK)
            if (!backSuccess) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore previous app: ${e.message}")
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun startPulsingLoop() {
        ringHandler.removeCallbacks(pulseRunnable)
        ringHandler.postDelayed(pulseRunnable, 100)
    }

    // Dynamic resolution & window-independent button tapping (works in mini view & full screen)
    private fun tapTargetButton(root: AccessibilityNodeInfo, label: String) {
        val node = findNodeByTextOrDesc(root, label, exactMatch = true)

        if (node != null) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.centerX() > 0 && rect.centerY() > 0) {
                // Programmatic click attempt
                var clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (!clicked) {
                    var parent = node.parent
                    while (parent != null && !clicked) {
                        clicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        parent = parent.parent
                    }
                }
                // Dispatch physical gesture at exact center coordinates
                tapCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat(), 80)
                Log.i(TAG, "Toggled $label dynamically at (${rect.centerX()}, ${rect.centerY()})")
                return
            }
        }

        // Relative offset fallback: calculate from "Vibration intensity" header position inside the window (perfect for mini view)
        val intensityNode = findNodeByTextOrDesc(root, "Vibration intensity", exactMatch = false)
        if (intensityNode != null) {
            val rect = Rect()
            intensityNode.getBoundsInScreen(rect)
            val targetY = if (label == "High") rect.bottom + 140f else rect.bottom + 330f
            tapCoordinates(rect.left + 60f, targetY, 80)
            Log.i(TAG, "Toggled $label via relative offset from intensity header at (${rect.left + 60f}, $targetY)")
            return
        }

        // Fallback if node text lookup returned no bounds
        tapTargetButtonFallback(label)
    }

    private fun tapTargetButtonFallback(label: String) {
        val dm = resources.displayMetrics
        val centerX = dm.widthPixels / 2f
        val targetY = if (label == "High") dm.heightPixels * 0.37f else dm.heightPixels * 0.45f
        Log.i(TAG, "Toggled $label via fallback ratio: ($centerX, $targetY)")
        tapCoordinates(centerX, targetY, 100)
    }

    private fun tapCoordinates(x: Float, y: Float, durationMs: Long = 100) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
