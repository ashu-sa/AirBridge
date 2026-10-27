package com.vibebridge.fitbit

import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibebridge.fitbit.ble.*
import com.vibebridge.fitbit.schedule.*
import com.vibebridge.fitbit.service.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class NotificationPipelineTest {
    @Test fun realAndroidNotificationIsBlockedOutsideScheduleWithoutOpeningBLE() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("notifications")=="true")
        exercisePipeline(false)
    }
    @Test fun realBackgroundNotificationInsideScheduleVibratesAndReleasesBand() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("hardware")=="true")
        exercisePipeline(true)
    }
    private fun exercisePipeline(allowed:Boolean) {
        val instrument=InstrumentationRegistry.getInstrumentation();val target=instrument.targetContext
        TestPreferenceBackup.saveIfAbsent(target)
        try {
            val now=ZonedDateTime.now();val minute=now.hour*60+now.minute
            val windows=if(allowed) listOf(AlertWindow("test","Controlled test",(minute+1439)%1440,(minute+10)%1440)) else emptyList()
            AlertScheduleStore.save(target,AlertSchedule(true,windows));AlertScheduleStore.clearSyncPause(target)
            AirBridgeDeviceSettings.setNotificationAlertsEnabled(target,true)
            AirBridgeNotificationSettings.saveRules(target,listOf(AirNotificationRule("com.airbridge.fixture",AirHapticPattern.SINGLE,true)))
            NotificationListenerService.requestRebind(ComponentName(target,AirNotificationListenerService::class.java))
            val bindDeadline=SystemClock.elapsedRealtime()+15_000
            while(!BleSessionDiagnostics.listenerConnected && SystemClock.elapsedRealtime()<bindDeadline)SystemClock.sleep(100)
            assertTrue("Notification listener was not bound",BleSessionDiagnostics.listenerConnected)
            BleSessionDiagnostics.lastScheduleBlockedPackage=null
            val before=BleSessionDiagnostics.notificationsScheduleBlocked.get()
            val sessionsBefore=BleSessionDiagnostics.started.get()
            val runtimeBefore=AirBridgeRuntime.isInitialized()
            AirBridgeDeviceSettings.saveLastStatus(target,"Preparing notification test")
            ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand(
                "am broadcast --include-stopped-packages -a airbridge.test.POST -n com.airbridge.fixture/.NotificationReceiver"
            )).bufferedReader().use { it.readText() }
            val deadline=SystemClock.elapsedRealtime()+if(allowed)90_000 else 10_000
            if(allowed) {
                while(SystemClock.elapsedRealtime()<deadline) {
                    val status=AirBridgeDeviceSettings.loadLastStatus(target)
                    if(status.startsWith("Error: com.airbridge.fixture"))fail(status)
                    if(status.startsWith("Success: com.airbridge.fixture") && !AirBridgeOneShotService.isVibrating)break
                    SystemClock.sleep(100)
                }
                assertTrue(AirBridgeDeviceSettings.loadLastStatus(target),AirBridgeDeviceSettings.loadLastStatus(target).startsWith("Success: com.airbridge.fixture"))
                assertEquals(0,BleSessionDiagnostics.activeSessions)
                assertNull(PendingHapticRecovery.get(target,AirBridgeDeviceSettings.loadAddress(target)!!))
            } else {
                while(BleSessionDiagnostics.lastScheduleBlockedPackage!="com.airbridge.fixture" && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(100)
                assertEquals("Listener did not receive the test", "com.airbridge.fixture", BleSessionDiagnostics.lastScheduleBlockedPackage)
                assertTrue(BleSessionDiagnostics.notificationsScheduleBlocked.get()>before)
                assertEquals(0,BleSessionDiagnostics.activeSessions)
                assertEquals(sessionsBefore,BleSessionDiagnostics.started.get())
                assertEquals(runtimeBefore,AirBridgeRuntime.isInitialized())
            }
        } finally {
            ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand(
                "am broadcast --include-stopped-packages -a airbridge.test.CANCEL -n com.airbridge.fixture/.NotificationReceiver"
            )).bufferedReader().use { it.readText() }
            TestPreferenceBackup.restore(target)
        }
    }
}
