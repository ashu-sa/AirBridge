package com.vibebridge.fitbit.service

import org.junit.Assert.*
import org.junit.Test

class NotificationAlertGateTest {
    @Test fun firstAlertIsAllowedImmediatelyAfterBoot() {
        assertTrue(NotificationAlertGate().canAlert("key:1", "app", 10L))
    }
    @Test fun failedServiceStartDoesNotConsumeCooldown() {
        val gate = NotificationAlertGate()
        assertTrue(gate.canAlert("key:1", "app", 10L))
        assertTrue(gate.canAlert("key:1", "app", 20L))
    }
    @Test fun duplicateAndPerAppCooldownSuppressUpdates() {
        val gate = NotificationAlertGate()
        gate.recordAccepted("key:1", "app", 10L)
        assertFalse(gate.canAlert("key:1", "app", 100L))
        assertFalse(gate.canAlert("key:2", "app", 2_000L))
        assertTrue(gate.canAlert("key:2", "app", 3_010L))
    }
    @Test fun differentAppsShareOnlyTheShorterGlobalCooldown() {
        val gate = NotificationAlertGate()
        gate.recordAccepted("key:1", "one", 10L)
        assertFalse(gate.canAlert("key:2", "two", 1_000L))
        assertTrue(gate.canAlert("key:2", "two", 1_510L))
    }
    @Test fun expiredMetadataDoesNotBlockReusedNotificationKey() {
        val gate = NotificationAlertGate()
        gate.recordAccepted("key:1", "app", 10L)
        assertTrue(gate.canAlert("key:1", "app", 3_010L))
    }
}
