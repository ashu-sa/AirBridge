package com.vibebridge.fitbit.service

import com.vibebridge.fitbit.ble.HapticsRestoreException
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeoutException

class AlertFailureMessagesTest {
    @Test fun genericGattFailureDoesNotInventStatus133() {
        assertFalse(AlertFailureMessages.describe(RuntimeException("GATT connection failed")).contains("133"))
        assertTrue(AlertFailureMessages.describe(RuntimeException("GATT status 133")).contains("133"))
        assertFalse(AlertFailureMessages.describe(RuntimeException("GATT error 1133")).contains("133"))
    }
    @Test fun causeTypesTakePrecedenceOverGenericGattText() {
        assertTrue(AlertFailureMessages.describe(SecurityException("GATT unavailable")).contains("permission"))
        assertTrue(AlertFailureMessages.describe(HapticsRestoreException("GATT timeout")).contains("Recover"))
        assertTrue(AlertFailureMessages.describe(RuntimeException("GATT operation", TimeoutException())).contains("timed out"))
    }
    @Test fun cleanupFailureRequiresProcessRecovery() {
        assertTrue(AlertFailureMessages.describe(RuntimeException(), true).contains("Force stop"))
    }
}
