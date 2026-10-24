package com.vibebridge.fitbit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {

    companion object {
        private var wakeLock: PowerManager.WakeLock? = null

        @Synchronized
        fun acquireWakeLock(context: Context) {
            try {
                if (wakeLock == null) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    @Suppress("DEPRECATION")
                    wakeLock = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                                PowerManager.ON_AFTER_RELEASE,
                        "fitbitvibe:CallWakeLock"
                    ).apply {
                        setReferenceCounted(false)
                    }
                }
                wakeLock?.acquire(30000) // Keep screen awake up to 30s while ringing
                Log.d("FitbitCallReceiver", "Screen wake lock acquired for incoming call")
            } catch (e: Exception) {
                Log.e("FitbitCallReceiver", "Failed to acquire wake lock: ${e.message}")
            }
        }

        @Synchronized
        fun releaseWakeLock() {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                    Log.d("FitbitCallReceiver", "Screen wake lock released")
                }
            } catch (e: Exception) {
                Log.e("FitbitCallReceiver", "Failed to release wake lock: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            Log.d("FitbitCallReceiver", "Phone state changed: $state")

            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    Log.i("FitbitCallReceiver", "Incoming call ringing from: $incomingNumber -> Waking screen & Starting continuous vibration")
                    acquireWakeLock(context)
                    FitbitVibeAccessibilityService.startContinuousRinging(context, incomingNumber)
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    Log.i("FitbitCallReceiver", "Call answered (OFFHOOK) -> Stopping vibration")
                    releaseWakeLock()
                    FitbitVibeAccessibilityService.stopContinuousRinging(context)
                }
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    Log.i("FitbitCallReceiver", "Call ended / declined (IDLE) -> Stopping vibration")
                    releaseWakeLock()
                    FitbitVibeAccessibilityService.stopContinuousRinging(context)
                }
            }
        }
    }
}
