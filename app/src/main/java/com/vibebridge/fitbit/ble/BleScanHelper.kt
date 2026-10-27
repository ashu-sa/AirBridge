package com.vibebridge.fitbit.ble

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isFitbit: Boolean
)

class BleScanner(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val _devices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredBleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private var scanCallback: ScanCallback? = null
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val stopRunnable = Runnable { stopScan() }

    private fun hasScanPermission(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else {
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        _error.value = null
        _devices.value = emptyList()
        if (!hasScanPermission()) {
            _error.value = "Allow Nearby devices in Android settings to find your band."
            return
        }

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            _error.value = "Turn on Bluetooth, then scan again."
            return
        }

        val scanner = adapter.bluetoothLeScanner ?: run {
            _error.value = "Bluetooth scanner is unavailable. Try turning Bluetooth on again."
            return
        }
        _devices.value = emptyList()
        _isScanning.value = true

        val foundMap = mutableMapOf<String, DiscoveredBleDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val address = dev.address ?: return
                val rawName = result.scanRecord?.deviceName ?: runCatching { dev.name }.getOrNull()
                val name = if (!rawName.isNullOrBlank()) rawName else "BLE Device"
                val isFitbit = name.contains("Fitbit", ignoreCase = true)

                val item = DiscoveredBleDevice(
                    name = name,
                    address = address,
                    rssi = result.rssi,
                    isFitbit = isFitbit
                )

                foundMap[address] = item
                // Sort with Fitbit devices on top, then by RSSI signal strength
                _devices.value = foundMap.values.sortedWith(
                    compareByDescending<DiscoveredBleDevice> { it.isFitbit }
                        .thenByDescending { it.rssi }
                )
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("BLE scan failed with error code: %d", errorCode)
                _error.value = "Scan could not start (code $errorCode). Wait a moment, then retry."
                stopScan()
            }
        }

        scanCallback = callback
        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            scanner.startScan(null, settings, callback)

            // Auto stop after 10 seconds
            mainHandler.postDelayed(stopRunnable, 10_000L)
        } catch (t: Throwable) {
            Timber.e(t, "Exception starting BLE scan")
            _error.value = "Could not scan. Check Bluetooth and Nearby devices permission."
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        mainHandler.removeCallbacks(stopRunnable)
        if (!_isScanning.value) return
        runCatching {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val scanner = manager?.adapter?.bluetoothLeScanner
            scanCallback?.let { scanner?.stopScan(it) }
        }
        scanCallback = null
        _isScanning.value = false
    }
}
