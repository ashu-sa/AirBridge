package com.vibebridge.fitbit.service

import android.Manifest
import android.content.pm.PackageManager
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
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fitbit.bluetooth.fbgatt.BitGattHelper
import com.fitbit.bluetooth.fbgatt.FitbitBluetoothDevice
import com.fitbit.bluetooth.fbgatt.FitbitGatt
import com.fitbit.bluetooth.fbgatt.GattConnection
import com.fitbit.bluetooth.fbgatt.rx.PeripheralConnectionChangeListener
import com.fitbit.goldengate.bindings.dtls.DtlsProtocolStatus
import com.fitbit.goldengate.bindings.coap.CoapEndpoint
import com.fitbit.goldengate.bindings.coap.CoapEndpointBuilder
import com.fitbit.goldengate.bindings.node.BluetoothAddressNodeKey
import com.fitbit.goldengate.bindings.stack.DtlsSocketNetifGattlink
import com.fitbit.goldengate.bt.DEFAULT_CLIENT_ADDRESS
import com.fitbit.goldengate.bt.DEFAULT_SERVER_ADDRESS
import com.fitbit.goldengate.bt.PeerRole
import com.fitbit.goldengate.node.stack.StackPeer
import com.vibebridge.fitbit.schedule.AlertScheduleStore
import com.vibebridge.fitbit.AirHapticPattern
import com.vibebridge.fitbit.MainActivity
import com.vibebridge.fitbit.ble.AirBridgeDeviceSettings
import com.vibebridge.fitbit.ble.AirBridgeRuntime
import com.vibebridge.fitbit.ble.AirDirectSettingsProtocol
import com.vibebridge.fitbit.ble.AirHapticExecutionResult
import com.vibebridge.fitbit.ble.NativePeerCleanup
import com.vibebridge.fitbit.ble.BleSessionDiagnostics
import com.vibebridge.fitbit.ble.PendingHapticRecovery
import com.vibebridge.fitbit.ble.HapticsRestoreException
import com.vibebridge.fitbit.ble.AirHapticPatternExecutor
import com.vibebridge.fitbit.ble.DiagnosticTlsIdentityRecorder
import io.reactivex.Single
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.net.Inet4Address
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.CancellationException

data class AirNotificationTrigger(
    val sourcePackage: String,
    val pattern: AirHapticPattern,
    val isCall: Boolean = false,
    val isContinuousCall: Boolean = false,
    val createdAtElapsed: Long = 0L,
    val isManual: Boolean = false,
    val appCallToken: Long? = null
)

/**
 * Pure Bluetooth Low Energy (BLE) background bridge for Fitbit Air:
 * scan -> connect -> Gattlink -> DTLS 1.2 -> CoAP POST /settings -> haptic pulse -> verify & disconnect.
 *
 * Uses Android's foreground-service notification for on-demand BLE work.
 */
class AirBridgeOneShotService : Service() {
    private val queue = ArrayDeque<AirNotificationTrigger>()
    private val disposables = CompositeDisposable()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var processing = false
    private var foregroundReady = false
    private var resetWhenIdle = false
    private var stopRequested = false
    private var callRepeatRunnable: Runnable? = null
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
        try {
            startForeground(NOTIFICATION_ID, buildNotification("Ready for on-demand BLE haptics"))
            foregroundReady = true
        } catch (error: Exception) {
            AirBridgeDeviceSettings.saveLastStatus(this, "Unable to start alerts. Check Nearby devices permission and reopen AirBridge.")
            Timber.w(error, "Unable to enter connected-device foreground mode")
            isVibrating = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foregroundReady) {
            stopSelf()
            return START_NOT_STICKY
        }
        val action = intent?.action
        if (action == ACTION_CANCEL_CALL) {
            if (!isCallRinging && queue.none { it.isCall }) {
                if (!processing && queue.isEmpty()) stopSelfIfQueueEmpty()
                return START_NOT_STICKY
            }
            isCallRinging = false
            callRepeatRunnable?.let { mainHandler.removeCallbacks(it) }
            callRepeatRunnable = null
            queue.removeAll { it.isCall }
            updateStatus(if (processing) "Call ended; finishing current alert" else "Call ended")
            if (!processing) processNext()
            return START_NOT_STICKY
        }

