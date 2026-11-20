package com.vibebridge.fitbit.alerts

import org.junit.Assert.*
import org.junit.Test

class CallDeliveryStateTest {
    @Test fun neverWarnsWhileCallOrDeliveryIsStillInProgress() {
        assertFalse(CallDeliveryState().needsNotice)
        assertFalse(CallDeliveryState(ended = true, pending = true).needsNotice)
        assertFalse(CallDeliveryState(ended = false, pending = false).needsNotice)
    }
    @Test fun endedCallWithNoAcknowledgedPulseNeedsNotice() {
        assertTrue(CallDeliveryState(ended = true, pending = false).needsNotice)
    }
    @Test fun acknowledgedPulseSuppressesMissedVibrationNoticeEvenIfLaterCleanupFails() {
        assertFalse(CallDeliveryState(confirmed = true, ended = true, pending = false).needsNotice)
    }
}
