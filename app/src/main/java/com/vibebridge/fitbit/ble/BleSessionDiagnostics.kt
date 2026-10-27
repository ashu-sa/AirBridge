package com.vibebridge.fitbit.ble

import java.util.concurrent.atomic.AtomicLong

/** Process-local diagnostics; no message contents, device IDs or persistent polling. */
object BleSessionDiagnostics {
    @Volatile var listenerConnected = false
    @Volatile var lastScheduleBlockedPackage: String? = null
    @Volatile var cleanupFailed = false
    @Volatile var lastObservedPackage: String? = null
    val appCallAttempts = AtomicLong(0)
    val notificationsObserved = AtomicLong(0)
    val notificationsScheduleBlocked = AtomicLong(0)
    val started = AtomicLong(0)
    val released = AtomicLong(0)
    @Volatile var activeSessions = 0
        private set
    fun onStarted() { activeSessions++; started.incrementAndGet() }
    fun onReleased() { activeSessions = (activeSessions - 1).coerceAtLeast(0); released.incrementAndGet() }
}
