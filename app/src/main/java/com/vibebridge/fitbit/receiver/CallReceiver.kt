package com.vibebridge.fitbit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.vibebridge.fitbit.AirHapticPattern
import com.vibebridge.fitbit.ble.AirBridgeDeviceSettings
import com.vibebridge.fitbit.service.AirBridgeOneShotService
import com.vibebridge.fitbit.service.AirNotificationTrigger
import timber.log.Timber

/**
 * Listens for incoming phone calls and triggers the Fitbit Air BLE haptic alert
 * without any UI interruption or accessibility intervention.
 */
class CallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_CANCEL_CALL || action == "com.vibebridge.fitbit.CANCEL_CALL") {
            Timber.i("Cancel call intent received; stopping active call alert.")
            AirBridgeOneShotService.stopAll(context)
            return
        }
        if (action == ACTION_TEST_CALL) {
            val pattern = AirBridgeDeviceSettings.loadCallPattern(context)
            Timber.i("Test call intent received; enqueuing BLE alert: %s", pattern.displayName)
            val incomingNumber = intent.getStringExtra("incoming_number")
                ?: intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                ?: "+91 79786 16226"
            val trigger = AirNotificationTrigger(
                sourcePackage = "Test Call ($incomingNumber)",
                pattern = pattern,
                isCall = true,
                isContinuousCall = false
            )
            AirBridgeOneShotService.enqueue(context, trigger)
            return
        }

        if (action == ACTION_TEST_PULSE) {
            val patternStr = intent.getStringExtra("pattern") ?: "single"
            val pattern = AirHapticPattern.fromWireName(patternStr) ?: AirHapticPattern.SINGLE
            Timber.i("Test pulse intent received; enqueuing pattern: %s", pattern.displayName)
            val trigger = AirNotificationTrigger(
                sourcePackage = "Manual Test",
                pattern = pattern,
                isCall = false,
                isContinuousCall = false
            )
            AirBridgeOneShotService.enqueue(context, trigger)
            return
        }

        if (action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        Timber.i("CallReceiver phone state: %s", state)

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                if (!AirBridgeDeviceSettings.isCallAlertEnabled(context)) {
                    Timber.d("Call alerts are disabled in settings")
                    return
                }

                @Suppress("DEPRECATION")
                val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                val callSource = if (!incomingNumber.isNullOrBlank()) "Call ($incomingNumber)" else "Incoming Call"

                val pattern = AirBridgeDeviceSettings.loadCallPattern(context)
                Timber.i("Incoming call detected! Enqueuing BLE alert with pattern: %s", pattern.displayName)

                val trigger = AirNotificationTrigger(
                    sourcePackage = callSource,
                    pattern = pattern,
                    isCall = true,
                    isContinuousCall = true
                )
                AirBridgeOneShotService.enqueue(context, trigger)
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK,
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Timber.i("Call state transitioned to %s; cancelling active call alert.", state)
                AirBridgeOneShotService.cancelCall(context)
            }
        }
    }

    companion object {
        const val ACTION_TEST_CALL = "com.vibebridge.fitbit.TEST_CALL"
        const val ACTION_TEST_PULSE = "com.vibebridge.fitbit.TEST_PULSE"
        const val ACTION_CANCEL_CALL = "com.vibebridge.fitbit.CANCEL_CALL"
    }
}
