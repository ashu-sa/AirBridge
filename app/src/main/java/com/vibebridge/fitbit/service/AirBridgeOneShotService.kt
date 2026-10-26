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
import com.fitbit.bluetooth.fbgatt.BitGattHelper
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
    val isCall: Boolean = false,
    val isContinuousCall: Boolean = false
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
    private var activeEndpoint: CoapEndpoint? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentStatus: String = "Ready (Idle)"
    @Volatile private var isCallRinging = false
    private var callRingStartTime = 0L
    private var callRingBurstCount = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ready for on-demand BLE haptics"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_CANCEL_CALL) {
            isCallRinging = false
            activeEndpoint = null
            releasePeer()
            updateStatus("Call ended; returning to idle")
            queue.removeAll { it.isCall }
            if (!processing) {
                stopSelfIfQueueEmpty()
            }
            return START_NOT_STICKY
        }

        if (action == ACTION_STOP_ALL) {
            isCallRinging = false
            activeEndpoint = null
            queue.clear()
            disposables.clear()
            releasePeer()
            updateStatus("Vibration stopped")
            processing = false
            stopSelfIfQueueEmpty()
            return START_NOT_STICKY
        }

        if (action == ACTION_RESET_BLE) {
            isCallRinging = false
            activeEndpoint = null
            queue.clear()
            disposables.clear()
            releasePeer()
            val fitbitGatt = FitbitGatt.getInstance()
            runCatching {
                fitbitGatt.getMatchingConnectionsForDeviceNames(null).forEach { conn ->
                    runCatching { conn.close() }
                }
                BitGattHelper.clearConnections(fitbitGatt)
            }
            DiagnosticTlsIdentityRecorder.clear()
            processing = false
            updateStatus("Connection reset • All caches cleared")
            stopSelfIfQueueEmpty()
            return START_NOT_STICKY
        }

        val sourcePackage = intent?.getStringExtra(EXTRA_SOURCE_PACKAGE)
        val pattern = intent?.getStringExtra(EXTRA_PATTERN)?.let(AirHapticPattern::fromWireName)
        val isCall = intent?.getBooleanExtra(EXTRA_IS_CALL, false) ?: false
        val isContinuousCall = intent?.getBooleanExtra(EXTRA_IS_CONTINUOUS_CALL, false) ?: false

        if (sourcePackage != null && pattern != null) {
            if (isCall) {
                if (isContinuousCall) {
                    if (!isCallRinging) {
                        callRingStartTime = System.currentTimeMillis()
                        callRingBurstCount = 0
                    }
                    isCallRinging = true
                }
                // Prioritize calls at the front of queue
                queue.addFirst(AirNotificationTrigger(sourcePackage, pattern, isCall = true, isContinuousCall = isContinuousCall))
            } else {
                if (queue.size < MAX_QUEUE_SIZE) {
                    queue.addLast(AirNotificationTrigger(sourcePackage, pattern, isCall = false, isContinuousCall = false))
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
        isVibrating = false
        disposables.dispose()
        releasePeer()
        releaseWakeLock()
        try {
            sendBroadcast(Intent(ACTION_STATUS_UPDATED).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATUS_TEXT, currentStatus)
                putExtra(EXTRA_IS_ACTIVE, false)
            })
        } catch (ignored: Exception) {}
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
                        if (trigger.isCall && trigger.isContinuousCall && isCallRinging) {
                            callRingBurstCount++
                            val elapsed = System.currentTimeMillis() - callRingStartTime
                            if (elapsed > MAX_CALL_RING_DURATION_MILLIS || callRingBurstCount > MAX_CALL_BURSTS) {
                                Timber.i("Call ringing safety limit reached (${elapsed}ms, ${callRingBurstCount} bursts). Releasing haptics.")
                                isCallRinging = false
                                activeEndpoint = null
                                releasePeer()
                                processNext()
                            } else {
                                mainHandler.postDelayed({
                                    if (isCallRinging) {
                                        queue.addFirst(trigger)
                                    }
                                    processNext()
                                }, 1200L)
                            }
                        } else {
                            processNext()
                        }
                    },
                    { throwable ->
                        val status = formatFriendlyError(trigger, throwable)
                        Timber.w(throwable, status)
                        updateStatus(status)
                        processing = false
                        activeEndpoint = null
                        releasePeer()
                        val fitbitGatt = FitbitGatt.getInstance()
                        runCatching { BitGattHelper.clearConnections(fitbitGatt) }
                        processNext()
                    }
                )
        )
    }

    private fun formatFriendlyError(trigger: AirNotificationTrigger, throwable: Throwable): String {
        val rawMsg = throwable.message.orEmpty()
        val errType = throwable.javaClass.simpleName
        val hint = when {
            rawMsg.contains("Bluetooth is disabled", ignoreCase = true) ->
                "Bluetooth is turned off on your phone. Turn on Bluetooth to reconnect."
            rawMsg.contains("scan timeout", ignoreCase = true) || rawMsg.contains("not advertising", ignoreCase = true) ->
                "Fitbit Air not found nearby. Make sure band is awake, charged, and in range."
            rawMsg.contains("empty", ignoreCase = true) || rawMsg.contains("NoSuchElement", ignoreCase = true) ->
                "Band disconnected or busy. Google Health may be syncing, or band is asleep. Tap 'Reset Link'."
            rawMsg.contains("timed out", ignoreCase = true) || rawMsg.contains("timeout", ignoreCase = true) ->
                "Connection timed out. Google Health may be syncing, or band is out of range."
            rawMsg.contains("133") || rawMsg.contains("GATT", ignoreCase = true) ->
                "Bluetooth connection interrupted (status 133). Tap 'Reset Connection'."
            rawMsg.contains("address not configured", ignoreCase = true) ->
                "No Fitbit Air address paired. Tap 'Auto-Detect Band' to find it."
            else ->
                "Connection glitch ($errType). Tap 'Reset Connection' to clear."
        }
        return "Error: ${trigger.sourcePackage} [${trigger.pattern.displayName}]: $hint"
    }

    private fun stopSelfIfQueueEmpty() {
        releaseWakeLock()
        isVibrating = false
        try {
            sendBroadcast(Intent(ACTION_STATUS_UPDATED).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATUS_TEXT, currentStatus)
                putExtra(EXTRA_IS_ACTIVE, false)
            })
        } catch (ignored: Exception) {}
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

        val getEndpoint: Single<CoapEndpoint> = activeEndpoint?.let { Single.just(it) }
            ?: Single.fromCallable {
                AirBridgeRuntime.ensureInitialized(this)
                address
            }.flatMap { resolveDeviceConnection(it) }
                .flatMap { connection -> connectStack(connection.device.address) }
                .doOnSuccess { endpoint ->
                    if (trigger.isCall && trigger.isContinuousCall) {
                        activeEndpoint = endpoint
                    }
                }

        return getEndpoint
            .flatMap { endpoint ->
                updateStatus("Connected! Pulsing ${trigger.pattern.displayName}")
                AirHapticPatternExecutor.execute(endpoint, trigger.pattern)
                    .timeout(25, TimeUnit.SECONDS)
            }
            .flatMap { result ->
                if (!trigger.isCall || !trigger.isContinuousCall || !isCallRinging) {
                    activeEndpoint = null
                    releasePeer()
                    Single.timer(RELEASE_SETTLE_MILLIS, TimeUnit.MILLISECONDS).map { result }
                } else {
                    Single.just(result)
                }
            }
            .doOnError {
                activeEndpoint = null
                releasePeer()
            }
    }

    private fun resolveDeviceConnection(address: String): Single<GattConnection> = Single.create { emitter ->
        val fitbitGatt = FitbitGatt.getInstance()
        val normalizedAddress = address.uppercase()

        // 1. Ask BitGatt to add any known connected/bonded devices into connectionMap
        runCatching { fitbitGatt.addConnectedDevices() }

        // 2. Invalidate any existing connection so each pulse connects freshly
        val existing = fitbitGatt.getMatchingConnectionsForDeviceNames(null)
            .firstOrNull { it.device.address.equals(normalizedAddress, ignoreCase = true) }
            ?: fitbitGatt.getConnectionForAddress(normalizedAddress)

        existing?.let {
            Timber.i("Air one-shot: Closing prior connection for %s", normalizedAddress)
            runCatching { it.close() }
            BitGattHelper.removeConnectionForAddress(fitbitGatt, normalizedAddress)
        }

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            if (!emitter.isDisposed) emitter.onError(IllegalStateException("Bluetooth is disabled"))
            return@create
        }

        // 3. Resolve device from Android Bluetooth stack (GATT connected or bonded)
        val connectedLeDevices = runCatching { manager.getConnectedDevices(7) }.getOrDefault(emptyList())
        val bondedDevices = adapter.bondedDevices ?: emptySet()
        val bondedOrConnected = (connectedLeDevices + bondedDevices)
            .firstOrNull { it.address.equals(normalizedAddress, ignoreCase = true) }

        if (bondedOrConnected != null) {
            val isAclConnected = connectedLeDevices.any { it.address.equals(normalizedAddress, ignoreCase = true) }
            Timber.i(
                "Air one-shot: Registering direct GattConnection for %s (aclConnected=%b, bonded=%b)",
                normalizedAddress,
                isAclConnected,
                bondedOrConnected.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED
            )
            val wrappedDevice = FitbitBluetoothDevice(bondedOrConnected)
            val connection = GattConnection(wrappedDevice, Looper.getMainLooper())
            fitbitGatt.putConnectionIntoDevices(wrappedDevice, connection)
            if (!emitter.isDisposed) emitter.onSuccess(connection)
            return@create
        }

        // 4. Fallback to BLE advertisement scan with candidate auto-detection
        Timber.i("Air one-shot: Device not connected/bonded, starting BLE scan for %s", normalizedAddress)
        scanForDevice(normalizedAddress).subscribe(emitter::onSuccess, emitter::onError)
    }

    private fun scanForDevice(address: String): Single<GattConnection> = Single.create { emitter ->
        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            emitter.onError(IllegalStateException("Bluetooth is disabled"))
            return@create
        }
        val scanner = adapter.bluetoothLeScanner
            ?: run {
                emitter.onError(IllegalStateException("BLE scanner unavailable"))
                return@create
            }
        var callback: ScanCallback? = null
        var timeout: Runnable? = null
        val stopScan = {
            timeout?.let { mainHandler.removeCallbacks(it) }
            callback?.let { runCatching { scanner.stopScan(it) } }
        }
        var nearbyFitbitCandidate: ScanResult? = null

        val activeCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val devAddress = dev.address ?: return
                val rawName = dev.name ?: result.scanRecord?.deviceName.orEmpty()

                if (rawName.contains("Fitbit", ignoreCase = true) || rawName.contains("Air", ignoreCase = true)) {
                    nearbyFitbitCandidate = result
                }

                if (!devAddress.equals(address, ignoreCase = true)) return
                stopScan()
                if (!emitter.isDisposed) {
                    runCatching { registerScannedDevice(result) }
                        .onSuccess(emitter::onSuccess)
                        .onFailure(emitter::onError)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                stopScan()
                if (!emitter.isDisposed) emitter.onError(IllegalStateException("BLE scan failed: code $errorCode"))
            }
        }
        callback = activeCallback

        val activeTimeout = Runnable {
            stopScan()
            val candidate = nearbyFitbitCandidate
            if (candidate != null) {
                val candDev = candidate.device
                val candAddr = candDev.address
                val candName = candDev.name ?: candidate.scanRecord?.deviceName ?: "Google Fitbit Air"
                Timber.w("Target $address not advertising; auto-detected nearby Fitbit: $candName ($candAddr)!")
                AirBridgeDeviceSettings.saveAddress(this@AirBridgeOneShotService, candAddr, candName)
                if (!emitter.isDisposed) {
                    runCatching { registerScannedDevice(candidate) }
                        .onSuccess(emitter::onSuccess)
                        .onFailure(emitter::onError)
                }
            } else {
                // Direct fallback via getRemoteDevice before declaring failure
                val directDev = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
                if (directDev != null) {
                    Timber.i("Scan timed out; attempting direct connection for %s", address)
                    val fitbitGatt = FitbitGatt.getInstance()
                    val wrappedDevice = FitbitBluetoothDevice(directDev)
                    val connection = GattConnection(wrappedDevice, Looper.getMainLooper())
                    fitbitGatt.putConnectionIntoDevices(wrappedDevice, connection)
                    if (!emitter.isDisposed) emitter.onSuccess(connection)
                } else {
                    if (!emitter.isDisposed) {
                        emitter.onError(IllegalStateException("Fitbit Air not advertising (scan timeout)"))
                    }
                }
            }
        }
        timeout = activeTimeout

        emitter.setCancellable {
            stopScan()
        }

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, activeCallback)
            mainHandler.postDelayed(activeTimeout, SCAN_TIMEOUT_MILLIS)
        } catch (throwable: Throwable) {
            stopScan()
            if (!emitter.isDisposed) emitter.onError(throwable)
        }
    }

    private fun registerScannedDevice(result: ScanResult): GattConnection {
        val fitbitGatt = FitbitGatt.getInstance()
        BitGattHelper.removeConnectionForAddress(fitbitGatt, result.device.address)
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
        activeEndpoint = null
        val peer = activePeer
        activePeer = null
        if (peer != null) {
            runCatching { peer.disconnect(false) }
                .onFailure { Timber.w(it, "Failed to disconnect one-shot Air peer") }
        }
    }


    private fun updateStatus(status: String) {
        Timber.i("Air one-shot: %s", status)
        currentStatus = status
        AirBridgeDeviceSettings.saveLastStatus(this, status)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(status))

        val active = processing || isCallRinging || queue.isNotEmpty()
        isVibrating = active

        // Notify Compose UI
        try {
            sendBroadcast(Intent(ACTION_STATUS_UPDATED).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATUS_TEXT, status)
                putExtra(EXTRA_IS_ACTIVE, active)
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
        .setContentTitle("Air Bridge (Pure BLE)")
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
                    "Air Bridge BLE Service",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        const val ACTION_STATUS_UPDATED = "com.vibebridge.fitbit.STATUS_UPDATED"
        const val EXTRA_STATUS_TEXT = "status_text"
        const val EXTRA_IS_ACTIVE = "extra_is_active"
        const val ACTION_CANCEL_CALL = "com.vibebridge.fitbit.CANCEL_CALL"
        const val ACTION_STOP_ALL = "com.vibebridge.fitbit.STOP_ALL"
        const val ACTION_RESET_BLE = "com.vibebridge.fitbit.RESET_BLE"

        @Volatile var isVibrating: Boolean = false

        private const val EXTRA_SOURCE_PACKAGE = "source_package"
        private const val EXTRA_PATTERN = "pattern"
        private const val EXTRA_IS_CALL = "is_call"
        private const val EXTRA_IS_CONTINUOUS_CALL = "extra_is_continuous_call"

        private const val CHANNEL_ID = "air_bridge_ble_connection"
        private const val NOTIFICATION_ID = 24001
        private const val MAX_QUEUE_SIZE = 10
        private const val SCAN_TIMEOUT_MILLIS = 8_000L
        private const val CONNECT_TIMEOUT_SECONDS = 20L
        private const val RELEASE_SETTLE_MILLIS = 700L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 120_000L
        private const val MAX_CALL_RING_DURATION_MILLIS = 60_000L
        private const val MAX_CALL_BURSTS = 15
        private val RETRY_DELAYS_MILLIS = listOf(2_000L, 4_000L)

        fun enqueue(context: Context, trigger: AirNotificationTrigger): Boolean {
            if (AirBridgeDeviceSettings.loadAddress(context) == null) return false
            isVibrating = true
            val intent = Intent(context, AirBridgeOneShotService::class.java)
                .putExtra(EXTRA_SOURCE_PACKAGE, trigger.sourcePackage)
                .putExtra(EXTRA_PATTERN, trigger.pattern.wireName)
                .putExtra(EXTRA_IS_CALL, trigger.isCall)
                .putExtra(EXTRA_IS_CONTINUOUS_CALL, trigger.isContinuousCall)
            return runCatching {
                ContextCompat.startForegroundService(context, intent)
                true
            }.getOrElse {
                isVibrating = false
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

        fun stopAll(context: Context) {
            val intent = Intent(context, AirBridgeOneShotService::class.java).apply {
                action = ACTION_STOP_ALL
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun releaseBleNow(context: Context) {
            stopAll(context)
            val fitbitGatt = FitbitGatt.getInstance()
            runCatching {
                fitbitGatt.getMatchingConnectionsForDeviceNames(null).forEach { conn ->
                    runCatching { conn.close() }
                }
                BitGattHelper.clearConnections(fitbitGatt)
            }
        }

        fun resetConnection(context: Context) {
            val intent = Intent(context, AirBridgeOneShotService::class.java).apply {
                action = ACTION_RESET_BLE
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
            resetBleStack(context, "Connection reset • Ready")
        }

        fun resetBleStack(context: Context, statusMessage: String = "Connection reset • Ready") {
            stopAll(context)
            val fitbitGatt = FitbitGatt.getInstance()
            runCatching {
                fitbitGatt.getMatchingConnectionsForDeviceNames(null).forEach { conn ->
                    runCatching { conn.close() }
                }
                BitGattHelper.clearConnections(fitbitGatt)
            }
            DiagnosticTlsIdentityRecorder.clear()

            // Rebind NotificationListener if system unbound it
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    android.service.notification.NotificationListenerService.requestRebind(
                        android.content.ComponentName(context, AirNotificationListenerService::class.java)
                    )
                }
            }

            AirBridgeDeviceSettings.saveLastStatus(context, statusMessage)
            try {
                context.sendBroadcast(Intent(ACTION_STATUS_UPDATED).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_STATUS_TEXT, statusMessage)
                    putExtra(EXTRA_IS_ACTIVE, false)
                })
            } catch (ignored: Exception) {}
        }
    }
}
