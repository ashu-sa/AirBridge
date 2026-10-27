package com.vibebridge.fitbit

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vibebridge.fitbit.ble.*
import com.vibebridge.fitbit.schedule.*
import com.vibebridge.fitbit.service.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionFaultTest {
    @Test fun unavailableBluetoothOrPermissionDoesNotStartNativeWork() {
        val args=InstrumentationRegistry.getArguments()
        Assume.assumeTrue(args.getString("fault")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val previous=AlertScheduleStore.load(context)
        AlertScheduleStore.save(context,AlertSchedule(false))
        AlertScheduleStore.clearSyncPause(context)
        try {
            val accepted=AirBridgeOneShotService.enqueue(context,AirNotificationTrigger("Permission fault",AirHapticPattern.SINGLE,isManual=true))
            assertFalse(accepted)
            val status=AirBridgeDeviceSettings.loadLastStatus(context)
            assertTrue(status,status.contains("permission",true)||status.contains("turned off",true))
            assertFalse(AirBridgeRuntime.isInitialized())
            assertEquals(0,BleSessionDiagnostics.activeSessions)
        } finally { AlertScheduleStore.save(context,previous) }
    }
}
