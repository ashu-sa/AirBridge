package com.vibebridge.fitbit.calls
import org.junit.Assert.*
import org.junit.Test
class CallStateTrackerTest {
    @Test fun ringingSIMWinsOverIdleSIM() { val s=CallStateTracker(setOf(1,2));assertEquals(1,s.update(2,1));assertEquals(1,s.update(1,0)) }
    @Test fun initialIdleWaitsForOtherSIM() { val s=CallStateTracker(setOf(1,2));assertNull(s.update(1,0));assertEquals(1,s.update(2,1)) }
    @Test fun answerEndsRinging() { val s=CallStateTracker(setOf(1));assertEquals(1,s.update(1,1));assertEquals(2,s.update(1,2));assertEquals(0,s.update(1,0)) }
    @Test fun secondRingingCallSurvivesFirstAnswer() { val s=CallStateTracker(setOf(1,2));s.update(1,1);s.update(2,1);assertEquals(1,s.update(1,2)) }
    @Test fun unknownSIMIsIgnored() { assertNull(CallStateTracker(setOf(1)).update(2,1)) }
    @Test fun failedSIMRegistrationDoesNotHoldCallOpen() { val s=CallStateTracker(setOf(1,2));s.remove(2);assertEquals(1,s.update(1,1));assertEquals(0,s.update(1,0)) }
    @Test fun invalidStateIsIgnored() { assertNull(CallStateTracker(setOf(1)).update(1,7)) }
}
