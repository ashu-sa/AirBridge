package com.vibebridge.fitbit

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.vibebridge.fitbit.ble.AirBridgeDeviceSettings
import com.vibebridge.fitbit.ble.BleScanner
import com.vibebridge.fitbit.service.AirBridgeOneShotService
import com.vibebridge.fitbit.service.AirNotificationTrigger
import com.vibebridge.fitbit.ui.FitbitDashboardScreen
import com.vibebridge.fitbit.ui.theme.FitbitVibeTheme

class MainActivity : ComponentActivity() {

    private var hasPhonePerm by mutableStateOf(false)
    private var hasBluetoothPerm by mutableStateOf(false)
    private var hasNotificationListenerPerm by mutableStateOf(false)
    private var isBatteryOptimized by mutableStateOf(true)

    private var pairedAddress by mutableStateOf("")
    private var pairedName by mutableStateOf("")
    private var lastStatusText by mutableStateOf("")
    private var isCallAlertEnabled by mutableStateOf(true)
    private var callPattern by mutableStateOf(AirHapticPattern.URGENT)
    private var notificationRulesText by mutableStateOf("")

    private lateinit var bleScanner: BleScanner

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(AirBridgeOneShotService.EXTRA_STATUS_TEXT)
            if (!status.isNullOrBlank()) {
                lastStatusText = status
            } else {
                refreshState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bleScanner = BleScanner(applicationContext)

        refreshState()

        setContent {
            FitbitVibeTheme {
                FitbitDashboardScreen(
                    hasPhonePerm = hasPhonePerm,
                    hasBluetoothPerm = hasBluetoothPerm,
                    hasNotificationListenerPerm = hasNotificationListenerPerm,
                    isBatteryOptimized = isBatteryOptimized,
                    pairedDeviceName = pairedName,
                    pairedDeviceAddress = pairedAddress,
                    lastStatus = lastStatusText,
                    isCallAlertEnabled = isCallAlertEnabled,
                    callPattern = callPattern,
                    rulesText = notificationRulesText,
                    bleScanner = bleScanner,
                    onToggleCallAlert = { enabled ->
                        isCallAlertEnabled = enabled
                        AirBridgeDeviceSettings.setCallAlertEnabled(this, enabled)
                    },
                    onChangeCallPattern = { pattern ->
                        callPattern = pattern
                        AirBridgeDeviceSettings.setCallPattern(this, pattern)
                    },
                    onSelectDevice = { address, name ->
                        AirBridgeDeviceSettings.saveAddress(this, address, name)
                        pairedAddress = address
                        pairedName = name
                        Toast.makeText(this, "Paired with $name ($address)", Toast.LENGTH_SHORT).show()
                    },
                    onTestCallAlert = {
                        val trigger = AirNotificationTrigger("Manual Call Test", callPattern, isCall = true)
                        val ok = AirBridgeOneShotService.enqueue(this, trigger)
                        if (ok) {
                            Toast.makeText(this, "Pulsing band via BLE [${callPattern.displayName}]", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to start BLE service", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTestPattern = { pattern ->
                        val trigger = AirNotificationTrigger("Manual Pattern Test", pattern, isCall = false)
                        val ok = AirBridgeOneShotService.enqueue(this, trigger)
                        if (ok) {
                            Toast.makeText(this, "Pulsing band: ${pattern.displayName}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Failed to start BLE service", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSaveRulesText = { newText ->
                        val success = AirBridgeNotificationSettings.saveRulesText(this, newText)
                        if (success) {
                            notificationRulesText = AirBridgeNotificationSettings.loadText(this)
                        }
                        success
                    },
                    onRequestPhonePerm = {
                        val perms = mutableListOf(Manifest.permission.READ_PHONE_STATE)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            perms.add(Manifest.permission.READ_CALL_LOG)
                        }
                        requestPermissions(perms.toTypedArray(), REQ_PHONE_PERMS)
                    },
                    onRequestBluetoothPerm = {
                        val perms = mutableListOf<String>()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            perms.add(Manifest.permission.BLUETOOTH_SCAN)
                            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
                        }
                        perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
                        perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        requestPermissions(perms.toTypedArray(), REQ_BLE_PERMS)
                    },
                    onRequestIgnoreBatteryOptimizations = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            @SuppressLint("BatteryLife")
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            runCatching { startActivity(intent) }
                        }
                    },
                    onOpenNotificationListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(AirBridgeOneShotService.ACTION_STATUS_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(statusReceiver) }
        bleScanner.stopScan()
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshState()
    }

    private fun refreshState() {
        hasPhonePerm = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        hasBluetoothPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        hasNotificationListenerPerm = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isBatteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(packageName) != true
        } else {
            false
        }

        pairedAddress = AirBridgeDeviceSettings.loadAddress(this) ?: AirBridgeDeviceSettings.DEFAULT_FALLBACK_ADDRESS
        pairedName = AirBridgeDeviceSettings.loadDeviceName(this)
        lastStatusText = AirBridgeDeviceSettings.loadLastStatus(this)
        isCallAlertEnabled = AirBridgeDeviceSettings.isCallAlertEnabled(this)
        callPattern = AirBridgeDeviceSettings.loadCallPattern(this)
        notificationRulesText = AirBridgeNotificationSettings.loadText(this)
    }

    companion object {
        private const val REQ_PHONE_PERMS = 201
        private const val REQ_BLE_PERMS = 202
    }
}
