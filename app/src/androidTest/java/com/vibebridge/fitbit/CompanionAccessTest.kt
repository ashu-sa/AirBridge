package com.vibebridge.fitbit

import androidx.test.platform.app.InstrumentationRegistry
import com.vibebridge.fitbit.background.BandAssociation
import org.junit.Assert.*
import org.junit.Test

/** Inspects setup only; does not create an association, connect, or change a bond. */
class CompanionAccessTest {
    @Test fun associationSetupIsOptionalAndTargetsOneDevice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val request = BandAssociation.requestFor("02:00:00:00:00:01")
        assertTrue(request.isSingleDevice)
        assertFalse(BandAssociation.isAssociated(context, ""))
        val info = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.contains("android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND"))
        assertFalse(info.requestedPermissions.contains("android.permission.SYSTEM_ALERT_WINDOW"))
    }
}
