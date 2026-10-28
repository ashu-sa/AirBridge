package com.vibebridge.fitbit.ui

/** A saved pairing or a past success is not proof of a live AirBridge connection. */
enum class BandStatus(val label: String) {
    CONNECTED("Connected"), ACTION_REQUIRED("Action required"), FAILED("Connection failed"),
    CONNECTING("Connecting…"), READY("Ready · connects for alerts");

    companion object {
        fun resolve(permission: Boolean, bluetoothOn: Boolean, hasBand: Boolean, recoveryNeeded: Boolean,
                    connected: Boolean, busy: Boolean, lastStatus: String): BandStatus = when {
            !permission || !bluetoothOn || !hasBand || recoveryNeeded -> ACTION_REQUIRED
            connected -> CONNECTED
            busy -> CONNECTING
            listOf("error:", "failed", "unable", "could not", "timed out", "not found", "cleanup did not finish")
                .any { lastStatus.contains(it, ignoreCase = true) } -> FAILED
            else -> READY
        }
    }
}
