package com.vibebridge.fitbit.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.vibebridge.fitbit.ble.AirBridgeDeviceSettings
import timber.log.Timber

/** Event-driven fallback while the app process is alive. No polling, wake lock or BLE session. */
object PhoneCallMonitor {
    private val unregister = mutableListOf<() -> Unit>()
    private var registeredIds: Set<Int> = emptySet()
    private var subscriptions: SubscriptionManager? = null
    private var subscriptionsListener: SubscriptionManager.OnSubscriptionsChangedListener? = null

    @Suppress("DEPRECATION")
    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            !AirBridgeDeviceSettings.isCallAlertEnabled(app)) {
            stop()
            return
        }
        if (!force && unregister.isNotEmpty()) return
        val manager = app.getSystemService(TelephonyManager::class.java) ?: return
        val subscriptionManager = app.getSystemService(SubscriptionManager::class.java)
        if (subscriptionsListener == null && subscriptionManager != null) {
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() { refresh(app, force = true) }
            }
            try {
                subscriptions = subscriptionManager
                subscriptionsListener = listener
                if (Build.VERSION.SDK_INT >= 30) subscriptionManager.addOnSubscriptionsChangedListener(ContextCompat.getMainExecutor(app), listener)
                else subscriptionManager.addOnSubscriptionsChangedListener(listener)
            } catch (error: RuntimeException) {
                subscriptionsListener = null
                Timber.w(error, "Unable to observe SIM changes")
            }
        }
        val ids = try {
            subscriptionManager?.activeSubscriptionInfoList?.map { it.subscriptionId }?.toSet().orEmpty()
        } catch (error: RuntimeException) {
            Timber.w(error, "Unable to read active SIM subscriptions")
            emptySet()
        }
        val effectiveIds = ids.ifEmpty { setOf(SubscriptionManager.getDefaultSubscriptionId()) }
        if (registeredIds == effectiveIds && unregister.isNotEmpty()) return
        unregisterCallbacks()
        val tracker = CallStateTracker(effectiveIds)
        effectiveIds.forEach { id ->
            val telephony = if (id >= 0) manager.createForSubscriptionId(id) else manager
            val onState: (Int) -> Unit = { state ->
                tracker.update(id, state)?.let { CallEventHandler.handle(app, it, "telephony callback") }
            }
            try {
                if (Build.VERSION.SDK_INT >= 31) {
                    val callback = ModernCallback(onState)
                    telephony.registerTelephonyCallback(ContextCompat.getMainExecutor(app), callback)
                    unregister.add { telephony.unregisterTelephonyCallback(callback) }
                } else {
                    val listener = object : PhoneStateListener() {
                        override fun onCallStateChanged(state: Int, ignoredNumber: String?) { onState(state) }
                    }
                    telephony.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                    unregister.add { telephony.listen(listener, PhoneStateListener.LISTEN_NONE) }
                }
                Timber.i("Phone-state callback registered")
            } catch (error: RuntimeException) {
                tracker.remove(id)
                Timber.w(error, "Phone-state callback registration failed")
            }
        }
        registeredIds = effectiveIds
    }

    private fun unregisterCallbacks() {
        unregister.forEach { runCatching { it() } }
        unregister.clear()
        registeredIds = emptySet()
    }
    private fun stop() {
        unregisterCallbacks()
        subscriptionsListener?.let { listener -> runCatching { subscriptions?.removeOnSubscriptionsChangedListener(listener) } }
        subscriptionsListener = null
        subscriptions = null
    }
    @RequiresApi(31)
    private class ModernCallback(val onState: (Int) -> Unit) : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) { onState(state) }
    }
}
