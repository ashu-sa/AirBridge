package com.vibebridge.fitbit.service

import com.vibebridge.fitbit.ble.HapticsRestoreException
import org.junit.Assert.*
import org.junit.Test

class AlertRetryPolicyTest {
    @Test fun manualTestRetriesTransientConnectionFailure() {
        assertTrue(AlertRetryPolicy.shouldRetry(0, 2, false, false, false, RuntimeException()))
    }
    @Test fun retryBudgetIsBounded() {
        assertFalse(AlertRetryPolicy.shouldRetry(2, 2, false, false, false, RuntimeException()))
    }
    @Test fun stoppedOrEndedCallsDoNotRestart() {
        assertFalse(AlertRetryPolicy.shouldRetry(0, 2, true, false, false, RuntimeException()))
        assertFalse(AlertRetryPolicy.shouldRetry(0, 2, false, false, true, RuntimeException()))
        assertFalse(AlertRetryPolicy.shouldRetry(0, 2, false, false, false, java.util.concurrent.CancellationException()))
    }
    @Test fun uncertainRestorationNeverRepeatsTheWholePattern() {
        assertFalse(AlertRetryPolicy.shouldRetry(0, 2, false, false, false, HapticsRestoreException("unknown")))
    }
    @Test fun revokedPermissionsDoNotCauseRepeatedConnectionAttempts() {
        assertFalse(AlertRetryPolicy.shouldRetry(0, 2, false, false, false, SecurityException()))
    }
}