        if (action == ACTION_STOP_ALL || action == ACTION_RESET_BLE) {
            stopRequested = true
            isCallRinging = false
            callRepeatRunnable?.let { mainHandler.removeCallbacks(it) }
            callRepeatRunnable = null
            queue.clear()
            resetWhenIdle = resetWhenIdle || action == ACTION_RESET_BLE
            updateStatus(if (processing) "Stopping after current alert completes" else "Alerts stopped")
            if (!processing) processNext()
            return START_NOT_STICKY
        }

        val sourcePackage = intent?.getStringExtra(EXTRA_SOURCE_PACKAGE)
        val pattern = intent?.getStringExtra(EXTRA_PATTERN)?.let(AirHapticPattern::fromWireName)
        val isCall = intent?.getBooleanExtra(EXTRA_IS_CALL, false) ?: false
        val appCallToken = intent?.getLongExtra(EXTRA_APP_CALL_TOKEN, 0L)?.takeIf { it != 0L }
        val isManual = intent?.getBooleanExtra(EXTRA_IS_MANUAL, false) ?: false
        val isContinuousCall = intent?.getBooleanExtra(EXTRA_IS_CONTINUOUS_CALL, false) ?: false

        if (sourcePackage != null && pattern != null) {
            val blocked = AlertScheduleStore.blockReason(this)
            if (blocked != null) {
                updateStatus(blocked)
                if (!processing) processNext()
                return START_NOT_STICKY
            }
            if (isCall || appCallToken != null) {
                if (isContinuousCall) {
                    if (!isCallRinging) {
                        callRingStartTime = SystemClock.elapsedRealtime()
                        callRingBurstCount = 0
                    }
                    if (isCallRinging) return START_NOT_STICKY
                    isCallRinging = true
                }
                // Prioritize calls without allowing manual-test floods to grow the queue.
                while (queue.size >= MAX_QUEUE_SIZE) queue.removeLast()
                queue.addFirst(AirNotificationTrigger(sourcePackage, pattern, isCall = isCall,
                    isContinuousCall = isContinuousCall, createdAtElapsed = SystemClock.elapsedRealtime(), isManual = isManual, appCallToken = appCallToken))
            } else {
                queue.removeAll { !it.isCall && it.appCallToken == null && it.sourcePackage == sourcePackage }
                if (queue.size < MAX_QUEUE_SIZE) {
                    queue.addLast(AirNotificationTrigger(sourcePackage, pattern,
                        createdAtElapsed = SystemClock.elapsedRealtime(), isManual = isManual))
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
        mainHandler.removeCallbacksAndMessages(null)
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
        val blocked = AlertScheduleStore.blockReason(this)
        if (blocked != null) {
            queue.clear()
            isCallRinging = false
            callRepeatRunnable?.let { mainHandler.removeCallbacks(it) }
            callRepeatRunnable = null
            updateStatus(blocked)
            stopSelfIfQueueEmpty()
            return
        }
        queue.removeAll { !automaticFeatureEnabled(it) ||
            (!it.isCall && SystemClock.elapsedRealtime() - it.createdAtElapsed > MAX_ALERT_AGE_MILLIS) }
        val trigger = queue.pollFirst()
        if (trigger == null) {
            stopSelfIfQueueEmpty()
            return
        }
        val address = AirBridgeDeviceSettings.loadAddress(this)
        if (!trigger.isManual && address != null && PendingHapticRecovery.get(this, address) != null) {
            updateStatus("Previous alert needs vibration-setting recovery. Automatic alerts are paused.")
            queue.clear()
            isCallRinging = false
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
                                "${result.groupCount} pulse group(s), restored to " +
                                AirDirectSettingsProtocol.hapticsIntensityName(result.finalIntensity)
                        updateStatus(status)
                        processing = false

                        // If it's a call and the phone is still ringing, queue another pulse after a short breather
                        if (trigger.isCall && trigger.isContinuousCall && isCallRinging &&
                            AlertScheduleStore.blockReason(this) == null) {
                            callRingBurstCount++
                            val elapsed = SystemClock.elapsedRealtime() - callRingStartTime
                            if (elapsed > MAX_CALL_RING_DURATION_MILLIS || callRingBurstCount > MAX_CALL_BURSTS) {
                                Timber.i("Call ringing safety limit reached (${elapsed}ms, ${callRingBurstCount} bursts). Releasing haptics.")
                                isCallRinging = false
                                activeEndpoint = null
                                releasePeer()
                                processNext()
                            } else {
                                releaseWakeLock()
                                callRepeatRunnable = Runnable {
                                    callRepeatRunnable = null
                                    if (isCallRinging) queue.addFirst(trigger)
                                    processNext()
                                }
                                mainHandler.postDelayed(callRepeatRunnable!!, CALL_REPEAT_GAP_MILLIS)
                            }
                        } else {
                            processNext()
                        }
                    },
                    { throwable ->
                        val status = if (throwable is CancellationException) "Alert stopped"
                            else formatFriendlyError(trigger, throwable)
                        Timber.w(throwable, status)
                        updateStatus(status)
                        processing = false
                        activeEndpoint = null
                        releasePeer()
                        if (AirBridgeRuntime.isInitialized()) {
                            runCatching { BitGattHelper.clearConnections(FitbitGatt.getInstance()) }
                        }
                        processNext()
                    }
                )
        )
    }

