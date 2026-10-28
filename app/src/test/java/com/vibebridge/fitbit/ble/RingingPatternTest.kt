package com.vibebridge.fitbit.ble

import com.vibebridge.fitbit.AirHapticPattern
import io.reactivex.Single
import io.reactivex.schedulers.TestScheduler
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class RingingPatternTest {
    private class Band(val clock: TestScheduler, var value: Int = 2) : HapticSettingsClient {
        var reads = 0
        var loseFirstResponse = false
        var failRestore = false
        val writes = mutableListOf<Pair<Long, Int>>()
        override fun read(): Single<Int> = Single.fromCallable { reads++; value }
        override fun write(intensity: Int): Single<Unit> = Single.fromCallable {
            writes.add(clock.now(TimeUnit.MILLISECONDS) to intensity)
            if (failRestore && intensity == 2) throw IllegalStateException("disconnected")
            value = intensity
            if (loseFirstResponse && writes.size == 1) throw IllegalStateException("lost response")
            Unit
        }
    }
    @Test fun continuousUsesOneReadSessionAndNoFiveSecondRestartGap() {
        val clock = TestScheduler(); val band = Band(clock); var ringing = true; var cleared = false
        val result = HapticPatternRunner(band, clock, verified = { cleared = true })
            .executeRinging(AirHapticPattern.CONTINUOUS, { ringing }).test()
        clock.advanceTimeBy(3, TimeUnit.SECONDS)
        assertEquals(1, band.reads)
        val starts = band.writes.filter { it.second == 3 }.map { it.first }
        assertTrue(starts.size >= 6)
        assertTrue(starts.zipWithNext().all { (a,b) -> b-a == 470L })
        ringing = false; clock.advanceTimeBy(60, TimeUnit.MILLISECONDS)
        result.assertComplete().assertNoErrors(); assertEquals(2,band.value); assertEquals(2,band.reads); assertTrue(cleared)
    }
    @Test fun urgentHasDistinctThreePulseBursts() {
        val clock = TestScheduler(); val band = Band(clock)
        HapticPatternRunner(band,clock).executeRinging(AirHapticPattern.URGENT,{true},4_000).test()
        clock.advanceTimeBy(4,TimeUnit.SECONDS)
        assertEquals(listOf(0L,1100L,2200L,3900L),band.writes.filter { it.second==3 }.map { it.first })
    }
    @Test fun answeringDuringPulseRestoresWithinPollingIntervalAndSendsNoMorePulses() {
        for (pattern in listOf(AirHapticPattern.URGENT,AirHapticPattern.CONTINUOUS)) {
            val clock=TestScheduler();val band=Band(clock);var ringing=true
            val result=HapticPatternRunner(band,clock).executeRinging(pattern,{ringing}).test()
            clock.advanceTimeBy(100,TimeUnit.MILLISECONDS);ringing=false;clock.advanceTimeBy(50,TimeUnit.MILLISECONDS)
            result.assertComplete().assertNoErrors();assertEquals(2,band.value)
            assertEquals(listOf(3,2),band.writes.map{it.second})
            clock.advanceTimeBy(1,TimeUnit.MINUTES);assertEquals(2,band.writes.size)
        }
    }
    @Test fun cancelledBeforeInitialReadCompletesNeverWritesOrCheckpoints() {
        val clock=TestScheduler();val band=Band(clock);var marked=false
        HapticPatternRunner(band,clock,checkpoint={marked=true}).executeRinging(AirHapticPattern.CONTINUOUS,{false})
            .test().assertComplete().assertValue{it.groupCount==0}
        assertFalse(marked);assertTrue(band.writes.isEmpty())
    }
    @Test fun missingEndEventStillStopsAtDurationLimitAndRestores() {
        val clock=TestScheduler();val band=Band(clock);var cleared=false
        val result=HapticPatternRunner(band,clock,verified={cleared=true})
            .executeRinging(AirHapticPattern.CONTINUOUS,{true},2_000).test()
        clock.advanceTimeBy(3,TimeUnit.SECONDS)
        result.assertComplete().assertNoErrors();assertEquals(2,band.value);assertTrue(cleared)
        assertTrue(band.writes.filter{it.second==3}.all{it.first<2000})
    }
    @Test fun lostResponseStillRestoresAndDoesNotRepeat() {
        val clock=TestScheduler();val band=Band(clock);band.loseFirstResponse=true;var cleared=false
        HapticPatternRunner(band,clock,verified={cleared=true}).executeRinging(AirHapticPattern.CONTINUOUS,{true})
            .test().assertError(IllegalStateException::class.java)
        assertEquals(2,band.value);assertEquals(listOf(3,2),band.writes.map{it.second});assertFalse(cleared)
    }
    @Test fun restorationFailureStopsTheSessionAndPreservesRecoveryCheckpoint() {
        val clock=TestScheduler();val band=Band(clock);band.failRestore=true;var pending:Int?=null
        val result=HapticPatternRunner(band,clock,{pending=it},{pending=null})
            .executeRinging(AirHapticPattern.CONTINUOUS,{true}).test()
        clock.advanceTimeBy(1,TimeUnit.SECONDS)
        result.assertError(HapticsRestoreException::class.java);assertEquals(2,pending)
        assertEquals(1,band.writes.count{it.second==3});assertEquals(3,band.writes.count{it.second==2})
    }
}
