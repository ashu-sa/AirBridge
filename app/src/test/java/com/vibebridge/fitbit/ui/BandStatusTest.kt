package com.vibebridge.fitbit.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BandStatusTest {
    @Test fun pastSuccessDoesNotClaimLiveConnection() {
        assertEquals(BandStatus.READY, BandStatus.resolve(true, true, true, false, false, false,
            "Band connection verified; no vibration sent. Alerts reconnect on demand."))
    }
    @Test fun liveConnectionOverridesPreviousFailure() {
        assertEquals(BandStatus.CONNECTED, BandStatus.resolve(true, true, true, false, true, true, "Error: old failure"))
    }
    @Test fun recoveryAndMissingPrerequisitesRequireActionEvenAfterSuccess() {
        for (flags in listOf(listOf(false,true,true,false), listOf(true,false,true,false),
            listOf(true,true,false,false), listOf(true,true,true,true))) {
            assertEquals(BandStatus.ACTION_REQUIRED, BandStatus.resolve(flags[0],flags[1],flags[2],flags[3],true,false,"Success"))
        }
    }
    @Test fun retryShowsProgressAndTerminalFailureShowsRed() {
        assertEquals(BandStatus.CONNECTING, BandStatus.resolve(true,true,true,false,false,true,"Connection attempt failed. Retrying"))
        assertEquals(BandStatus.FAILED, BandStatus.resolve(true,true,true,false,false,false,"Error: band not found nearby"))
    }
}
