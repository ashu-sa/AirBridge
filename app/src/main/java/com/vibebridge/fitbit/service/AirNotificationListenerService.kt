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
import com.vibebridge.fitbit.calls.*

/**
 * Intercepts user-selected app notifications (WhatsApp, Telegram, Slack, etc.)
 * and routes them directly to Fitbit Air via pure BLE.
 */
class AirNotificationListenerService : NotificationListenerService() {
    private val alertGate = NotificationAlertGate()

    override fun onListenerConnected() {
        super.onListenerConnected()
        com.vibebridge.fitbit.calls.PhoneCallMonitor.refresh(this)
        com.vibebridge.fitbit.ble.BleSessionDiagnostics.listenerConnected = true
        Timber.i("Fitbit Vibe Notification Listener connected")
    }

    override fun onListenerDisconnected() {
        AppCallSessions.notifications.clear()
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
        com.vibebridge.fitbit.ble.BleSessionDiagnostics.lastObservedPackage = sbn.packageName
        com.vibebridge.fitbit.ble.BleSessionDiagnostics.notificationsObserved.incrementAndGet()
        val notification = sbn.notification ?: return
        val callType = runCatching {
            if (notification.extras.containsKey("android.callType")) notification.extras.getInt("android.callType", -1) else null
        }.getOrDefault(-1)
        val call = runCatching { CallNotificationMetadata(notification.category, callType,
            notification.fullScreenIntent != null, notification.actions?.size ?: 0,
            notification.flags and Notification.FLAG_INSISTENT != 0,
            notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false))
        }.getOrElse { CallNotificationMetadata(callType = -1) }
        if (call.isCallRelated) {
            if (call.isIncoming()) {
                CallingAppsDiscovery.recordCallNotification(this, sbn.packageName)
            }
            if (!call.isIncoming()) {
                AppCallSessions.notifications.end(sbn.key)
                return
            }
            val config = AppCallSettings.load(this)
            if (!config.allows(sbn.packageName)) {
                AppCallSessions.notifications.end(sbn.key)
                return
            }
            if (AlertScheduleStore.blockReason(this) != null) return
            val token = AppCallSessions.notifications.begin(sbn.key, sbn.packageName, SystemClock.elapsedRealtime()) ?: return
            com.vibebridge.fitbit.ble.BleSessionDiagnostics.appCallAttempts.incrementAndGet()
            AirBridgeOneShotService.enqueue(this, AirNotificationTrigger(sbn.packageName, config.pattern, appCallToken = token))
            return // A call never also takes the ordinary-message route.
        }

        AppCallSessions.notifications.end(sbn.key)
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

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { AppCallSessions.notifications.end(it.key) }
    }

    override fun onDestroy() {
        AppCallSessions.notifications.clear()
        com.vibebridge.fitbit.ble.BleSessionDiagnostics.listenerConnected = false
        super.onDestroy()
    }

}
