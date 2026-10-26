package com.vibebridge.fitbit.ble

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

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            Timber.w("Bluetooth is disabled or adapter is null")
            return
        }

        val scanner = adapter.bluetoothLeScanner ?: return
        _devices.value = emptyList()
        _isScanning.value = true

        val foundMap = mutableMapOf<String, DiscoveredBleDevice>()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device ?: return
                val address = dev.address ?: return
                val rawName = dev.name ?: result.scanRecord?.deviceName
                val name = if (!rawName.isNullOrBlank()) rawName else "BLE Device"
                val isFitbit = name.contains("Fitbit", ignoreCase = true) ||
                        name.contains("Air", ignoreCase = true)

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
            mainHandler.postDelayed({
                stopScan()
            }, 10_000L)
        } catch (t: Throwable) {
            Timber.e(t, "Exception starting BLE scan")
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val scanner = manager?.adapter?.bluetoothLeScanner
        scanCallback?.let {
            runCatching { scanner?.stopScan(it) }
        }
        scanCallback = null
        _isScanning.value = false
    }
}
