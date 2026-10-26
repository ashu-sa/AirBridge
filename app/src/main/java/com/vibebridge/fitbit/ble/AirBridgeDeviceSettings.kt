package com.vibebridge.fitbit.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import com.vibebridge.fitbit.AirHapticPattern

object AirBridgeDeviceSettings {
    private const val PREFS_NAME = "air_bridge_device"
    private const val KEY_ADDRESS = "address"
    private const val KEY_NAME = "name"
    private const val KEY_LAST_STATUS = "last_status"
    private const val KEY_CALL_ALERTS_ENABLED = "call_alerts_enabled"
    private const val KEY_CALL_PATTERN = "call_pattern"
    private const val KEY_NOTIFICATION_ALERTS_ENABLED = "notification_alerts_enabled"

    const val DEFAULT_FALLBACK_ADDRESS = "5E:75:11:59:E2:43"
    const val DEFAULT_FALLBACK_NAME = "Google Fitbit Air"

    fun saveDevice(context: Context, device: BluetoothDevice) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ADDRESS, device.address)
            .putString(KEY_NAME, device.name ?: "Google Fitbit Air")
            .apply()
    }

    fun saveAddress(context: Context, address: String, name: String = "Google Fitbit Air") {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ADDRESS, address)
            .putString(KEY_NAME, name)
            .apply()
    }

    fun loadAddress(context: Context): String? {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ADDRESS, null)
            ?.takeIf(String::isNotBlank)
        return saved ?: DEFAULT_FALLBACK_ADDRESS
    }

    fun loadDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_NAME, DEFAULT_FALLBACK_NAME) ?: DEFAULT_FALLBACK_NAME
    }

    fun loadDeviceLabel(context: Context): String {
        val addr = loadAddress(context) ?: return "No Device Paired"
        val name = loadDeviceName(context)
        return "$name ($addr)"
    }

    fun saveLastStatus(context: Context, status: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_STATUS, status)
            .apply()
    }

    fun loadLastStatus(context: Context): String = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LAST_STATUS, "Ready (Idle)")
        ?: "Ready (Idle)"

    fun isCallAlertEnabled(context: Context): Boolean = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_CALL_ALERTS_ENABLED, true)

    fun setCallAlertEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CALL_ALERTS_ENABLED, enabled)
            .apply()
    }

    fun loadCallPattern(context: Context): AirHapticPattern {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CALL_PATTERN, AirHapticPattern.URGENT.wireName)
        return raw?.let(AirHapticPattern::fromWireName) ?: AirHapticPattern.URGENT
    }

    fun setCallPattern(context: Context, pattern: AirHapticPattern) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CALL_PATTERN, pattern.wireName)
            .apply()
    }

    fun isNotificationAlertsEnabled(context: Context): Boolean = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_NOTIFICATION_ALERTS_ENABLED, true)

    fun setNotificationAlertsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_NOTIFICATION_ALERTS_ENABLED, enabled)
            .apply()
    }

    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    fun isOnboardingCompleted(context: Context): Boolean = context
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_ONBOARDING_COMPLETED, false)

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
            .apply()
    }
}
