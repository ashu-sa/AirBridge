package com.vibebridge.fitbit

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.test.platform.app.InstrumentationRegistry
import com.vibebridge.fitbit.ble.*
import com.vibebridge.fitbit.calls.*
import com.vibebridge.fitbit.schedule.*
import com.vibebridge.fitbit.service.*
import org.junit.*
import org.junit.Assert.*

/** Emulator-only routing checks with BLE permission denied before instrumentation starts. */
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class AppCallIntegrationTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val context=instrument.targetContext
    private val pkg="com.airbridge.fixture"
    private var ready=false
    private fun shell(command:String) = ParcelFileDescriptor.AutoCloseInputStream(instrument.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }
    private fun waitFor(condition:()->Boolean) {
        val until=SystemClock.elapsedRealtime()+20_000
        while(!condition() && SystemClock.elapsedRealtime()<until)SystemClock.sleep(50)
        assertTrue("Timed out waiting for notification delivery",condition())
    }
    @Before fun setup() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("appcalls")=="true")
        assertNotEquals("Deny BLE permission on emulator before starting this test",PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT))
        TestPreferenceBackup.saveIfAbsent(context);ready=true
        AlertScheduleStore.save(context,AlertSchedule(false));AlertScheduleStore.clearSyncPause(context)
        AirBridgeDeviceSettings.setCallAlertEnabled(context,false)
        AirBridgeDeviceSettings.setNotificationAlertsEnabled(context,false)
        AppCallSettings.save(context,AppCallConfig(true,setOf(pkg),AirHapticPattern.SINGLE))
        AppCallSessions.notifications.clear()
        shell("cmd notification disallow_listener com.vibebridge.fitbit/com.vibebridge.fitbit.service.AirNotificationListenerService")
        shell("cmd notification allow_listener com.vibebridge.fitbit/com.vibebridge.fitbit.service.AirNotificationListenerService")
        waitFor { BleSessionDiagnostics.listenerConnected }
        shell("am broadcast --include-stopped-packages -a airbridge.test.CANCEL -n $pkg/.NotificationReceiver")
    }
    @After fun restore() {
        if(!ready)return
        shell("am broadcast --include-stopped-packages -a airbridge.test.CANCEL -n $pkg/.NotificationReceiver")
        AppCallSessions.notifications.clear();TestPreferenceBackup.restore(context)
    }
    private fun post(phase:String="incoming",message:Boolean=false) {
        val before=BleSessionDiagnostics.notificationsObserved.get()
        shell("am broadcast --include-stopped-packages -a airbridge.test.${if(message) "POST" else "CALL"} --es phase $phase -n $pkg/.NotificationReceiver")
        waitFor { BleSessionDiagnostics.notificationsObserved.get()>before && BleSessionDiagnostics.lastObservedPackage==pkg }
        SystemClock.sleep(150)
    }
    @Test fun incomingCallStyleRoutesOnceIndependentlyOfMessagesAndSIMAlerts() {
        val before=BleSessionDiagnostics.appCallAttempts.get()
        post();assertEquals(before+1,BleSessionDiagnostics.appCallAttempts.get())
        assertTrue(AirBridgeDeviceSettings.loadLastStatus(context).contains("permission"))
        post();assertEquals("Duplicate incoming update retriggered",before+1,BleSessionDiagnostics.appCallAttempts.get())
        post("ongoing");assertEquals(before+1,BleSessionDiagnostics.appCallAttempts.get())
        post();assertEquals("New incoming phase was lost",before+2,BleSessionDiagnostics.appCallAttempts.get())
        assertFalse(AirBridgeRuntime.isInitialized());assertEquals(0,BleSessionDiagnostics.activeSessions)
    }
    @Test fun messagesAndOngoingCallsDoNotTriggerAppCallAlerts() {
        val before=BleSessionDiagnostics.appCallAttempts.get();post(message=true);post("ongoing")
        assertEquals(before,BleSessionDiagnostics.appCallAttempts.get());assertFalse(AirBridgeRuntime.isInitialized())
    }
    @Test fun offUnselectedScheduleAndSyncPauseAllBlockAppCalls() {
        val before=BleSessionDiagnostics.appCallAttempts.get()
        AppCallSettings.save(context,AppCallConfig(false,setOf(pkg)));post()
        AppCallSettings.save(context,AppCallConfig(true,emptySet()));post()
        AppCallSettings.save(context,AppCallConfig(true,setOf(pkg)));AlertScheduleStore.save(context,AlertSchedule(true,emptyList()));post()
        AlertScheduleStore.save(context,AlertSchedule(false));AlertScheduleStore.pauseForSync(context);post()
        assertEquals(before,BleSessionDiagnostics.appCallAttempts.get());assertFalse(AirBridgeRuntime.isInitialized())
    }
    @Test fun legacyCallNotificationAndNotificationRemovalAreHandled() {
        val before=BleSessionDiagnostics.appCallAttempts.get();post("legacy")
        assertEquals(before+1,BleSessionDiagnostics.appCallAttempts.get())
        shell("am broadcast --include-stopped-packages -a airbridge.test.CANCEL -n $pkg/.NotificationReceiver")
        SystemClock.sleep(300);post("legacy");assertEquals(before+2,BleSessionDiagnostics.appCallAttempts.get())
    }
    @Test fun zzEndedCallCannotOpenBLEEvenAfterServiceWasQueued() {
        shell("pm grant com.vibebridge.fitbit android.permission.BLUETOOTH_CONNECT")
        AirBridgeDeviceSettings.saveAddress(context,"00:11:22:33:44:55","Test only")
        val token=AppCallSessions.notifications.begin("cancelled",pkg,SystemClock.elapsedRealtime())!!
        AppCallSessions.notifications.end("cancelled")
        AirBridgeDeviceSettings.saveLastStatus(context,"Preparing cancelled call test")
        assertTrue(AirBridgeOneShotService.enqueue(context,AirNotificationTrigger(pkg,AirHapticPattern.SINGLE,appCallToken=token)))
        waitFor { AirBridgeDeviceSettings.loadLastStatus(context)=="Alert stopped" && !AirBridgeOneShotService.isVibrating }
        assertFalse("Ended call initialized BLE",AirBridgeRuntime.isInitialized())
        assertEquals(0,BleSessionDiagnostics.activeSessions)
    }
    @Test fun settingsRoundTripAndCorruptSelectionsFailClosed() {
        val config=AppCallConfig(true,setOf("com.Slack","us.zoom.videomeetings"),AirHapticPattern.DOUBLE)
        AppCallSettings.save(context,config);assertEquals(config,AppCallSettings.load(context))
        context.getSharedPreferences("air_bridge_app_calls",0).edit().putString("packages","broken").commit()
        assertFalse(AppCallSettings.load(context).enabled)
    }
}
