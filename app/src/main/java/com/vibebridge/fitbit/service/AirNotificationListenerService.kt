package com.vibebridge.fitbit.service

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.vibebridge.fitbit.schedule.AlertScheduleStore
import com.vibebridge.fitbit.AirBridgeNotificationSettings
import timber.log.Timber

/**
 * Intercepts user-selected app notifications (WhatsApp, Telegram, Slack, etc.)
 * and routes them directly to Fitbit Air via pure BLE.
 */
class AirNotificationListenerService : NotificationListenerService() {
    private val alertGate = NotificationAlertGate()

    override fun onListenerConnected() {
        super.onListenerConnected()
        com.vibebridge.fitbit.ble.BleSessionDiagnostics.listenerConnected = true
        Timber.i("Fitbit Vibe Notification Listener connected")
    }

    override fun onListenerDisconnected() {
        com.vibebridge.fitbit.ble.BleSessionDiagnostics.listenerConnected = false
        Timber.w("Fitbit Vibe Notification Listener disconnected")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, AirNotificationListenerService::class.java))
        }
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName == packageName) return
        com.vibebridge.fitbit.ble.BleSessionDiagnostics.notificationsObserved.incrementAndGet()
        if (AlertScheduleStore.blockReason(this) != null) {
            com.vibebridge.fitbit.ble.BleSessionDiagnostics.lastScheduleBlockedPackage = sbn.packageName
            com.vibebridge.fitbit.ble.BleSessionDiagnostics.notificationsScheduleBlocked.incrementAndGet()
            return
        }

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

        val signature = "${sbn.key}:${sbn.postTime}"
        val now = SystemClock.elapsedRealtime()
        if (!alertGate.canAlert(signature, rule.packageName, now)) return

        val trigger = AirNotificationTrigger(rule.packageName, rule.pattern, isCall = false)
        val accepted = AirBridgeOneShotService.enqueue(this, trigger)
        if (accepted) alertGate.recordAccepted(signature, rule.packageName, now)
        Timber.i(
            "Notification intercepted pkg=%s pattern=%s accepted=%s",
            rule.packageName,
            rule.pattern.wireName,
            accepted
        )
    }

    override fun onDestroy() {
        com.vibebridge.fitbit.ble.BleSessionDiagnostics.listenerConnected = false
        super.onDestroy()
    }

}
