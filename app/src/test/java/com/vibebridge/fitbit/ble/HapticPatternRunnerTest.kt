package com.vibebridge.fitbit.ble

import com.vibebridge.fitbit.AirHapticPattern
import io.reactivex.Single
import io.reactivex.schedulers.TestScheduler
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class HapticPatternRunnerTest {
    private class Band(var intensity: Int = 2): HapticSettingsClient {
        var reads = 0
        val writes = mutableListOf<Int>()
        var writeFault: (Int,Int) -> Throwable? = { _,_ -> null }
        var readFault: (Int) -> Throwable? = { null }
        var loseAlternateResponse = false
        var forceReadback: Int? = null
        override fun read():Single<Int> = Single.defer {
            reads++
            val error=readFault(reads)
            if(error!=null) Single.error(error) else Single.just(if(reads>1) forceReadback ?: intensity else intensity)
        }
        override fun write(value:Int):Single<Unit> = Single.defer {
            writes.add(value)
            val error=writeFault(writes.size,value)
            if(error!=null) Single.error(error) else {
                intensity=value
                if(loseAlternateResponse && writes.size==1) Single.error(RuntimeException("Lost response")) else Single.just(Unit)
            }
        }
    }
    @Test fun everyPatternRestoresOffLowAndHighAndClearsCheckpointOnlyAfterReadback() {
        AirHapticPattern.values().forEach { pattern -> (1..3).forEach { initial ->
            val band=Band(initial);val clock=TestScheduler();var checkpoint:Int?=null;var cleared=false
            val result=HapticPatternRunner(band,clock,{ checkpoint=it;assertTrue(band.writes.isEmpty()) },{
                assertEquals(2,band.reads);assertEquals(initial,band.intensity);cleared=true
            }).execute(pattern).test()
            assertEquals(initial,checkpoint)
            clock.advanceTimeBy(1,TimeUnit.MINUTES)
            result.assertComplete().assertNoErrors().assertValue { it.groupCount==pattern.groupCount && it.finalIntensity==initial }
            assertEquals(initial,band.intensity);assertTrue(cleared)
        } }
    }
    @Test fun failedInitialReadNeverWritesOrCreatesACheckpoint() {
        val b=Band();b.readFault={ RuntimeException("offline") };var marked=false
        HapticPatternRunner(b,TestScheduler(),{marked=true}).execute(AirHapticPattern.SINGLE).test().assertError(RuntimeException::class.java)
        assertTrue(b.writes.isEmpty());assertFalse(marked)
    }
    @Test fun invalidInitialValueNeverWrites() {
        listOf(0,4,-1).forEach { value ->
            val b=Band(value)
            HapticPatternRunner(b).execute(AirHapticPattern.SINGLE).test().assertError(IllegalArgumentException::class.java)
            assertTrue(b.writes.isEmpty())
        }
    }
    @Test fun lostAlternateWriteResponseStillRestoresTheBand() {
        val b=Band();b.loseAlternateResponse=true;val clock=TestScheduler();var cleared=false
        val result=HapticPatternRunner(b,clock,verified={cleared=true}).execute(AirHapticPattern.SINGLE).test()
        clock.advanceTimeBy(1,TimeUnit.MINUTES)
        result.assertError(RuntimeException::class.java);assertEquals(2,b.intensity)
        assertEquals(listOf(3,2),b.writes);assertFalse(cleared)
    }
    @Test fun restorationRetriesAreBoundedAndFailureKeepsCheckpoint() {
        val b=Band();b.writeFault={ _,value -> if(value==2) RuntimeException("disconnected") else null }
        val clock=TestScheduler();var cleared=false
        val result=HapticPatternRunner(b,clock,verified={cleared=true}).execute(AirHapticPattern.SINGLE).test()
        clock.advanceTimeBy(1,TimeUnit.MINUTES)
        result.assertError(HapticsRestoreException::class.java)
        assertEquals(3,b.writes.count { it==2 });assertFalse(cleared)
    }
    @Test fun failedReadbackIsNeverReportedAsSuccess() {
        val b=Band();b.readFault={ if(it>1) RuntimeException("read timeout") else null }
        val clock=TestScheduler();var cleared=false
        val result=HapticPatternRunner(b,clock,verified={cleared=true}).execute(AirHapticPattern.SINGLE).test()
        clock.advanceTimeBy(1,TimeUnit.MINUTES)
        result.assertError(HapticsRestoreException::class.java);assertFalse(cleared)
    }
    @Test fun mismatchedReadbackIsNeverReportedAsSuccess() {
        val b=Band();b.forceReadback=3;val clock=TestScheduler()
        val result=HapticPatternRunner(b,clock).execute(AirHapticPattern.SINGLE).test()
        clock.advanceTimeBy(1,TimeUnit.MINUTES);result.assertError(HapticsRestoreException::class.java)
    }
    @Test fun simulatedProcessDeathLeavesDurableCheckpointForExplicitRecovery() {
        val b=Band();val clock=TestScheduler();var pending:Int?=null
        val result=HapticPatternRunner(b,clock,{pending=it},{pending=null}).execute(AirHapticPattern.SINGLE).test()
        clock.triggerActions();assertEquals(3,b.intensity)
        result.dispose();clock.advanceTimeBy(1,TimeUnit.MINUTES)
        assertEquals(2,pending);assertEquals(3,b.intensity)
        HapticPatternRunner(b,clock,verified={pending=null}).recover(pending!!).test().assertComplete()
        assertEquals(2,b.intensity);assertNull(pending)
    }
    @Test fun failedCheckpointStoragePreventsAnyBandModification() {
        val b=Band()
        HapticPatternRunner(b,checkpoint={throw IllegalStateException("disk full")}).execute(AirHapticPattern.SINGLE)
            .test().assertError(IllegalStateException::class.java)
        assertTrue(b.writes.isEmpty())
    }
    @Test fun explicitRecoveryVerifiesBeforeClearingPendingState() {
        val b=Band(3);var cleared=false
        HapticPatternRunner(b,verified={assertEquals(2,b.intensity);cleared=true}).recover(2).test().assertComplete()
        assertEquals(listOf(2),b.writes);assertTrue(cleared)
    }
}
