package com.vibebridge.fitbit

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibebridge.fitbit.ble.*
import com.vibebridge.fitbit.schedule.*
import com.vibebridge.fitbit.service.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Reads connection/sync labels only; never exports health data. */
@RunWith(AndroidJUnit4::class)
class HardwareSyncTest {
    private val instrument=InstrumentationRegistry.getInstrumentation()
    private val context=instrument.targetContext
    private fun find(node:AccessibilityNodeInfo?, predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo? {
        if(node==null)return null
        if(predicate(node))return node
        for(i in 0 until node.childCount) { val found=find(node.getChild(i),predicate);if(found!=null)return found }
        return null
    }
    private fun label(node:AccessibilityNodeInfo)=listOfNotNull(node.text?.toString(),node.contentDescription?.toString()).joinToString(" ")
    private fun freshRoot():AccessibilityNodeInfo? {
        instrument.uiAutomation.clearCache()
        return instrument.uiAutomation.rootInActiveWindow?.apply { refresh() }
    }
    private fun healthNode(fragment:String)=find(freshRoot()) {
        it.packageName?.toString()=="com.fitbit.FitbitMobile" && label(it).contains(fragment,true)
    }
    private fun click(fragment:String):Boolean {
        var node=healthNode(fragment) ?: return false
        repeat(4) {
            if(node.isClickable && node.isEnabled && node.isVisibleToUser) {
                val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
                val now=SystemClock.uptimeMillis()
                val down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.exactCenterX(),bounds.exactCenterY(),0)
                val up=android.view.MotionEvent.obtain(now,now+40,android.view.MotionEvent.ACTION_UP,bounds.exactCenterX(),bounds.exactCenterY(),0)
                down.source=android.view.InputDevice.SOURCE_TOUCHSCREEN;up.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { return instrument.uiAutomation.injectInputEvent(down,true) && instrument.uiAutomation.injectInputEvent(up,true) }
                finally { down.recycle();up.recycle() }
            }
            node=node.parent ?: return false
        }
        return false
    }
    private fun syncLabel():String? {
        val node=find(freshRoot()) {
            it.packageName?.toString()=="com.fitbit.FitbitMobile" &&
                (label(it).contains("Synced",true) || label(it).contains("Syncing",true))
        }
        return node?.let { label(it).take(120) }
    }
    @Test fun companionSyncCompletesAfterThreeBoundedAlertSessions() {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("hardware")=="true")
        TestPreferenceBackup.saveIfAbsent(context)
        AlertScheduleStore.save(context,AlertSchedule(false));AlertScheduleStore.clearSyncPause(context)
        AirBridgeDeviceSettings.setCallAlertEnabled(context,false)
        AirBridgeDeviceSettings.setNotificationAlertsEnabled(context,false)
        try {
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SystemClock.sleep(1_000)
            val companion=context.packageManager.getLaunchIntentForPackage("com.fitbit.FitbitMobile")!!
            context.startActivity(companion.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            SystemClock.sleep(2_000)
            if(healthNode("Sync connected devices and apps")==null) { click("Devices and apps");SystemClock.sleep(1_000) }
            repeat(3) { i ->
                val readyDeadline=SystemClock.elapsedRealtime()+60_000
                while(healthNode("Sync connected devices and apps")==null && SystemClock.elapsedRealtime()<readyDeadline)SystemClock.sleep(250)
                val before=syncLabel()
                assertTrue("Companion sync control not available",click("Sync connected devices and apps"))
                val source="Coexistence sync $i"
                assertTrue(AirBridgeOneShotService.enqueue(context,AirNotificationTrigger(source,AirHapticPattern.SINGLE,isManual=true)))
                val deadline=SystemClock.elapsedRealtime()+90_000
                var sawBusy=before?.contains("Syncing",true)==true
                var completed:String?=null
                var alertSuccess=false
                var refreshedAt=0L
                var refreshes=0
                while(SystemClock.elapsedRealtime()<deadline) {
                    val status=AirBridgeDeviceSettings.loadLastStatus(context)
                    if(status.startsWith("Error: $source"))fail(status)
                    alertSuccess=status.startsWith("Success: $source") || alertSuccess
                    if(alertSuccess && !AirBridgeOneShotService.isVibrating && refreshes<3 &&
                        SystemClock.elapsedRealtime()-refreshedAt>5_000) {
                        instrument.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
                        SystemClock.sleep(500)
                        click("Devices and apps")
                        SystemClock.sleep(500)
                        refreshedAt=SystemClock.elapsedRealtime();refreshes++
                    }
                    val sync=syncLabel()
                    if(sync?.contains("Syncing",true)==true)sawBusy=true
                    if(sync?.contains("Synced",true)==true && !sync.contains("Syncing",true))completed=sync
                    val fresh=completed?.contains("just now",true)==true || completed?.contains("0 min",true)==true
                    if(alertSuccess && fresh && !AirBridgeOneShotService.isVibrating)break
                    SystemClock.sleep(250)
                }
                assertTrue("Alert did not complete",alertSuccess)
                assertNotNull("Companion sync did not complete",completed)
                assertTrue("No fresh sync completion was observed: before=$before after=$completed sawBusy=$sawBusy",
                    completed?.contains("just now",true)==true || completed?.contains("0 min",true)==true)
                assertEquals(0,BleSessionDiagnostics.activeSessions)
                println("AIRBRIDGE_SYNC cycle=$i before=$before sawSyncing=$sawBusy after=$completed")
                SystemClock.sleep(3_000)
            }
        } finally {
            if(AirBridgeOneShotService.isVibrating)AirBridgeOneShotService.stopAll(context)
            TestPreferenceBackup.restore(context)
        }
    }
}
