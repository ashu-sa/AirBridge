package com.vibebridge.fitbit

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibebridge.fitbit.ble.*
import com.vibebridge.fitbit.schedule.*
import com.vibebridge.fitbit.service.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Opt-in physical-band tests. Never run as part of ordinary connectedAndroidTest. */
@RunWith(AndroidJUnit4::class)
class HardwareBleTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val args=InstrumentationRegistry.getArguments()
    private lateinit var schedule: AlertSchedule
    private var calls=false
    private var notifications=false
    private var setupComplete=false

    @Before fun prepare() {
        Assume.assumeTrue("Physical tests require explicit opt-in",args.getString("hardware")=="true")
        TestPreferenceBackup.saveIfAbsent(context)
        schedule=AlertScheduleStore.load(context)
        calls=AirBridgeDeviceSettings.isCallAlertEnabled(context)
        notifications=AirBridgeDeviceSettings.isNotificationAlertsEnabled(context)
        setupComplete=true
        AlertScheduleStore.save(context,AlertSchedule(false))
        AlertScheduleStore.clearSyncPause(context)
        AirBridgeDeviceSettings.setCallAlertEnabled(context,false)
        AirBridgeDeviceSettings.setNotificationAlertsEnabled(context,false)
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(1200)
        instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
    }
    @After fun restore() {
        if(!setupComplete)return
        if(AirBridgeOneShotService.isVibrating) AirBridgeOneShotService.stopAll(context)
        waitUntil(45_000) { !AirBridgeOneShotService.isVibrating }
        TestPreferenceBackup.restore(context)
    }
    private fun waitUntil(timeout:Long, condition:()->Boolean):Boolean {
        val until=SystemClock.elapsedRealtime()+timeout
        while(SystemClock.elapsedRealtime()<until) { if(condition())return true;SystemClock.sleep(100) }
        return condition()
    }
    private fun pulse(pattern:AirHapticPattern,source:String) {
        AirBridgeDeviceSettings.saveLastStatus(context,"Preparing hardware test")
        val start=SystemClock.elapsedRealtime()
        assertTrue(AirBridgeOneShotService.enqueue(context,AirNotificationTrigger(source,pattern,isManual=true)))
        assertTrue("No result: "+AirBridgeDeviceSettings.loadLastStatus(context),waitUntil(90_000) {
            val status=AirBridgeDeviceSettings.loadLastStatus(context)
            status.startsWith("Success: $source") || status.startsWith("Error: $source")
        })
        val status=AirBridgeDeviceSettings.loadLastStatus(context)
        assertTrue(status,status.startsWith("Success: $source"))
        assertTrue(waitUntil(5_000) { !AirBridgeOneShotService.isVibrating })
        assertEquals(0,BleSessionDiagnostics.activeSessions)
        val address=AirBridgeDeviceSettings.loadAddress(context)!!
        assertNull(PendingHapticRecovery.get(context,address))
        println("AIRBRIDGE_RESULT pattern=${pattern.wireName} durationMs=${SystemClock.elapsedRealtime()-start} status=$status sessions=${BleSessionDiagnostics.started.get()}/${BleSessionDiagnostics.released.get()}")
    }
    @Test fun incomingPhoneStateUsesAutomaticCallPathAndStopsOnAnswer() {
        AirBridgeDeviceSettings.setCallAlertEnabled(context,true)
        val receiver=com.vibebridge.fitbit.receiver.CallReceiver()
        fun state(value:String) = receiver.onReceive(context, Intent(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
            .putExtra(android.telephony.TelephonyManager.EXTRA_STATE,value))
        AirBridgeDeviceSettings.saveLastStatus(context,"Preparing incoming call test")
        state(android.telephony.TelephonyManager.EXTRA_STATE_RINGING)
        assertTrue("Automatic call did not complete: " + AirBridgeDeviceSettings.loadLastStatus(context), waitUntil(90_000) {
            val status=AirBridgeDeviceSettings.loadLastStatus(context)
            status.startsWith("Success: Incoming Call") || status.startsWith("Error: Incoming Call")
        })
        assertTrue(AirBridgeDeviceSettings.loadLastStatus(context),AirBridgeDeviceSettings.loadLastStatus(context).startsWith("Success: Incoming Call"))
        state(android.telephony.TelephonyManager.EXTRA_STATE_OFFHOOK)
        assertTrue("Answered call did not stop",waitUntil(15_000) { !AirBridgeOneShotService.isVibrating })
        assertEquals(0,BleSessionDiagnostics.activeSessions)
    }
    @Test fun patternsRestoreAndRelease() {
        val requested=args.getString("pattern")
        val patterns=if(requested!=null) listOf(AirHapticPattern.fromWireName(requested)!!) else
            listOf(AirHapticPattern.SINGLE,AirHapticPattern.DOUBLE,AirHapticPattern.TRIPLE,AirHapticPattern.LONG_GAP,AirHapticPattern.URGENT)
        patterns.forEachIndexed { i,p -> pulse(p,"Hardware pattern $i ${p.wireName}");SystemClock.sleep(3_000) }
    }
    @Test fun repeatingCallYieldsAndScheduleClosureStopsIt() {
        assertTrue(AirBridgeOneShotService.enqueue(context,AirNotificationTrigger("Hardware repeat",AirHapticPattern.SINGLE,
            isCall=true,isContinuousCall=true,isManual=true)))
        assertTrue(waitUntil(90_000) { AirBridgeDeviceSettings.loadLastStatus(context).startsWith("Success: Hardware repeat") })
        assertEquals(0,BleSessionDiagnostics.activeSessions)
        val released=BleSessionDiagnostics.released.get()
        SystemClock.sleep(2_000)
        assertEquals("Band connection held in inter-burst gap",0,BleSessionDiagnostics.activeSessions)
        AlertScheduleStore.save(context,AlertSchedule(true,emptyList()))
        assertTrue("Schedule did not stop repeating call",waitUntil(10_000) { !AirBridgeOneShotService.isVibrating })
        assertEquals(0,BleSessionDiagnostics.activeSessions)
        assertEquals(released,BleSessionDiagnostics.released.get())
    }
    @Test fun pendingRecoveryBlocksAutomaticAlertsAndManualTestHealsIt() {
        val address=AirBridgeDeviceSettings.loadAddress(context)!!
        val recoveryValue=args.getString("recoveryIntensity")?.toInt() ?: 2
        PendingHapticRecovery.mark(context,address,recoveryValue)
        assertFalse(AirBridgeOneShotService.enqueue(context,AirNotificationTrigger("Must not run",AirHapticPattern.SINGLE)))
        pulse(AirHapticPattern.SINGLE,"Hardware recovery")
    }
}
