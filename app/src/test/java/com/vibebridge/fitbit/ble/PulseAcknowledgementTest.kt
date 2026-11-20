package com.vibebridge.fitbit.ble

import com.vibebridge.fitbit.AirHapticPattern
import io.reactivex.Single
import io.reactivex.schedulers.TestScheduler
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class PulseAcknowledgementTest {
    @Test fun acknowledgementSurvivesLaterRestorationFailure() {
        val clock = TestScheduler()
        var acknowledgements = 0
        val client = object : HapticSettingsClient {
            override fun read() = Single.just(3)
            override fun write(intensity: Int): Single<Unit> = if (intensity == 2) Single.just(Unit) else Single.error(RuntimeException("restore failed"))
        }
        val result = HapticPatternRunner(client, clock, pulseConfirmed = { acknowledgements++ }).execute(AirHapticPattern.SINGLE).test()
        clock.advanceTimeBy(10, TimeUnit.SECONDS)
        result.assertError(HapticsRestoreException::class.java)
        assertEquals(1, acknowledgements)
    }
    @Test fun rejectedPulseIsNotCountedAsDelivery() {
        val clock = TestScheduler()
        var acknowledgements = 0
        val client = object : HapticSettingsClient {
            override fun read() = Single.just(3)
            override fun write(intensity: Int): Single<Unit> = if (intensity == 2) Single.error(RuntimeException("write failed")) else Single.just(Unit)
        }
        val result = HapticPatternRunner(client, clock, pulseConfirmed = { acknowledgements++ }).execute(AirHapticPattern.SINGLE).test()
        clock.advanceTimeBy(10, TimeUnit.SECONDS)
        result.assertError(RuntimeException::class.java)
        assertEquals(0, acknowledgements)
    }
}
