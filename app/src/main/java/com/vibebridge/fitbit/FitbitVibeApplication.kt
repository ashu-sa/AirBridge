package com.vibebridge.fitbit

import android.app.Application
import timber.log.Timber

class FitbitVibeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        io.reactivex.plugins.RxJavaPlugins.setErrorHandler { throwable ->
            val actual = if (throwable is io.reactivex.exceptions.UndeliverableException) throwable.cause ?: throwable else throwable
            Timber.w(actual, "Suppressed undeliverable RxJava exception: %s", actual.message)
        }
        com.vibebridge.fitbit.calls.PhoneCallMonitor.refresh(this)
        // Initialize Golden Gate lazily only when an allowed alert needs BLE.
    }
}
