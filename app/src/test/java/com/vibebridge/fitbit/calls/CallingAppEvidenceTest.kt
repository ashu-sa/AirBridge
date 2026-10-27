package com.vibebridge.fitbit.calls
import org.junit.Assert.*
import org.junit.Test
class CallingAppEvidenceTest {
    @Test fun uninstalledKnownAppIsNotOffered() { assertNull(CallingAppEvidence(false,known=true).reason) }
    @Test fun unrelatedInstalledAppIsNotOffered() { assertNull(CallingAppEvidence(true).reason) }
    @Test fun installedKnownAppIsOffered() { assertEquals(CallingAppReason.KNOWN,CallingAppEvidence(true,known=true).reason) }
    @Test fun declaredCapabilityFindsUnknownApps() { assertEquals(CallingAppReason.DECLARED,CallingAppEvidence(true,declared=true).reason) }
    @Test fun knownAppsAreNotDemotedByGenericLibraryDeclarations() { assertEquals(CallingAppReason.KNOWN,CallingAppEvidence(true,known=true,declared=true).reason) }
    @Test fun observedNotificationTakesPrecedence() { assertEquals(CallingAppReason.OBSERVED,CallingAppEvidence(true,true,true,true).reason) }
    @Test fun manualSelectionIsPreservedButNotCalledDetected() { assertEquals(CallingAppReason.MANUAL,CallingAppEvidence(true,selectedManually=true).reason) }
    @Test fun systemDialerIsHandledSeparately() { assertNull(CallingAppEvidence(true,true,true,true,isSystemDialer=true).reason) }
    @Test fun knownPackageMatchingRespectsAndroidCase() { assertTrue("com.Slack" in KnownCallingApps.packages);assertFalse("com.slack" in KnownCallingApps.packages) }
}