    private fun formatFriendlyError(trigger: AirNotificationTrigger, throwable: Throwable): String {
        val rawMsg = throwable.message.orEmpty()
        val errType = throwable.javaClass.simpleName
        val hint = when {
            BleSessionDiagnostics.cleanupFailed ->
                "Bluetooth cleanup did not finish. Force stop AirBridge in Android app settings, then reopen it."
            rawMsg.contains("Encrypted session failed", ignoreCase = true) ->
                "Could not establish a secure session. Confirm band setup in the companion app, then retry."
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
            throwable is HapticsRestoreException ->
                "Band vibration setting could not be verified. Check it in the companion app before retrying."
            throwable is SecurityException ->
                "Bluetooth permission is missing. Allow Nearby devices in Android settings."
            else ->
                "Could not complete alert ($errType). Check the band and try again."
        }
        return "Error: ${trigger.sourcePackage} [${trigger.pattern.displayName}]: $hint"
    }

    private fun stopSelfIfQueueEmpty() {
        releasePeer()
        if (resetWhenIdle) {
            if (AirBridgeRuntime.isInitialized()) {
                runCatching { BitGattHelper.clearConnections(FitbitGatt.getInstance()) }
            }
            DiagnosticTlsIdentityRecorder.clear()
            resetWhenIdle = false
            updateStatus("Connection reset. Ready for the next alert.")
        }
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

    private fun automaticFeatureEnabled(trigger: AirNotificationTrigger): Boolean = trigger.isManual ||
        if (trigger.appCallToken != null) com.vibebridge.fitbit.calls.AppCallSettings.load(this).allows(trigger.sourcePackage)
        else if (trigger.isCall) AirBridgeDeviceSettings.isCallAlertEnabled(this)
        else AirBridgeDeviceSettings.isNotificationAlertsEnabled(this)

    private fun canBegin(trigger: AirNotificationTrigger): Boolean = !stopRequested &&
        !BleSessionDiagnostics.cleanupFailed && automaticFeatureEnabled(trigger) &&
        (!trigger.isContinuousCall || isCallRinging) && AlertScheduleStore.blockReason(this) == null &&
        (trigger.appCallToken == null || com.vibebridge.fitbit.calls.AppCallSessions.notifications.active(
            trigger.appCallToken, trigger.sourcePackage, SystemClock.elapsedRealtime()))

    private fun executeWithRetry(
        trigger: AirNotificationTrigger,
        attemptIndex: Int
    ): Single<AirHapticExecutionResult> = Single.defer {
        if (!canBegin(trigger) ||
            (!trigger.isCall && SystemClock.elapsedRealtime() - trigger.createdAtElapsed > MAX_ALERT_AGE_MILLIS)) {
            Single.error(CancellationException("Alert stopped before connection attempt"))
        } else {
            executeOnce(trigger)
        }
    }.onErrorResumeNext { throwable ->
        releasePeerAndWait().andThen(Single.defer {
            if (BleSessionDiagnostics.cleanupFailed || !AlertRetryPolicy.shouldRetry(attemptIndex, RETRY_DELAYS_MILLIS.size,
                    trigger.isContinuousCall, isCallRinging, stopRequested, throwable)) {
                Single.error(throwable)
            } else {
                val delay = RETRY_DELAYS_MILLIS[attemptIndex]
                updateStatus(
                    "Connection attempt failed. Retrying in ${delay / 1000}s..."
                )
                Single.timer(delay, TimeUnit.MILLISECONDS)
                    .flatMap { executeWithRetry(trigger, attemptIndex + 1) }
            }
        })
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


        return getEndpoint
            .flatMap { endpoint ->
                if (!canBegin(trigger)) return@flatMap Single.error<AirHapticExecutionResult>(CancellationException("Alert cancelled before settings write"))
                val pending = PendingHapticRecovery.get(this, address)
                val ready = if (pending == null) Single.just(Unit)
                    else if (trigger.isManual) {
                        updateStatus("Recovering the previous vibration setting before testing")
                        AirHapticPatternExecutor.recover(endpoint, pending) { PendingHapticRecovery.clear(this, address) }
                    } else Single.error(HapticsRestoreException("Previous vibration setting needs recovery"))
                ready.flatMap {
                    if (!canBegin(trigger)) return@flatMap Single.error<AirHapticExecutionResult>(CancellationException("Alert cancelled before settings write"))
                    updateStatus("Connected! Pulsing ${trigger.pattern.displayName}")
                    AirHapticPatternExecutor.execute(endpoint, trigger.pattern,
                        checkpoint = { PendingHapticRecovery.mark(this, address, it) },
                        verified = { PendingHapticRecovery.clear(this, address) })
                }.timeout(30, TimeUnit.SECONDS)
            }
            .flatMap { result ->
                // Yield the band after EVERY bounded pattern, including repeating calls.
                releasePeerAndWait().andThen(Single.timer(RELEASE_SETTLE_MILLIS, TimeUnit.MILLISECONDS).map { result })
            }
    }

    private fun resolveDeviceConnection(address: String): Single<GattConnection> = Single.create { emitter ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            emitter.onError(SecurityException("Nearby devices permission is required"))
            return@create
        }
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

        // 4. Scan for the explicitly selected address only.
        Timber.i("Air one-shot: Device not connected/bonded, starting BLE scan for %s", normalizedAddress)
        emitter.setDisposable(scanForDevice(normalizedAddress).subscribe(
            { if (!emitter.isDisposed) emitter.onSuccess(it) },
            { if (!emitter.isDisposed) emitter.onError(it) }
        ))
    }

