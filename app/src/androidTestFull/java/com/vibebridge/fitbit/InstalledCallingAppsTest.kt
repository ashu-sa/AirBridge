package com.vibebridge.fitbit

import androidx.test.platform.app.InstrumentationRegistry
import com.vibebridge.fitbit.calls.*
import org.junit.*
import org.junit.Assert.*

/** Opt-in read-only discovery checks. Output contains only calling-app labels and evidence, no account data. */
class InstalledCallingAppsTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun optIn() { Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("discovery")=="true") }
    @Suppress("DEPRECATION")
    @Test fun resultsAreInstalledEnabledAndHaveCallingEvidence() {
        val apps=CallingAppsDiscovery.find(context)
        assertEquals(apps.size,apps.map { it.packageName }.distinct().size)
        apps.forEach { app ->
            assertTrue(context.packageManager.getApplicationInfo(app.packageName,0).enabled)
            assertNotEquals(context.packageName,app.packageName)
            assertNotEquals(CallingAppReason.MANUAL,app.reason)
            assertTrue(app.label.isNotBlank())
            println("CALLING_APP ${app.label} [${app.reason.label}]")
        }
        println("CALLING_APP_COUNT ${apps.size}")
    }
    @Test fun discoveryDoesNotEnableOrSelectAlerts() {
        val before=AppCallSettings.load(context)
        CallingAppsDiscovery.find(context,before.packages)
        assertEquals(before,AppCallSettings.load(context))
    }
    @Test fun repeatedDiscoveryReusesInventoryUntilInvalidated() {
        val before=AppCallSettings.load(context)
        CallingAppsDiscovery.invalidate()
        val first=CallingAppsDiscovery.find(context)
        assertSame(first,CallingAppsDiscovery.find(context))
        assertSame(first,CallingAppsDiscovery.cachedApps())
        CallingAppsDiscovery.invalidate()
        assertNull(CallingAppsDiscovery.cachedApps())
        assertEquals(before,AppCallSettings.load(context))
    }
    @Test fun savedInventorySurvivesMemoryInvalidationWithoutChangingAlerts() {
        val before = AppCallSettings.load(context)
        CallingAppsDiscovery.invalidate()
        val apps = CallingAppsDiscovery.find(context, before.packages)
        CallingAppsDiscovery.invalidate()
        assertNull(CallingAppsDiscovery.cachedApps())
        assertEquals(apps, CallingAppsDiscovery.savedApps(context))
        assertEquals(before, AppCallSettings.load(context))
    }

}
