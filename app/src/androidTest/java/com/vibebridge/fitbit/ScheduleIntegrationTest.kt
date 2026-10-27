package com.vibebridge.fitbit

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibebridge.fitbit.schedule.*
import com.vibebridge.fitbit.service.AirBridgeOneShotService
import com.vibebridge.fitbit.service.AirNotificationTrigger
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class ScheduleIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs by lazy { context.getSharedPreferences("air_bridge_schedule", Context.MODE_PRIVATE) }
    private lateinit var snapshot: Map<String, *>
    @Before fun save() { snapshot = prefs.all; prefs.edit().clear().commit() }
    @After fun restore() {
        val editor = prefs.edit().clear()
        snapshot.forEach { (k,v) -> when(v) {
            is String -> editor.putString(k,v)
            is Boolean -> editor.putBoolean(k,v)
            is Long -> editor.putLong(k,v)
            is Int -> editor.putInt(k,v)
        } }
        editor.commit()
    }
    @Test fun defaultDoesNotEnableThePresets() {
        assertFalse(AlertScheduleStore.load(context).enabled)
        assertTrue(AlertScheduleStore.allowsNow(context))
    }
    @Test fun roundTripKeepsWeekdaysOvernightAndDisabledFlags() {
        val config = AlertSchedule(true,listOf(AlertWindow("x","Sleep",1320,420,setOf(1,3,7),false)))
        AlertScheduleStore.save(context,config)
        assertEquals(config,AlertScheduleStore.load(context))
    }
    @Test fun damagedEnabledConfigurationFailsClosed() {
        prefs.edit().putBoolean("enabled",true).putString("windows","broken").commit()
        assertFalse(AlertScheduleStore.allowsNow(context))
    }
    @Test fun missingEnabledWindowsFailClosed() {
        prefs.edit().putBoolean("enabled",true).commit()
        assertFalse(AlertScheduleStore.allowsNow(context))
    }
    @Test fun oversizedConfigurationIsRejected() {
        val w=AlertWindow("x","Window",0,60)
        try {
            AlertScheduleStore.save(context,AlertSchedule(true,(0..6).map { w.copy(id=it.toString()) }))
            fail("Accepted more than six windows")
        } catch (_:IllegalArgumentException) { }
    }
    @Test fun corruptedConfigurationsNeverEnableUnscheduledAlerts() {
        val broken=listOf("{}","null","[1]","[]", "[{\"id\":\"x\"}]")
        broken.forEach { raw ->
            prefs.edit().putBoolean("enabled",true).putString("windows",raw).commit()
            assertFalse(raw,AlertScheduleStore.allowsNow(context))
        }
    }
    @Test fun duplicateWindowIdsAreRejected() {
        val w=AlertWindow("x","Window",0,60)
        try { AlertScheduleStore.save(context,AlertSchedule(true,listOf(w,w))); fail("Accepted duplicate IDs") }
        catch (_:IllegalArgumentException) { }
    }
    @Test fun blockedScheduleDoesNotStartTheBLEService() {
        AlertScheduleStore.save(context,AlertSchedule(true,emptyList()))
        assertFalse(AirBridgeOneShotService.enqueue(context,AirNotificationTrigger("Schedule integration",AirHapticPattern.SINGLE)))
        assertTrue(AirBridgeDeviceSettingsForTest.lastStatus(context).contains("schedule"))
        assertFalse(com.vibebridge.fitbit.ble.AirBridgeRuntime.isInitialized())
    }
    @Test fun syncPauseExpiresAndCanBeResumed() {
        val now=System.currentTimeMillis()
        AlertScheduleStore.pauseForSync(context,now)
        assertTrue(AlertScheduleStore.isSyncPaused(context,now+299_999))
        assertFalse(AlertScheduleStore.isSyncPaused(context,now+300_000))
        AlertScheduleStore.clearSyncPause(context)
        assertFalse(AlertScheduleStore.isSyncPaused(context,now))
    }
    @Test fun syncPauseBlocksEvenWhenSchedulingIsDisabled() {
        AlertScheduleStore.pauseForSync(context)
        assertFalse(AirBridgeOneShotService.enqueue(context,AirNotificationTrigger("Sync integration",AirHapticPattern.SINGLE)))
    }
}
private object AirBridgeDeviceSettingsForTest {
    fun lastStatus(context:Context)=com.vibebridge.fitbit.ble.AirBridgeDeviceSettings.loadLastStatus(context)
}
