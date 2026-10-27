package com.vibebridge.fitbit.ble

import io.reactivex.Completable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

/** JNI disposal can block. Keep it off native callback threads and bound the caller's wait. */
object NativePeerCleanup {
    fun close(action: () -> Unit): Completable = Completable.fromAction(action)
        .subscribeOn(Schedulers.io()).timeout(10, TimeUnit.SECONDS)
}
