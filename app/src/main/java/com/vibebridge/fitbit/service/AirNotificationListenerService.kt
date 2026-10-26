package com.vibebridge.fitbit.service

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vibebridge.fitbit.AirBridgeNotificationSettings
import timber.log.Timber

/**
 * Intercepts user-selected app notifications (WhatsApp, Telegram, Slack, etc.)
 * and routes them directly to Fitbit Air via pure BLE.
 */
class AirNotificationListenerService : NotificationListenerService() {
    private val recentNotifications = LinkedHashMap<String, Long>()
    private val lastAlertTimePerApp = HashMap<String, Long>()
    private var lastGlobalAlertTime = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.i("Fitbit Vibe Notification Listener connected")
    }

    override fun onListenerDisconnected() {
        Timber.w("Fitbit Vibe Notification Listener disconnected")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, AirNotificationListenerService::class.java))
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return

        if (!com.vibebridge.fitbit.ble.AirBridgeDeviceSettings.isNotificationAlertsEnabled(this)) {
            Timber.d("Notification vibration alerts globally disabled by user")
            return
        }

        val rule = AirBridgeNotificationSettings.findRule(this, sbn.packageName) ?: return
        if (!rule.isEnabled) {
            Timber.d("Ignored notification from disabled app: %s", sbn.packageName)
            return
        }

        val notification = sbn.notification ?: return
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return

        val extras = notification.extras
        val contentSignature = listOf(
            sbn.packageName,
            sbn.key ?: "${sbn.id}:${sbn.tag.orEmpty()}",
            extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        ).joinToString("\u0000")

        if (isRecentDuplicate(contentSignature)) return
        if (isRateLimited(rule.packageName)) return

        val trigger = AirNotificationTrigger(rule.packageName, rule.pattern, isCall = false)
        val accepted = AirBridgeOneShotService.enqueue(this, trigger)
        Timber.i(
            "Notification intercepted pkg=%s pattern=%s accepted=%s",
            rule.packageName,
            rule.pattern.wireName,
            accepted
        )
    }

    private fun isRateLimited(packageName: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        synchronized(lastAlertTimePerApp) {
            val lastApp = lastAlertTimePerApp[packageName] ?: 0L
            if (now - lastApp < PER_APP_COOLDOWN_MILLIS) {
                Timber.d("Rate limiting notification buzz for %s (app cooldown active)", packageName)
                return true
            }
            if (now - lastGlobalAlertTime < GLOBAL_COOLDOWN_MILLIS) {
                Timber.d("Rate limiting notification buzz (global cooldown active)")
                return true
            }
            lastAlertTimePerApp[packageName] = now
            lastGlobalAlertTime = now
            return false
        }
    }

    private fun isRecentDuplicate(signature: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        synchronized(recentNotifications) {
            recentNotifications.entries.removeAll { now - it.value > DUPLICATE_WINDOW_MILLIS }
            val previous = recentNotifications[signature]
            if (previous != null && now - previous < DUPLICATE_WINDOW_MILLIS) return true
            recentNotifications[signature] = now
            while (recentNotifications.size > MAX_RECENT_NOTIFICATIONS) {
                val firstKey = recentNotifications.keys.firstOrNull() ?: break
                recentNotifications.remove(firstKey)
            }
        }
        return false
    }

    companion object {
        private const val DUPLICATE_WINDOW_MILLIS = 1_500L
        private const val PER_APP_COOLDOWN_MILLIS = 3_000L
        private const val GLOBAL_COOLDOWN_MILLIS = 1_500L
        private const val MAX_RECENT_NOTIFICATIONS = 512
    }
}
