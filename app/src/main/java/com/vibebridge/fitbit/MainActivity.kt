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
import android.os.Handler
import android.os.Looper
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
    private var isNotificationAlertEnabled by mutableStateOf(true)
    private var isVibratingActive by mutableStateOf(false)
    private var callPattern by mutableStateOf(AirHapticPattern.URGENT)
    private var notificationRules by mutableStateOf<List<AirNotificationRule>>(emptyList())

    private lateinit var bleScanner: BleScanner
    private val testAutoStopHandler = Handler(Looper.getMainLooper())

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(AirBridgeOneShotService.EXTRA_STATUS_TEXT)
            if (!status.isNullOrBlank()) {
                lastStatusText = status
            }
            val active = intent?.getBooleanExtra(AirBridgeOneShotService.EXTRA_IS_ACTIVE, AirBridgeOneShotService.isVibrating)
            isVibratingActive = active ?: AirBridgeOneShotService.isVibrating
            refreshState()
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
                    isNotificationAlertEnabled = isNotificationAlertEnabled,
                    isTestingVibration = isVibratingActive,
                    callPattern = callPattern,
                    rules = notificationRules,
                    bleScanner = bleScanner,
                    onToggleCallAlert = { enabled ->
                        isCallAlertEnabled = enabled
                        AirBridgeDeviceSettings.setCallAlertEnabled(this, enabled)
                    },
                    onToggleNotificationAlert = { enabled ->
                        isNotificationAlertEnabled = enabled
                        AirBridgeDeviceSettings.setNotificationAlertsEnabled(this, enabled)
                    },
                    onChangeCallPattern = { pattern ->
                        callPattern = pattern
                        AirBridgeDeviceSettings.setCallPattern(this, pattern)
                    },
                    onToggleAppRule = { index, enabled ->
                        val rule = notificationRules.getOrNull(index)
                        if (rule != null) {
                            notificationRules = AirBridgeNotificationSettings.setAppEnabled(this, rule.packageName, enabled)
                        }
                    },
                    onChangeAppPattern = { index, pattern ->
                        val rule = notificationRules.getOrNull(index)
                        if (rule != null) {
                            notificationRules = AirBridgeNotificationSettings.setAppPattern(this, rule.packageName, pattern)
                        }
                    },
                    onUpdateAppRule = { index, newPackage, pattern ->
                        val current = notificationRules.getOrNull(index)
                        val updated = AirNotificationRule(newPackage, pattern, isEnabled = current?.isEnabled ?: true)
                        notificationRules = AirBridgeNotificationSettings.updateRule(this, index, updated)
                    },
                    onResetDefaultRules = {
                        notificationRules = AirBridgeNotificationSettings.resetToDefaults(this)
                        Toast.makeText(this, "Reset to 5 default apps", Toast.LENGTH_SHORT).show()
                    },
                    onSelectDevice = { address, name ->
                        AirBridgeDeviceSettings.saveAddress(this, address, name)
                        pairedAddress = address
                        pairedName = name
                        Toast.makeText(this, "Paired with $name ($address)", Toast.LENGTH_SHORT).show()
                    },
                    onTestCallAlert = {
                        val trigger = AirNotificationTrigger("Manual Call Test", callPattern, isCall = true, isContinuousCall = false)
                        isVibratingActive = true
                        val ok = AirBridgeOneShotService.enqueue(this, trigger)
                        if (ok) {
                            Toast.makeText(this, "Pulsing band via BLE [${callPattern.displayName}]", Toast.LENGTH_SHORT).show()
                            testAutoStopHandler.removeCallbacksAndMessages(null)
                            testAutoStopHandler.postDelayed({
                                if (AirBridgeOneShotService.isVibrating) {
                                    AirBridgeOneShotService.stopAll(this@MainActivity)
                                    isVibratingActive = false
                                }
                            }, 14_000L)
                        } else {
                            isVibratingActive = false
                            Toast.makeText(this, "Failed to start BLE service", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTestPattern = { pattern ->
                        val trigger = AirNotificationTrigger("Manual Pattern Test", pattern, isCall = false, isContinuousCall = false)
                        isVibratingActive = true
                        val ok = AirBridgeOneShotService.enqueue(this, trigger)
                        if (ok) {
                            Toast.makeText(this, "Pulsing band: ${pattern.displayName}", Toast.LENGTH_SHORT).show()
                        } else {
                            isVibratingActive = false
                            Toast.makeText(this, "Failed to start BLE service", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStopVibration = {
                        testAutoStopHandler.removeCallbacksAndMessages(null)
                        AirBridgeOneShotService.stopAll(this)
                        isVibratingActive = false
                        Toast.makeText(this, "Vibration stopped", Toast.LENGTH_SHORT).show()
                    },
                    onRequestPhonePerm = { requestPhonePermissions() },
                    onRequestBluetoothPerm = { requestBluetoothPermissions() },
                    onRequestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() },
                    onOpenNotificationListenerSettings = { openNotificationListenerSettings() },
                    onForceSyncHealth = {
                        AirBridgeOneShotService.releaseBleNow(this)
                        val fitbitIntent = packageManager.getLaunchIntentForPackage("com.fitbit.FitbitMobile")
                        if (fitbitIntent != null) {
                            startActivity(fitbitIntent)
                            Toast.makeText(this, "BLE connection cleared! Opening Google Health...", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Bluetooth link released cleanly for background sync", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onResetConnection = {
                        AirBridgeOneShotService.resetConnection(this)
                        lastStatusText = "Connection reset • All caches cleared"
                        Toast.makeText(this, "BLE connection & caches reset clean", Toast.LENGTH_SHORT).show()
                    },
                    onAutoDetectBand = {
                        startAutoDetectBand()
                    },
                    onEnableBluetooth = {
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        runCatching { startActivity(enableBtIntent) }.onFailure {
                            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                        }
                    }
                )
            }
        }
    }

    private fun startAutoDetectBand() {
        AirBridgeOneShotService.resetConnection(this)
        Toast.makeText(this, "Scanning for nearby Fitbit band...", Toast.LENGTH_SHORT).show()
        lastStatusText = "Auto-detecting nearby Fitbit band..."
        bleScanner.startScan()

        val autoDetectHandler = Handler(Looper.getMainLooper())
        val checkRunnable = object : Runnable {
            var attempts = 0
            override fun run() {
                attempts++
                val foundFitbit = bleScanner.devices.value.firstOrNull { it.isFitbit }
                if (foundFitbit != null) {
                    bleScanner.stopScan()
                    AirBridgeDeviceSettings.saveAddress(this@MainActivity, foundFitbit.address, foundFitbit.name)
                    pairedAddress = foundFitbit.address
                    pairedName = foundFitbit.name
                    lastStatusText = "Auto-paired with ${foundFitbit.name} (${foundFitbit.address})"
                    AirBridgeDeviceSettings.saveLastStatus(this@MainActivity, lastStatusText)
                    Toast.makeText(this@MainActivity, "Connected to ${foundFitbit.name}!", Toast.LENGTH_SHORT).show()
                } else if (attempts < 14) {
                    autoDetectHandler.postDelayed(this, 500L)
                } else {
                    bleScanner.stopScan()
                    Toast.makeText(this@MainActivity, "No Fitbit found nearby. Tap band screen to wake it.", Toast.LENGTH_LONG).show()
                }
            }
        }
        autoDetectHandler.postDelayed(checkRunnable, 500L)
    }

    private fun requestPhonePermissions() {
        val perms = mutableListOf(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            perms.add(Manifest.permission.READ_CALL_LOG)
        }
        requestPermissions(perms.toTypedArray(), REQ_PHONE_PERMS)
    }

    private fun requestBluetoothPermissions() {
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
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @SuppressLint("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            runCatching { startActivity(intent) }
        }
    }

    private fun openNotificationListenerSettings() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
        isNotificationAlertEnabled = AirBridgeDeviceSettings.isNotificationAlertsEnabled(this)
        callPattern = AirBridgeDeviceSettings.loadCallPattern(this)
        notificationRules = AirBridgeNotificationSettings.loadRules(this)
        isVibratingActive = AirBridgeOneShotService.isVibrating
    }

    companion object {
        private const val REQ_PHONE_PERMS = 201
        private const val REQ_BLE_PERMS = 202
    }
}
