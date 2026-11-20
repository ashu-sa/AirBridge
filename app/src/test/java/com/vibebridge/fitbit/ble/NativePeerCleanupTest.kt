package com.vibebridge.fitbit.ble

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class NativePeerCleanupTest {
    @Test(timeout=4_000) fun replacementServiceWaitsForPreviousCleanup() {
        val entered = CountDownLatch(1)
        val allowRelease = CountDownLatch(1)
        val nextStarted = java.util.concurrent.atomic.AtomicBoolean(false)
        val first = NativePeerCleanup.close { entered.countDown(); allowRelease.await(2, TimeUnit.SECONDS) }.test()
        assertTrue(entered.await(1, TimeUnit.SECONDS))
        val next = NativePeerCleanup.awaitIdle().andThen(io.reactivex.Completable.fromAction { nextStarted.set(true) }).test()
        try { assertFalse(nextStarted.get()); next.assertNotComplete() }
        finally { allowRelease.countDown() }
        first.awaitDone(2, TimeUnit.SECONDS).assertComplete().assertNoErrors()
        next.awaitDone(2, TimeUnit.SECONDS).assertComplete().assertNoErrors()
        assertTrue(nextStarted.get())
    }

    @Test(timeout=4_000) fun asynchronousCompletionAlsoDisposesOffCallbackThread() {
        val disconnect = io.reactivex.subjects.CompletableSubject.create()
        val releaseStarted = CountDownLatch(1)
        val allowRelease = CountDownLatch(1)
        val callbackReturned = CountDownLatch(1)
        val result = NativePeerCleanup.close(disconnect) {
            releaseStarted.countDown()
            allowRelease.await(2, TimeUnit.SECONDS)
        }.test()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (!disconnect.hasObservers() && System.nanoTime() < deadline) Thread.yield()
        assertTrue(disconnect.hasObservers())
        Thread({ disconnect.onComplete(); callbackReturned.countDown() }, "native-callback").start()
        try {
            assertTrue(releaseStarted.await(1, TimeUnit.SECONDS))
            assertTrue("Disposal blocked native callback", callbackReturned.await(1, TimeUnit.SECONDS))
        } finally { allowRelease.countDown() }
        result.awaitDone(2, TimeUnit.SECONDS).assertComplete().assertNoErrors()
    }

    @Test fun disconnectMustCompleteBeforeResourcesAreReleased() {
        val disconnect = io.reactivex.subjects.CompletableSubject.create()
        val released = java.util.concurrent.atomic.AtomicInteger()
        val result = NativePeerCleanup.close(disconnect) { released.incrementAndGet() }.test()
        result.assertNotComplete()
        assertEquals(0, released.get())
        // Wait until the worker has subscribed, so completion exercises the asynchronous path.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1)
        while (!disconnect.hasObservers() && System.nanoTime() < deadline) Thread.yield()
        assertTrue(disconnect.hasObservers())
        disconnect.onComplete()
        result.awaitDone(2, TimeUnit.SECONDS).assertComplete().assertNoErrors()
        assertEquals(1, released.get())
    }

    @Test fun failedDisconnectStillClosesResourcesAndRemainsAnError() {
        val released = java.util.concurrent.atomic.AtomicInteger()
        val error = IllegalStateException("disconnect failed")
        NativePeerCleanup.close(io.reactivex.Completable.error(error)) { released.incrementAndGet() }
            .test().awaitDone(2, TimeUnit.SECONDS).assertError(error).assertNotComplete()
        assertEquals(1, released.get())
    }

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
