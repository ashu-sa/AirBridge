package com.vibebridge.fitbit.service

import org.junit.Assert.*
import org.junit.Test

class NotificationStormTest {
    @Test fun hundredThousandSameAppUpdatesAreRateLimited() {
        val gate=NotificationAlertGate();var accepted=0
        repeat(100_000) { i ->
            if(gate.canAlert("event:$i","app",i.toLong())) {
                accepted++;gate.recordAccepted("event:$i","app",i.toLong())
            }
        }
        assertEquals(34,accepted)
    }
    @Test fun manyDifferentAppsStillRespectTheGlobalBudget() {
        val gate=NotificationAlertGate();var accepted=0
        repeat(100_000) { i ->
            val app="app:${i%1000}"
            if(gate.canAlert("event:$i",app,i.toLong())) {
                accepted++;gate.recordAccepted("event:$i",app,i.toLong())
            }
        }
        assertEquals(67,accepted)
    }
}
