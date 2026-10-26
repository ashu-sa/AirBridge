package com.vibebridge.fitbit.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fitbit.bluetooth.fbgatt.FitbitBluetoothDevice
import com.fitbit.bluetooth.fbgatt.FitbitGatt
import com.fitbit.bluetooth.fbgatt.GattConnection
import com.fitbit.bluetooth.fbgatt.rx.PeripheralConnectionChangeListener
import com.fitbit.goldengate.bindings.coap.CoapEndpoint
import com.fitbit.goldengate.bindings.coap.CoapEndpointBuilder
import com.fitbit.goldengate.bindings.node.BluetoothAddressNodeKey
import com.fitbit.goldengate.bindings.stack.DtlsSocketNetifGattlink
import com.fitbit.goldengate.bt.DEFAULT_CLIENT_ADDRESS
import com.fitbit.goldengate.bt.DEFAULT_SERVER_ADDRESS
import com.fitbit.goldengate.bt.PeerRole
import com.fitbit.goldengate.node.stack.StackPeer
import com.vibebridge.fitbit.AirHapticPattern
import com.vibebridge.fitbit.MainActivity
import com.vibebridge.fitbit.ble.AirBridgeDeviceSettings
import com.vibebridge.fitbit.ble.AirBridgeRuntime
import com.vibebridge.fitbit.ble.AirDirectSettingsProtocol
import com.vibebridge.fitbit.ble.AirHapticExecutionResult
import com.vibebridge.fitbit.ble.AirHapticPatternExecutor
import com.vibebridge.fitbit.ble.DiagnosticTlsIdentityRecorder
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.net.Inet4Address
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

data class AirNotificationTrigger(
    val sourcePackage: String,
    val pattern: AirHapticPattern,
    val isCall: Boolean = false
)

/**
 * Pure Bluetooth Low Energy (BLE) background bridge for Fitbit Air:
 * scan -> connect -> Gattlink -> DTLS 1.2 -> CoAP POST /settings -> haptic pulse -> verify & disconnect.
 *
 * Runs 100% in background without any accessibility service, screen popups, or foreground disruption.
 */
