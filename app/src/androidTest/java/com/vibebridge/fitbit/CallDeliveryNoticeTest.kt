package com.vibebridge.fitbit

import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import com.vibebridge.fitbit.alerts.CallAlertDelivery
import com.vibebridge.fitbit.ble.AirBridgeRuntime
import org.junit.*
import org.junit.Assert.*

/** Opt-in emulator tests only: no calls, Bluetooth connections or band writes. */
class CallDeliveryNoticeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private fun isEmulator() = android.os.Build.HARDWARE in setOf("ranchu", "goldfish") || android.os.Build.FINGERPRINT.startsWith("generic")
    @Before fun setup() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("noticeTests") == "true")
        Assume.assumeTrue(isEmulator())
        context.getSharedPreferences("air_bridge_call_delivery", 0).edit().clear().commit()
        manager.cancel(24002)
    }
    @After fun cleanup() {
        if (InstrumentationRegistry.getArguments().getString("noticeTests") != "true" || !isEmulator()) return
        context.getSharedPreferences("air_bridge_call_delivery", 0).edit().clear().commit()
        manager.cancel(24002)
    }
    private fun notices() = manager.activeNotifications.filter { it.id == 24002 }
    private fun awaitNotice(): List<android.service.notification.StatusBarNotification> {
        val until = android.os.SystemClock.elapsedRealtime() + 2_000
        while (notices().isEmpty() && android.os.SystemClock.elapsedRealtime() < until) android.os.SystemClock.sleep(20)
        assertFalse("Android did not post the recovery notice", notices().isEmpty())
        return notices()
    }
    @Test fun postsOnlyAfterCallAndAttemptEndWithResetAction() {
        val id = CallAlertDelivery.begin(context)
        CallAlertDelivery.ended(context, true)
        assertTrue(notices().isEmpty())
        CallAlertDelivery.complete(context, id)
        val notice = awaitNotice().single().notification
        assertTrue(notice.actions.any { it.title.toString() == "Reset link" })
        assertTrue(notice.extras.getCharSequence("android.title").toString().contains("wasn't confirmed"))
        assertFalse(AirBridgeRuntime.isInitialized())
    }
    @Test fun latePulseAcknowledgementSuppressesFalseNotice() {
        val id = CallAlertDelivery.begin(context)
        CallAlertDelivery.ended(context, true)
        CallAlertDelivery.confirmed(context, id)
        CallAlertDelivery.complete(context, id)
        assertTrue(notices().isEmpty())
    }
    @Test fun blockedStartWaitsForCallEndAndDoesNotNotifyTwice() {
        val id = CallAlertDelivery.begin(context)
        assertEquals(id, CallAlertDelivery.begin(context))
        CallAlertDelivery.complete(context, id)
        assertTrue(notices().isEmpty())
        CallAlertDelivery.ended(context, false)
        val posted = awaitNotice().single().postTime
        CallAlertDelivery.ended(context, false)
        assertEquals(posted, notices().single().postTime)
    }
    @Test fun processRestartCanFinishAnUnconfirmedCall() {
        CallAlertDelivery.begin(context)
        CallAlertDelivery.ended(context, false)
        assertEquals(1, awaitNotice().size)
    }
    @Test fun intentionalSkipNeverPosts() {
        val id = CallAlertDelivery.begin(context)
        CallAlertDelivery.skip(context, id)
        CallAlertDelivery.ended(context, false)
        assertTrue(notices().isEmpty())
    }
    @Test fun retryAfterFailedStartWaitsForNewAttempt() {
        val id = CallAlertDelivery.begin(context)
        CallAlertDelivery.complete(context, id)
        assertEquals(id, CallAlertDelivery.begin(context))
        CallAlertDelivery.ended(context, true)
        assertTrue(notices().isEmpty())
        CallAlertDelivery.confirmed(context, id)
        CallAlertDelivery.complete(context, id)
        assertTrue(notices().isEmpty())
    }
    @Test fun deniedBluetoothStartUsesRealCallRoutingAndNotifiesAfterIdle() {
        Assume.assumeTrue(context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED)
        TestPreferenceBackup.saveIfAbsent(context)
        try {
            com.vibebridge.fitbit.ble.AirBridgeDeviceSettings.saveAddress(context, "02:00:00:00:00:01", "Test only")
            com.vibebridge.fitbit.ble.AirBridgeDeviceSettings.setCallAlertEnabled(context, true)
            com.vibebridge.fitbit.schedule.AlertScheduleStore.save(context, com.vibebridge.fitbit.schedule.AlertSchedule(false))
            com.vibebridge.fitbit.schedule.AlertScheduleStore.clearSyncPause(context)
            com.vibebridge.fitbit.calls.CallEventHandler.handle(context, android.telephony.TelephonyManager.CALL_STATE_RINGING, "emulator notice test")
            assertTrue(notices().isEmpty())
            com.vibebridge.fitbit.calls.CallEventHandler.handle(context, android.telephony.TelephonyManager.CALL_STATE_IDLE, "emulator notice test")
            assertEquals(1, awaitNotice().size)
            assertFalse(AirBridgeRuntime.isInitialized())
        } finally { TestPreferenceBackup.restore(context) }
    }
    @Test fun newCallDoesNotOverwriteAnUnfinishedOlderCall() {
        val old = CallAlertDelivery.begin(context)
        CallAlertDelivery.ended(context, true)
        val next = CallAlertDelivery.begin(context)
        assertNotEquals(old, next)
        CallAlertDelivery.confirmed(context, old)
        CallAlertDelivery.complete(context, old)
        assertTrue(notices().isEmpty())
        CallAlertDelivery.ended(context, true)
        CallAlertDelivery.complete(context, next)
        assertEquals(1, awaitNotice().size)
    }
}
