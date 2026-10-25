package com.vibebridge.fitbit

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class AirNotificationListenerService : NotificationListenerService() {

    private val recentNotifications = LinkedHashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Air notification listener connected and active.")
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "Air notification listener disconnected.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, AirNotificationListenerService::class.java))
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return // Ignore self

        val notification = sbn.notification ?: return
        // Ignore group summary or ongoing background notifications (e.g. music playing)
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        // Deduplication signature matching paceaitian's spec
        val contentSignature = listOf(
            sbn.packageName,
            sbn.key ?: "${sbn.id}:${sbn.tag.orEmpty()}",
            title,
            text,
            bigText
        ).joinToString("\u0000")

        if (isRecentDuplicate(contentSignature)) return

        // 1. Check if package has an explicit rule
        val rule = AirBridgeNotificationSettings.findRule(this, sbn.packageName)

        // 2. Check if it is a phone/VOIP call (e.g. WhatsApp Call, Telegram Call, Dialer Call)
        val isCallCategory = notification.category == Notification.CATEGORY_CALL
        val pattern = when {
            rule != null -> rule.pattern
            isCallCategory -> AirHapticPattern.URGENT
            else -> null
        }

        if (pattern != null) {
            val label = if (title.isNotEmpty()) title else sbn.packageName
            Log.i(
                TAG,
                "Notification alert triggered: package=${sbn.packageName} pattern=${pattern.wireName} title='$title'"
            )
            CallReceiver.acquireWakeLock(this)
            FitbitVibeAccessibilityService.executePattern(this, pattern, label)
        }
    }

    private fun isRecentDuplicate(signature: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        synchronized(recentNotifications) {
            recentNotifications.entries.removeAll { now - it.value > DUPLICATE_WINDOW_MILLIS }
            val previous = recentNotifications[signature]
            if (previous != null && now - previous < DUPLICATE_WINDOW_MILLIS) {
                return true
            }
            recentNotifications[signature] = now
            while (recentNotifications.size > MAX_RECENT_NOTIFICATIONS) {
                val firstKey = recentNotifications.keys.firstOrNull() ?: break
                recentNotifications.remove(firstKey)
            }
        }
        return false
    }

    companion object {
        private const val TAG = "AirNotifListener"
        private const val DUPLICATE_WINDOW_MILLIS = 1_500L
        private const val MAX_RECENT_NOTIFICATIONS = 512
    }
}
