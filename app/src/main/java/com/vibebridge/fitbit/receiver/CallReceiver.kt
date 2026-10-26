package com.vibebridge.fitbit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
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
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

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
                    isCall = true
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
}
