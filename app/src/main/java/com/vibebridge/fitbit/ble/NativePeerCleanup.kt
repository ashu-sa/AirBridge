package com.vibebridge.fitbit.ble

import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/** Serial, process-wide barrier: a replacement service must not race native disposal. */
object NativePeerCleanup {
    private var pending: Completable? = null

    @Synchronized
    fun awaitIdle(): Completable = pending ?: Completable.complete()

    fun close(action: () -> Unit): Completable = close(Completable.complete(), action)

    @Synchronized
    fun close(disconnect: Completable, release: () -> Unit): Completable {
        val previous = pending ?: Completable.complete()
        lateinit var task: Completable
        val ownCleanup = disconnect.timeout(6, TimeUnit.SECONDS)
            .observeOn(Schedulers.io())
            .onErrorResumeNext { error ->
                Completable.fromAction(release).andThen(Completable.error(error))
            }
            .andThen(Completable.fromAction(release))
            .timeout(10, TimeUnit.SECONDS)
        task = previous.andThen(ownCleanup)
            .subscribeOn(Schedulers.io())
            .doOnTerminate { synchronized(this) { if (pending === task) pending = null } }
            .cache()
        pending = task
        return task
    }
}
