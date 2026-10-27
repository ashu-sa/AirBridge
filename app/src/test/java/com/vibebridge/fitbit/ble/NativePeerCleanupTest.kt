package com.vibebridge.fitbit.ble

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class NativePeerCleanupTest {
    @Test(timeout=4_000) fun blockingCleanupCannotBlockTheNativeCallbackThread() {
        val callbackReturned=CountDownLatch(1);val allowCleanup=CountDownLatch(1);val finished=CountDownLatch(1)
        val cleanupThread=AtomicReference<String>()
        val worker=Thread({
            NativePeerCleanup.close {
                cleanupThread.set(Thread.currentThread().name)
                allowCleanup.await(2,TimeUnit.SECONDS)
            }.subscribe({finished.countDown()},{finished.countDown()})
            callbackReturned.countDown()
        },"simulated-native-event-loop").apply { isDaemon=true;start() }
        try {
            assertTrue("Cleanup blocked the callback",callbackReturned.await(1,TimeUnit.SECONDS))
            allowCleanup.countDown()
            assertTrue(finished.await(1,TimeUnit.SECONDS))
            assertNotEquals(worker.name,cleanupThread.get())
        } finally { allowCleanup.countDown() }
    }
}
