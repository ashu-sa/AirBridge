package com.vibebridge.fitbit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.vibebridge.fitbit.calls.CallEventHandler

/** Manifest receiver remains available when Android starts the app for a cellular call. */
class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> TelephonyManager.CALL_STATE_RINGING
            TelephonyManager.EXTRA_STATE_OFFHOOK -> TelephonyManager.CALL_STATE_OFFHOOK
            TelephonyManager.EXTRA_STATE_IDLE -> TelephonyManager.CALL_STATE_IDLE
            else -> return
        }
        CallEventHandler.handle(context, state, "phone-state broadcast")
    }
}
