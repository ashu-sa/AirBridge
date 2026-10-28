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
import androidx.core.content.ContextCompat
import com.vibebridge.fitbit.ble.PendingHapticRecovery
import com.vibebridge.fitbit.ble.AirBridgeDeviceSettings
import com.vibebridge.fitbit.ble.BleScanner
import com.vibebridge.fitbit.service.AirBridgeOneShotService
import com.vibebridge.fitbit.service.AirNotificationTrigger
import com.vibebridge.fitbit.schedule.AlertSchedule
import com.vibebridge.fitbit.schedule.AlertScheduleStore
import com.vibebridge.fitbit.ui.FitbitDashboardScreen
import com.vibebridge.fitbit.ui.theme.FitbitVibeTheme

class MainActivity : ComponentActivity() {

    private var appCallConfig by mutableStateOf(com.vibebridge.fitbit.calls.AppCallConfig())
    private var callDetectionStatus by mutableStateOf("")
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

    private var alertSchedule by mutableStateOf(AlertSchedule())
    private var syncPaused by mutableStateOf(false)
    private var needsHapticRecovery by mutableStateOf(false)

    private lateinit var bleScanner: BleScanner

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
                    callDetectionStatus = callDetectionStatus,
                    appCallConfig = appCallConfig,
                    onChangeAppCalls = { updated ->
                        com.vibebridge.fitbit.calls.AppCallSettings.save(this, updated)
                        appCallConfig = updated
                    },
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
                    alertSchedule = alertSchedule,
                    syncPaused = syncPaused,
                    needsHapticRecovery = needsHapticRecovery,
                    onChangeSchedule = { updated ->
                        AlertScheduleStore.save(this, updated)
                        alertSchedule = updated
                        if (!AlertScheduleStore.allowsNow(this) && AirBridgeOneShotService.isVibrating) {
                            AirBridgeOneShotService.stopAll(this)
                        }
                    },
                    onResumeAlerts = {
                        AlertScheduleStore.clearSyncPause(this)
                        syncPaused = false
                        Toast.makeText(this, "Sync pause ended", Toast.LENGTH_SHORT).show()
                    },
                    onToggleCallAlert = { enabled ->
                        isCallAlertEnabled = enabled
                        AirBridgeDeviceSettings.setCallAlertEnabled(this, enabled)
                        com.vibebridge.fitbit.calls.PhoneCallMonitor.refresh(this)
                        if (!enabled) AirBridgeOneShotService.cancelCall(this)
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
                    onSelectDevice = selectBand@{ address, name ->
                        if (AirBridgeOneShotService.isVibrating) {
                            Toast.makeText(this, "Wait for the current alert or connection check to finish", Toast.LENGTH_SHORT).show()
                            return@selectBand
                        }
                        if (!BluetoothAdapter.checkBluetoothAddress(address)) {
                            Toast.makeText(this, "Enter a valid Bluetooth address", Toast.LENGTH_SHORT).show()
                        } else {
                            AirBridgeDeviceSettings.saveAddress(this, address, name)
                            pairedAddress = address
                            pairedName = name
                            val started = AirBridgeOneShotService.enqueue(this, AirNotificationTrigger(
                                "Connection check", AirHapticPattern.SINGLE, isManual = true, isConnectionCheck = true))
                            refreshState()
                            Toast.makeText(this, if (started) "Selected $name. Checking the connection…" else "Band saved. See Latest activity for connection status.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTestCallAlert = {
                        val trigger = AirNotificationTrigger("Manual Call Test", callPattern, isCall = true, isContinuousCall = false, isManual = true)
                        isVibratingActive = true
                        val ok = AirBridgeOneShotService.enqueue(this, trigger)
                        if (ok) {
                            Toast.makeText(this, "Starting test: ${callPattern.displayName}", Toast.LENGTH_SHORT).show()
                        } else {
                            isVibratingActive = false
                            Toast.makeText(this, AirBridgeDeviceSettings.loadLastStatus(this), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onTestPattern = { pattern ->
                        val trigger = AirNotificationTrigger("Manual Pattern Test", pattern, isCall = false, isContinuousCall = false, isManual = true)
                        isVibratingActive = true
                        val ok = AirBridgeOneShotService.enqueue(this, trigger)
                        if (ok) {
                            Toast.makeText(this, "Starting test: ${pattern.displayName}", Toast.LENGTH_SHORT).show()
                        } else {
                            isVibratingActive = false
                            Toast.makeText(this, AirBridgeDeviceSettings.loadLastStatus(this), Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStopVibration = {
                        AirBridgeOneShotService.stopAll(this)
                        Toast.makeText(this, "Finishing the current alert before stopping", Toast.LENGTH_SHORT).show()
                    },
                    onRequestPhonePerm = { requestPhonePermissions() },
                    onRequestBluetoothPerm = { requestBluetoothPermissions() },
                    onRequestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() },
                    onOpenNotificationListenerSettings = { openNotificationListenerSettings() },
                    onForceSyncHealth = {
                        val fitbitIntent = packageManager.getLaunchIntentForPackage("com.fitbit.FitbitMobile")
                        if (fitbitIntent != null) {
                            AlertScheduleStore.pauseForSync(this)
                            syncPaused = true
                            AirBridgeOneShotService.releaseBleNow(this)
                            startActivity(fitbitIntent)
                            Toast.makeText(this, "Alerts paused for 5 minutes. Pull down in the companion app to sync.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Companion app not found. You can open it manually.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onResetConnection = {
                        AirBridgeOneShotService.resetConnection(this)
                        Toast.makeText(this, "Reset requested. Any current alert will finish first.", Toast.LENGTH_SHORT).show()
                    },
                    onEnableBluetooth = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            requestBluetoothPermissions()
                        } else {
                            startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        }
                    }
                )
            }
        }
    }

    private fun requestPhonePermissions() {
        requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), REQ_PHONE_PERMS)
    }

    private fun requestBluetoothPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            perms.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
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
        if (BuildConfig.NOTIFICATION_FEATURES) startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(AirBridgeOneShotService.ACTION_STATUS_UPDATED)
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
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
        com.vibebridge.fitbit.calls.PhoneCallMonitor.refresh(this)
        callDetectionStatus = com.vibebridge.fitbit.calls.CallEventHandler.lastDetectedLabel(this)
        appCallConfig = if (BuildConfig.NOTIFICATION_FEATURES) com.vibebridge.fitbit.calls.AppCallSettings.load(this) else com.vibebridge.fitbit.calls.AppCallConfig()
        hasPhonePerm = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        hasBluetoothPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        hasNotificationListenerPerm = BuildConfig.NOTIFICATION_FEATURES && NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        isBatteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(packageName) != true
        } else {
            false
        }

        pairedAddress = AirBridgeDeviceSettings.loadAddress(this).orEmpty()
        pairedName = AirBridgeDeviceSettings.loadDeviceName(this)
        needsHapticRecovery = pairedAddress.isNotBlank() && PendingHapticRecovery.get(this, pairedAddress) != null
        lastStatusText = AirBridgeDeviceSettings.loadLastStatus(this)
        isCallAlertEnabled = AirBridgeDeviceSettings.isCallAlertEnabled(this)
        isNotificationAlertEnabled = AirBridgeDeviceSettings.isNotificationAlertsEnabled(this)
        callPattern = AirBridgeDeviceSettings.loadCallPattern(this)
        notificationRules = if (BuildConfig.NOTIFICATION_FEATURES) AirBridgeNotificationSettings.loadRules(this) else emptyList()
        alertSchedule = AlertScheduleStore.load(this)
        syncPaused = AlertScheduleStore.isSyncPaused(this)
        isVibratingActive = AirBridgeOneShotService.isVibrating
    }

    companion object {
        private const val REQ_PHONE_PERMS = 201
        private const val REQ_BLE_PERMS = 202
    }
}
