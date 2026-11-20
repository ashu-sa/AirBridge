package com.vibebridge.fitbit.service

import com.vibebridge.fitbit.ble.HapticsRestoreException
import java.util.concurrent.TimeoutException

/** Never invent a GATT status from an unrelated exception message. */
internal object AlertFailureMessages {
    fun describe(error: Throwable, cleanupFailed: Boolean = false): String {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val message = causes.mapNotNull { it.message }.joinToString(" ")
        return when {
            cleanupFailed -> "Bluetooth cleanup did not finish. Force stop Air Bridge in Android app settings, then reopen it."
            causes.any { it is HapticsRestoreException } -> "Band vibration setting could not be verified. Recover the vibration setting before trying again."
            causes.any { it is SecurityException } -> "Bluetooth permission is missing. Allow Nearby devices in Android settings."
            message.contains("Bluetooth is disabled", true) -> "Bluetooth is turned off. Turn it on to reconnect."
            message.contains("address not configured", true) -> "Choose your Fitbit Air using Change band before testing alerts."
            message.contains("Encrypted session failed", true) -> "Could not establish a secure session. Check the band in Google Health, then retry."
            message.contains("scan timeout", true) || message.contains("not advertising", true) -> "Fitbit Air was not detected nearby. Check that it is charged and in range."
            causes.any { it is TimeoutException } || message.contains("timed out", true) || message.contains("timeout", true) -> "Band connection timed out. Check its charge and range; Google Health may also be using it."
            Regex("\\b133\\b").containsMatchIn(message) -> "Bluetooth reported status 133. Try Reset link, then check that the band is charged and nearby."
            message.contains("GATT", true) -> "Bluetooth connection failed. Try Reset link and check the band in Google Health."
            causes.any { it is NoSuchElementException } -> "Band disconnected before the connection was ready. Check its charge and range."
            else -> "Could not complete alert (${error.javaClass.simpleName}). Check the band and try again."
        }
    }

    fun startFailure(error: Throwable): String =
        if (error.javaClass.simpleName == "ForegroundServiceStartNotAllowedException")
            "Android blocked starting a background alert. Open Air Bridge and check Battery → Unrestricted. A background toggle alone may not allow delivery on this phone."
        else "Unable to start Bluetooth alerts (${error.javaClass.simpleName}). Open Air Bridge and check permissions and background access."
}