class AirBridgeOneShotService : Service() {
    private val queue = ArrayDeque<AirNotificationTrigger>()
    private val disposables = CompositeDisposable()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var processing = false
    private var activePeer: StackPeer<CoapEndpoint>? = null
    private var wakeLock: PowerManager.WakeLock? = null
    @Volatile private var isCallRinging = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ready for on-demand BLE haptics"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_CANCEL_CALL) {
            isCallRinging = false
            updateStatus("Call ended; returning to idle")
            queue.removeAll { it.isCall }
            if (!processing) {
                stopSelfIfQueueEmpty()
            }
            return START_NOT_STICKY
        }

        val sourcePackage = intent?.getStringExtra(EXTRA_SOURCE_PACKAGE)
        val pattern = intent?.getStringExtra(EXTRA_PATTERN)?.let(AirHapticPattern::fromWireName)
        val isCall = intent?.getBooleanExtra(EXTRA_IS_CALL, false) ?: false

        if (sourcePackage != null && pattern != null) {
            if (isCall) {
                isCallRinging = true
                // Prioritize calls at the front of queue
                queue.addFirst(AirNotificationTrigger(sourcePackage, pattern, isCall = true))
            } else {
                if (queue.size < MAX_QUEUE_SIZE) {
                    queue.addLast(AirNotificationTrigger(sourcePackage, pattern, isCall = false))
                } else {
                    updateStatus("Queue full; discarded $sourcePackage")
                }
            }
        }
        processNext()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        disposables.dispose()
        releasePeer()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun processNext() {
        if (processing) return
        val trigger = queue.pollFirst()
        if (trigger == null) {
            stopSelfIfQueueEmpty()
            return
        }
        acquireWakeLock()
        processing = true
        updateStatus("Connecting to Fitbit Air: ${trigger.sourcePackage} [${trigger.pattern.displayName}]")
        disposables.add(
            executeWithRetry(trigger, 0)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { result ->
                        val status = "Success: ${trigger.sourcePackage} [${trigger.pattern.displayName}], " +
                                "${result.groups.size} pulse group(s), restored to " +
                                AirDirectSettingsProtocol.hapticsIntensityName(result.finalIntensity)
                        updateStatus(status)
                        processing = false

                        // If it's a call and the phone is still ringing, queue another pulse after a short breather
                        if (trigger.isCall && isCallRinging) {
                            mainHandler.postDelayed({
                                if (isCallRinging) {
                                    queue.addFirst(trigger)
                                }
                                processNext()
                            }, 1200L)
                        } else {
                            processNext()
                        }
                    },
                    { throwable ->
                        val status = "Error: ${trigger.sourcePackage} [${trigger.pattern.displayName}]: " +
                                "${throwable.javaClass.simpleName}: ${throwable.message ?: "unknown"}"
                        Timber.w(throwable, status)
                        updateStatus(status)
                        processing = false
                        processNext()
                    }
                )
        )
    }

    private fun stopSelfIfQueueEmpty() {
        releaseWakeLock()
        stopForeground(true)
        stopSelf()
    }

    private fun executeWithRetry(
        trigger: AirNotificationTrigger,
        attemptIndex: Int
    ): Single<AirHapticExecutionResult> = executeOnce(trigger)
        .onErrorResumeNext { throwable ->
            releasePeer()
            if (attemptIndex >= RETRY_DELAYS_MILLIS.size || (trigger.isCall && !isCallRinging)) {
                Single.error(throwable)
            } else {
                val delay = RETRY_DELAYS_MILLIS[attemptIndex]
                updateStatus(
                    "Band busy, retrying in ${delay / 1000}s (Google Health sync may be active)..."
                )
                Single.timer(delay, TimeUnit.MILLISECONDS)
                    .flatMap { executeWithRetry(trigger, attemptIndex + 1) }
            }
        }

    private fun executeOnce(trigger: AirNotificationTrigger): Single<AirHapticExecutionResult> {
        val address = AirBridgeDeviceSettings.loadAddress(this)
            ?: return Single.error(IllegalStateException("Fitbit Air address not configured"))
        return Single.fromCallable {
            AirBridgeRuntime.ensureInitialized(this)
            address
        }.flatMap { scanForDevice(it) }
            .flatMap { connection -> connectStack(connection.device.address) }
            .flatMap { endpoint ->
                updateStatus("DTLS 1.2 Connected! Pulsing ${trigger.pattern.displayName}")
                AirHapticPatternExecutor.execute(endpoint, trigger.pattern)
            }
            .flatMap { result ->
                releasePeer()
                Single.timer(RELEASE_SETTLE_MILLIS, TimeUnit.MILLISECONDS).map { result }
            }
            .doOnError { releasePeer() }
    }

    private fun scanForDevice(address: String): Single<GattConnection> = Single.create { emitter ->
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter: BluetoothAdapter = manager.adapter
            ?: run {
                emitter.onError(IllegalStateException("Bluetooth adapter unavailable"))
                return@create
            }
        if (!adapter.isEnabled) {
            emitter.onError(IllegalStateException("Bluetooth is disabled"))
            return@create
        }
        val scanner = adapter.bluetoothLeScanner
            ?: run {
                emitter.onError(IllegalStateException("BLE scanner unavailable"))
                return@create
            }
        lateinit var callback: ScanCallback
        val stopScan = {
            mainHandler.removeCallbacksAndMessages(callback)
            runCatching { scanner.stopScan(callback) }
        }
        callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (!result.device.address.equals(address, ignoreCase = true)) return
                stopScan()
                if (!emitter.isDisposed) {
                    runCatching { registerScannedDevice(result) }
                        .onSuccess(emitter::onSuccess)
                        .onFailure(emitter::onError)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                stopScan()
                if (!emitter.isDisposed) emitter.onError(IllegalStateException("BLE scan failed: $errorCode"))
            }
        }
        val timeout = Runnable {
            stopScan()
            if (!emitter.isDisposed) {
                emitter.onError(IllegalStateException("Fitbit Air not advertising (scan timeout)"))
            }
        }
        emitter.setCancellable {
            mainHandler.removeCallbacks(timeout)
            stopScan()
        }
        try {
            scanner.startScan(
                listOf(ScanFilter.Builder().setDeviceAddress(address).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
                callback
            )
            mainHandler.postDelayed(timeout, SCAN_TIMEOUT_MILLIS)
        } catch (throwable: Throwable) {
            stopScan()
            if (!emitter.isDisposed) emitter.onError(throwable)
        }
    }

    private fun registerScannedDevice(result: ScanResult): GattConnection {
        val fitbitGatt = FitbitGatt.getInstance()
        fitbitGatt.addBackgroundScannedDeviceConnection(result)
        fitbitGatt.getMatchingConnectionsForDeviceNames(null)
            .firstOrNull { it.device.address.equals(result.device.address, ignoreCase = true) }
            ?.let { return it }
        val wrappedDevice = FitbitBluetoothDevice(result.device)
        return GattConnection(wrappedDevice, Looper.getMainLooper()).also {
            fitbitGatt.putConnectionIntoDevices(wrappedDevice, it)
        }
    }

    private fun connectStack(address: String): Single<CoapEndpoint> {
        updateStatus("Fitbit Air found. Establishing Gattlink & DTLS 1.2...")
        DiagnosticTlsIdentityRecorder.clear()
        val stackConfig = DtlsSocketNetifGattlink(
            Inet4Address.getByName(DEFAULT_SERVER_ADDRESS) as Inet4Address,
            0,
            Inet4Address.getByName(DEFAULT_CLIENT_ADDRESS) as Inet4Address,
            0
        )
        val peer = StackPeer(
            BluetoothAddressNodeKey(address),
            PeerRole.Peripheral,
            stackConfig,
            CoapEndpointBuilder(),
            { connection -> PeripheralConnectionChangeListener().register(connection) },
            { stack -> stack.dtlsEventObservable }
        )
        activePeer = peer
        return peer.connection()
            .firstOrError()
            .timeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .map { peer.stackService }
    }

    @Synchronized
    private fun releasePeer() {
        activePeer?.let { peer ->
            activePeer = null
            runCatching { peer.close() }
                .onFailure { Timber.w(it, "Failed to close one-shot Air peer") }
        }
    }

    private fun updateStatus(status: String) {
        Timber.i("Air one-shot: %s", status)
        AirBridgeDeviceSettings.saveLastStatus(this, status)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))

        // Notify Compose UI
        try {
            sendBroadcast(Intent(ACTION_STATUS_UPDATED).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATUS_TEXT, status)
            })
        } catch (ignored: Exception) {}
    }

    private fun acquireWakeLock() {
        val current = wakeLock
        if (current?.isHeld == true) return
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fitbitvibebridge:one-shot")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MILLIS) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun buildNotification(status: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("Fitbit Vibe Bridge (Pure BLE)")
        .setContentText(status)
        .setStyle(NotificationCompat.BigTextStyle().bigText(status))
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Fitbit Air Pure BLE Bridge",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        const val ACTION_STATUS_UPDATED = "com.vibebridge.fitbit.STATUS_UPDATED"
        const val EXTRA_STATUS_TEXT = "status_text"
        const val ACTION_CANCEL_CALL = "com.vibebridge.fitbit.CANCEL_CALL"

        private const val EXTRA_SOURCE_PACKAGE = "source_package"
        private const val EXTRA_PATTERN = "pattern"
        private const val EXTRA_IS_CALL = "is_call"

        private const val CHANNEL_ID = "air_bridge_ble_connection"
        private const val NOTIFICATION_ID = 24001
        private const val MAX_QUEUE_SIZE = 10
        private const val SCAN_TIMEOUT_MILLIS = 8_000L
        private const val CONNECT_TIMEOUT_SECONDS = 20L
        private const val RELEASE_SETTLE_MILLIS = 700L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 120_000L
        private val RETRY_DELAYS_MILLIS = listOf(2_000L, 4_000L)

        fun enqueue(context: Context, trigger: AirNotificationTrigger): Boolean {
            if (AirBridgeDeviceSettings.loadAddress(context) == null) return false
            val intent = Intent(context, AirBridgeOneShotService::class.java)
                .putExtra(EXTRA_SOURCE_PACKAGE, trigger.sourcePackage)
                .putExtra(EXTRA_PATTERN, trigger.pattern.wireName)
                .putExtra(EXTRA_IS_CALL, trigger.isCall)
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.getOrElse {
                Timber.w(it, "Unable to start one-shot Air service")
                AirBridgeDeviceSettings.saveLastStatus(
                    context,
                    "Failed to start BLE service: ${it.javaClass.simpleName}: ${it.message ?: "unknown"}"
                )
                false
            }
        }

        fun cancelCall(context: Context) {
            val intent = Intent(context, AirBridgeOneShotService::class.java).apply {
                action = ACTION_CANCEL_CALL
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
