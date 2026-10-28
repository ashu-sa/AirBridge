package com.vibebridge.fitbit.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A bounded, user-initiated scan. All visible devices are listed; no Fitbit-only filter. */
class BleScanner(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val _devices = MutableStateFlow<List<DiscoveredBleDevice>>(emptyList())
    val devices = _devices.asStateFlow()
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    private val found = linkedMapOf<String, DiscoveredBleDevice>()
    private var callback: ScanCallback? = null
    private var scanner: BluetoothLeScanner? = null
    private var adapter: BluetoothAdapter? = null
    private var receiverRegistered = false
    private var classicStarted = false
    private var generation = 0
    private val stop = Runnable { stopScan() }
    private val classic = Runnable { startClassic() }
    @SuppressLint("MissingPermission")
    private fun startClassic() {
        if (_isScanning.value && permission()) runCatching {
            if (adapter?.isDiscovering == false) classicStarted = adapter?.startDiscovery() == true
        }
    }
    private fun permission() = if (Build.VERSION.SDK_INT >= 31) {
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    } else context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun add(item: DiscoveredBleDevice) {
        found[item.address] = BandDiscoveryPolicy.merge(found[item.address], item)
        _devices.value = found.values.sortedWith(compareByDescending<DiscoveredBleDevice> { BandDiscoveryPolicy.isAir(it.name) }
            .thenByDescending { it.isFitbit }.thenByDescending { it.bleSeen }.thenByDescending { it.rssi })
    }
    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        @Suppress("DEPRECATION")
        override fun onReceive(c: Context?, intent: Intent?) {
            if (!_isScanning.value || !permission() || intent?.action != BluetoothDevice.ACTION_FOUND) return
            val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            device ?: return
            val name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: runCatching { device.name }.getOrNull() ?: "Unnamed Bluetooth device"
            add(DiscoveredBleDevice(name, device.address, intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(),
                name.contains("fitbit", true), bleSeen = false, connectable = device.type != BluetoothDevice.DEVICE_TYPE_CLASSIC,
                paired = device.bondState == BluetoothDevice.BOND_BONDED, source = "Nearby classic Bluetooth"))
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return
        _error.value = null
        found.clear(); _devices.value = emptyList()
        if (!permission()) { _error.value = "Allow Nearby devices (or Location on Android 11 and earlier), then retry."; return }
        adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        if (adapter == null) { _error.value = "This device does not provide a Bluetooth adapter."; return }
        if (runCatching { adapter?.isEnabled }.getOrNull() != true) { _error.value = "Bluetooth is off. Turn it on, then retry."; return }
        runCatching { adapter?.bondedDevices.orEmpty().forEach { dev ->
            val name = dev.name ?: "Unnamed Bluetooth device"
            add(DiscoveredBleDevice(name, dev.address, Int.MIN_VALUE, name.contains("fitbit", true), bleSeen = false,
                connectable = dev.type != BluetoothDevice.DEVICE_TYPE_CLASSIC, paired = true, source = "Paired • range unknown"))
        } }
        // A band already connected to its companion app may stop advertising.
        runCatching {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.getConnectedDevices(android.bluetooth.BluetoothProfile.GATT).orEmpty().forEach { dev ->
                val name = dev.name ?: "Unnamed Bluetooth device"
                add(DiscoveredBleDevice(name, dev.address, Int.MIN_VALUE, name.contains("fitbit", true),
                    bleSeen = true, connectable = true, paired = dev.bondState == BluetoothDevice.BOND_BONDED,
                    source = "Connected Bluetooth LE"))
            }
        }
        if (Build.VERSION.SDK_INT <= 30) {
            val location = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val enabled = runCatching { if (Build.VERSION.SDK_INT >= 28) location?.isLocationEnabled == true
                else location?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true || location?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true }.getOrDefault(false)
            if (!enabled) { _error.value = "Turn on Location services to discover nearby devices on Android 11 and earlier."; return }
        }
        scanner = runCatching { adapter?.bluetoothLeScanner }.getOrNull()
        if (scanner == null) { _error.value = "Bluetooth LE scanning is unavailable. Try Bluetooth again."; return }
        val scanGeneration = ++generation
        _isScanning.value = true
        callback = object : ScanCallback() {
            override fun onScanResult(type: Int, result: ScanResult) { main.post {
                if (!_isScanning.value || generation != scanGeneration) return@post
                if (!permission()) { _error.value = "Bluetooth permission changed. Allow Nearby devices and rescan."; stopScan(); return@post }
                val dev = result.device ?: return@post
                val name = result.scanRecord?.deviceName?.takeIf { it.isNotBlank() }
                    ?: runCatching { dev.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Unnamed Bluetooth device"
                add(DiscoveredBleDevice(name, dev.address, result.rssi, name.contains("fitbit", true),
                    connectable = result.isConnectable, paired = runCatching { dev.bondState == BluetoothDevice.BOND_BONDED }.getOrDefault(false)))
            } }
            override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }
            override fun onScanFailed(code: Int) { main.post {
                if (generation != scanGeneration) return@post
                _error.value = "BLE scan failed (code $code). Wait a moment and retry."
                stopScan()
            } }
        }
        try {
            // Bluetooth broadcasts can originate from the privileged Bluetooth process, not the system UID.
            ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_FOUND), ContextCompat.RECEIVER_EXPORTED)
            receiverRegistered = true
            scanner!!.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            // Give LE band detection a head start before classic discovery uses the radio.
            main.postDelayed(classic, 5_000)
            main.postDelayed(stop, 20_000)
        } catch (_: Exception) { _error.value = "Could not scan. Check Bluetooth and Nearby devices permission."; stopScan() }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        ++generation
        main.removeCallbacks(stop); main.removeCallbacks(classic)
        callback?.let { cb -> runCatching { scanner?.stopScan(cb) } }
        callback = null; scanner = null
        if (classicStarted) runCatching { adapter?.cancelDiscovery() }
        classicStarted = false
        if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
        receiverRegistered = false
        _isScanning.value = false
    }
}