    private fun scanForDevice(address: String): Single<GattConnection> = Single.create { emitter ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)) {
            emitter.onError(SecurityException("Nearby devices permission is required"))
            return@create
        }
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
            callback?.let {
                try { scanner.stopScan(it) } catch (_: SecurityException) { /* Permission revoked during scan. */ }
            }
        }

        val activeCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val devAddress = dev.address ?: return
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
            { stack ->
                stack.dtlsEventObservable.observeOn(Schedulers.io()).doOnNext { status ->
                    check(status.state != DtlsProtocolStatus.TlsProtocolState.TLS_STATE_ERROR) {
                        "Encrypted session failed (code ${status.lastError}). Confirm band setup in the companion app."
                    }
                }
            }
        )
        activePeer = peer
        BleSessionDiagnostics.onStarted()
        return peer.connection()
            .observeOn(Schedulers.io())
            .firstOrError()
            .timeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .map { peer.stackService }
    }

    private fun releasePeerAndWait(): Completable {
        val peer = synchronized(this) {
            activeEndpoint = null
            val detached = activePeer
            activePeer = null
            detached
        } ?: return Completable.complete()
        // Native stack disposal must never execute inside its own DTLS/CoAP callback.
        return NativePeerCleanup.close {
            peer.disconnect(false)
            BleSessionDiagnostics.onReleased()
        }
            .doOnError { BleSessionDiagnostics.cleanupFailed = true }
    }

    private fun releasePeer() {
        releasePeerAndWait().subscribe({}, { error ->
            Timber.w(error, "BLE cleanup could not complete")
        })
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
        .setContentTitle("AirBridge")
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
        private const val EXTRA_APP_CALL_TOKEN = "app_call_token"
        private const val EXTRA_IS_CALL = "is_call"
        private const val EXTRA_IS_MANUAL = "is_manual"
        private const val EXTRA_IS_CONTINUOUS_CALL = "extra_is_continuous_call"

        private const val CHANNEL_ID = "air_bridge_ble_connection"
        private const val NOTIFICATION_ID = 24001
        private const val MAX_QUEUE_SIZE = 3
        private const val MAX_ALERT_AGE_MILLIS = 30_000L
        private const val CALL_REPEAT_GAP_MILLIS = 5_000L
        private const val SCAN_TIMEOUT_MILLIS = 8_000L
        private const val CONNECT_TIMEOUT_SECONDS = 20L
        private const val RELEASE_SETTLE_MILLIS = 2_000L
        private const val WAKE_LOCK_TIMEOUT_MILLIS = 120_000L
        private const val MAX_CALL_RING_DURATION_MILLIS = 60_000L
        private const val MAX_CALL_BURSTS = 15
        private val RETRY_DELAYS_MILLIS = listOf(2_000L, 4_000L)

        fun enqueue(context: Context, trigger: AirNotificationTrigger): Boolean {
            if (BleSessionDiagnostics.cleanupFailed) {
                AirBridgeDeviceSettings.saveLastStatus(context, "Bluetooth cleanup did not finish. Force stop AirBridge in Android app settings, then reopen it before trying again.")
                return false
            }
            val blocked = AlertScheduleStore.blockReason(context)
            if (blocked != null) {
                AirBridgeDeviceSettings.saveLastStatus(context, blocked)
                return false
            }
            val hasPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            } else context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!hasPermissions) {
                AirBridgeDeviceSettings.saveLastStatus(context, "Nearby devices permission is required before starting an alert.")
                return false
            }
            val bluetoothEnabled = try {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled == true
            } catch (_: SecurityException) { false }
            if (!bluetoothEnabled) {
                AirBridgeDeviceSettings.saveLastStatus(context, "Bluetooth is turned off. Turn it on before testing alerts.")
                return false
            }
            if (AirBridgeDeviceSettings.loadAddress(context) == null) {
                AirBridgeDeviceSettings.saveLastStatus(context, "Choose a band before testing alerts.")
                return false
            }
            val address = AirBridgeDeviceSettings.loadAddress(context)!!
            if (!trigger.isManual && PendingHapticRecovery.get(context, address) != null) {
                AirBridgeDeviceSettings.saveLastStatus(context, "Previous alert was interrupted. Use Recover vibration setting before enabling more alerts.")
                return false
            }
            isVibrating = true
            val intent = Intent(context, AirBridgeOneShotService::class.java)
                .putExtra(EXTRA_SOURCE_PACKAGE, trigger.sourcePackage)
                .putExtra(EXTRA_PATTERN, trigger.pattern.wireName)
                .putExtra(EXTRA_APP_CALL_TOKEN, trigger.appCallToken ?: 0L)
                .putExtra(EXTRA_IS_CALL, trigger.isCall)
                .putExtra(EXTRA_IS_MANUAL, trigger.isManual)
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
            if (!isVibrating) return
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
            if (isVibrating) stopAll(context)
        }

        fun resetConnection(context: Context) {
            val intent = Intent(context, AirBridgeOneShotService::class.java).apply {
                action = ACTION_RESET_BLE
            }
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure {
                    AirBridgeDeviceSettings.saveLastStatus(context, "Unable to reset connection. Open the app and try again.")
                }
        }

    }
}
