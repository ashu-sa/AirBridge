package com.vibebridge.fitbit.ble

import io.reactivex.Single
import org.junit.Assert.*
import org.junit.Test

class ConnectionCheckTest {
    private class Client(val intensity: Int = 2, val fail: Boolean = false) : HapticSettingsClient {
        var writes = 0
        override fun read(): Single<Int> = if (fail) Single.error(IllegalStateException("read failed")) else Single.just(intensity)
        override fun write(intensity: Int): Single<Unit> { writes++;return Single.just(Unit) }
    }
    @Test fun readsWithoutPulsingOrChangingRecoveryCheckpoint() {
        val client = Client();var checkpoints=0;var verifications=0
        val result=HapticPatternRunner(client, checkpoint={checkpoints++}, verified={verifications++}).checkConnection().blockingGet()
        assertEquals(AirHapticExecutionResult(2,2,0),result);assertEquals(0,client.writes);assertEquals(0,checkpoints);assertEquals(0,verifications)
    }
    @Test fun failedCheckNeverWrites() { val client=Client(fail=true);HapticPatternRunner(client).checkConnection().test().assertError(IllegalStateException::class.java);assertEquals(0,client.writes) }
    @Test fun invalidSettingFailsWithoutWrites() { val client=Client(intensity=99);HapticPatternRunner(client).checkConnection().test().assertError(IllegalArgumentException::class.java);assertEquals(0,client.writes) }
}
