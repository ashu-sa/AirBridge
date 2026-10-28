package com.vibebridge.fitbit

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Checks the installed variant, not just the source manifest. */
class EditionContractTest {
    @Suppress("DEPRECATION")
    @Test fun installedManifestMatchesEdition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Air Bridge", context.applicationInfo.loadLabel(context.packageManager).toString())
        val info = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_SERVICES or PackageManager.GET_PERMISSIONS or PackageManager.GET_RECEIVERS)
        val listeners = info.services.orEmpty().filter {
            it.permission == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        }
        assertEquals(if (BuildConfig.NOTIFICATION_FEATURES) 1 else 0, listeners.size)
        if (!BuildConfig.NOTIFICATION_FEATURES) {
            assertThrows(ClassNotFoundException::class.java) {
                Class.forName("com.vibebridge.fitbit.service.AirNotificationListenerService")
            }
        }
        assertEquals(if (BuildConfig.NOTIFICATION_FEATURES) "com.vibebridge.fitbit" else "com.vibebridge.fitbit.calls", context.packageName)
        assertTrue(info.requestedPermissions.orEmpty().contains("android.permission.READ_PHONE_STATE"))
        assertTrue(info.receivers.orEmpty().any { it.name.endsWith(".receiver.CallReceiver") })
        val foreground = info.services.orEmpty().single { it.name.endsWith(".service.AirBridgeOneShotService") }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            assertTrue(foreground.foregroundServiceType and android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE != 0)
        }
        assertFalse(info.requestedPermissions.orEmpty().any { it in setOf("android.permission.READ_SMS", "android.permission.RECEIVE_SMS", "android.permission.BIND_ACCESSIBILITY_SERVICE") })
    }
}
