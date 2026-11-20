package com.vibebridge.fitbit.calls

import android.content.Context
import android.telephony.TelephonyManager
import com.vibebridge.fitbit.ble.AirBridgeDeviceSettings
import com.vibebridge.fitbit.schedule.AlertScheduleStore
import com.vibebridge.fitbit.service.AirBridgeOneShotService
import com.vibebridge.fitbit.service.AirNotificationTrigger
import timber.log.Timber
import java.text.DateFormat
import java.util.Date

/** Call states only: no numbers, contacts, call log or notification text. */
object CallEventHandler {
    fun handle(context: Context, state: Int, source: String) {
        Timber.i("Phone event: state=%d source=%s", state, source)
        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                context.getSharedPreferences("air_bridge_call_diagnostics", 0).edit()
                    .putLong("last_ring", System.currentTimeMillis()).apply()
                val blocked = AlertScheduleStore.blockReason(context)
                if (blocked != null) {
                    AirBridgeDeviceSettings.saveLastStatus(context, "Incoming call: $blocked")
                    return
                }
                if (!AirBridgeDeviceSettings.isCallAlertEnabled(context)) {
                    AirBridgeDeviceSettings.saveLastStatus(context, "Incoming call: call alerts are switched off.")
                    return
                }
                val deliveryId = if (AirBridgeDeviceSettings.loadAddress(context) != null)
                    com.vibebridge.fitbit.alerts.CallAlertDelivery.begin(context) else 0L
                val accepted = AirBridgeOneShotService.enqueue(context, AirNotificationTrigger(
                    "Incoming Call", AirBridgeDeviceSettings.loadCallPattern(context),
                    isCall = true, isContinuousCall = true, deliveryId = deliveryId))
                if (!accepted) com.vibebridge.fitbit.alerts.CallAlertDelivery.complete(context, deliveryId)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> AirBridgeOneShotService.cancelCall(context)
            TelephonyManager.CALL_STATE_IDLE -> {
                AirBridgeOneShotService.cancelCall(context)
                com.vibebridge.fitbit.alerts.CallAlertDelivery.ended(context, AirBridgeOneShotService.isVibrating)
            }
        }
    }
    fun lastDetectedLabel(context: Context): String {
        val at = context.getSharedPreferences("air_bridge_call_diagnostics", 0).getLong("last_ring", 0)
        return if (at == 0L) "No incoming phone call detected yet"
        else "Last call detected: ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(at))}"
    }
}
