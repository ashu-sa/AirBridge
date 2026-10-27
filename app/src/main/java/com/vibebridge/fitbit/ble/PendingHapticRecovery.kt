package com.vibebridge.fitbit.ble

import android.content.Context

/** Durable checkpoint: process death must not turn an uncertain restore into normal operation. */
object PendingHapticRecovery {
    private fun prefs(context: Context) = context.getSharedPreferences("air_bridge_haptic_recovery", Context.MODE_PRIVATE)
    fun get(context: Context, address: String): Int? =
        if (prefs(context).contains(address)) prefs(context).getInt(address, -1) else null
    fun mark(context: Context, address: String, original: Int) {
        check(prefs(context).edit().putInt(address, original).commit()) { "Unable to save recovery checkpoint" }
    }
    fun clear(context: Context, address: String) {
        check(prefs(context).edit().remove(address).commit()) { "Unable to clear recovery checkpoint" }
    }
}
