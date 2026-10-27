package com.vibebridge.fitbit.service

import com.vibebridge.fitbit.ble.HapticsRestoreException
import java.util.concurrent.CancellationException

internal object AlertRetryPolicy {
    fun shouldRetry(attempt: Int, retryCount: Int, continuousCall: Boolean,
                    ringing: Boolean, stopped: Boolean, error: Throwable): Boolean =
        attempt < retryCount && !stopped && (!continuousCall || ringing) &&
            error !is HapticsRestoreException && error !is SecurityException && error !is CancellationException
}
