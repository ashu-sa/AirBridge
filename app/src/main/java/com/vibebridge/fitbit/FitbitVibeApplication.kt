package com.vibebridge.fitbit

import android.app.Application
import com.vibebridge.fitbit.ble.AirBridgeRuntime
import timber.log.Timber

class FitbitVibeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        try {
            AirBridgeRuntime.ensureInitialized(this)
            Timber.i("Fitbit Vibe Application initialized successfully with GoldenGate runtime")
        } catch (t: Throwable) {
            Timber.e(t, "Failed to initialize GoldenGate runtime on application startup")
        }
    }
}
