package com.vibebridge.fitbit.ui

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.vibebridge.fitbit.schedule.AlertSchedule
import com.vibebridge.fitbit.AirBridgeNotificationSettings
import com.vibebridge.fitbit.AirHapticPattern
import com.vibebridge.fitbit.AirNotificationRule
import com.vibebridge.fitbit.ble.BleScanner
import com.vibebridge.fitbit.ble.DiscoveredBleDevice
import com.vibebridge.fitbit.ui.theme.*
import com.vibebridge.fitbit.util.DeviceApp
import com.vibebridge.fitbit.util.InstalledAppsManager

@Composable
fun FitbitDashboardScreen(
    hasPhonePerm: Boolean,
    callDetectionStatus: String,
    appCallConfig: com.vibebridge.fitbit.calls.AppCallConfig,
    onChangeAppCalls: (com.vibebridge.fitbit.calls.AppCallConfig) -> Unit,
    hasBluetoothPerm: Boolean,
    hasNotificationListenerPerm: Boolean,
    isBatteryOptimized: Boolean,
    pairedDeviceName: String,
    pairedDeviceAddress: String,
    lastStatus: String,
    isCallAlertEnabled: Boolean,
    isNotificationAlertEnabled: Boolean,
    isTestingVibration: Boolean,
    callPattern: AirHapticPattern,
    rules: List<AirNotificationRule>,
    bleScanner: BleScanner,
    alertSchedule: AlertSchedule,
    syncPaused: Boolean,
    needsHapticRecovery: Boolean,
    onChangeSchedule: (AlertSchedule) -> Unit,
    onResumeAlerts: () -> Unit,
    onToggleCallAlert: (Boolean) -> Unit,
    onToggleNotificationAlert: (Boolean) -> Unit,
    onChangeCallPattern: (AirHapticPattern) -> Unit,
    onToggleAppRule: (Int, Boolean) -> Unit,
    onChangeAppPattern: (Int, AirHapticPattern) -> Unit,
    onUpdateAppRule: (Int, String, AirHapticPattern) -> Unit,
    onResetDefaultRules: () -> Unit,
    onSelectDevice: (String, String) -> Unit,
    onTestCallAlert: () -> Unit,
    onTestPattern: (AirHapticPattern) -> Unit,
    onStopVibration: () -> Unit = {},
    onRequestPhonePerm: () -> Unit,
    onRequestBluetoothPerm: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit,
    onForceSyncHealth: () -> Unit = {},
    onResetConnection: () -> Unit = {},
    onAutoDetectBand: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showScannerDialog by remember { mutableStateOf(false) }
    var showManualAddressDialog by remember { mutableStateOf(false) }
    var manualAddressInput by remember { mutableStateOf(pairedDeviceAddress) }
    var manualNameInput by remember { mutableStateOf(pairedDeviceName) }

    var slotToEdit by remember { mutableStateOf<Int?>(null) }
    var slotForPatternSelection by remember { mutableStateOf<Int?>(null) }
    var activeTestingAppPackage by remember { mutableStateOf<String?>(null) }
    var activeTestingPattern by remember { mutableStateOf<AirHapticPattern?>(null) }

    LaunchedEffect(isTestingVibration) {
        if (!isTestingVibration) {
            activeTestingAppPackage = null
            activeTestingPattern = null
        }
    }

    val isArmed = hasBluetoothPerm && pairedDeviceAddress.isNotBlank()
    val openScanner = {
        if (hasBluetoothPerm) {
            showScannerDialog = true
            bleScanner.startScan()
        } else {
            onRequestBluetoothPerm()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {

        // ==========================================
        // 1. MINIMALIST TOP HEADER & STATUS
        // ==========================================
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = CyberTeal.copy(alpha = 0.12f)) {
                Icon(Icons.Rounded.Waves, contentDescription = null, tint = CyberTeal,
                    modifier = Modifier.padding(12.dp).size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("AirBridge", color = TextWhite, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("Your alerts, a little closer.", color = TextMuted, fontSize = 13.sp)
            }
        }

        ModernHeaderCard(
            isArmed = isArmed,
            deviceName = pairedDeviceName,
            deviceAddress = pairedDeviceAddress,
            onOpenScanner = openScanner,
            onTestBand = { onTestPattern(AirHapticPattern.SINGLE) },
            isTesting = isTestingVibration,
            needsHapticRecovery = needsHapticRecovery,
            onOpenManual = {
                manualAddressInput = pairedDeviceAddress
                manualNameInput = pairedDeviceName
                showManualAddressDialog = true
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (syncPaused) {
            Surface(color = SurfaceCardElevated, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Paused for companion-app sync", color = CyberTeal, fontWeight = FontWeight.SemiBold)
                    Text("Automatic alerts resume after 5 minutes, within your schedule.", color = TextMuted, fontSize = 12.sp)
                    TextButton(onClick = onResumeAlerts) { Text("Resume alerts now") }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Coexistence sync status & error recovery banner
        CoexistenceSyncBanner(
            lastStatus = lastStatus,
            onForceSyncHealth = onForceSyncHealth,
            onResetConnection = onResetConnection,
            onAutoDetectBand = openScanner,
            onEnableBluetooth = onEnableBluetooth
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ==========================================
        // 2. INCOMING CALL NOTIFICATIONS
        // ==========================================
        ModernCallAlertCard(
            isEnabled = isCallAlertEnabled,
            selectedPattern = callPattern,
            isTesting = isTestingVibration && activeTestingAppPackage == null,
            hasPhonePerm = hasPhonePerm,
            callDetectionStatus = callDetectionStatus,
            onToggle = onToggleCallAlert,
            onSelectPattern = onChangeCallPattern,
            onRequestPerm = onRequestPhonePerm,
            onToggleTest = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isTestingVibration) {
                    activeTestingAppPackage = null
                    activeTestingPattern = null
                    onStopVibration()
                } else {
                    activeTestingAppPackage = null
                    activeTestingPattern = callPattern
                    onTestCallAlert()
                }
            }
        )

        Spacer(modifier = Modifier.height(18.dp))

        AppCallCard(appCallConfig, onChangeAppCalls, hasNotificationListenerPerm, onOpenNotificationListenerSettings)
        Spacer(modifier = Modifier.height(18.dp))

        // ==========================================
        // 3. PRIORITY APPS (MAX 5) - ALLOWED ON TOP
        // ==========================================
        ModernPriorityAppsCard(
            isEnabled = isNotificationAlertEnabled,
            hasListenerPerm = hasNotificationListenerPerm,
            rules = rules,
            isTestingVibration = isTestingVibration,
            activeTestingAppPackage = activeTestingAppPackage,
            onToggleMaster = onToggleNotificationAlert,
            onToggleRule = onToggleAppRule,
            onOpenPattern = { slotIndex -> slotForPatternSelection = slotIndex },
            onPickApp = { slotIndex -> slotToEdit = slotIndex },
            onTestAppPattern = { rule ->
                activeTestingAppPackage = rule.packageName
                activeTestingPattern = rule.pattern
                onTestPattern(rule.pattern)
            },
            onStopVibration = {
                activeTestingAppPackage = null
                activeTestingPattern = null
                onStopVibration()
            },
            onResetDefaults = onResetDefaultRules,
            onOpenListenerSettings = onOpenNotificationListenerSettings
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ==========================================
        // 4. BETTER SYSTEM PERMISSIONS & READINESS
        // ==========================================
        ModernPermissionsCard(
            hasPhonePerm = hasPhonePerm,
            hasBluetoothPerm = hasBluetoothPerm,
            hasListenerPerm = hasNotificationListenerPerm,
            isBatteryOptimized = isBatteryOptimized,
            onRequestPhonePerm = onRequestPhonePerm,
            onRequestBluetoothPerm = onRequestBluetoothPerm,
            onRequestBatteryOptimization = onRequestIgnoreBatteryOptimizations,
            onOpenListenerSettings = onOpenNotificationListenerSettings
        )

        Spacer(modifier = Modifier.height(18.dp))

        // ==========================================
        // 5. PRIVACY & HOW IT WORKS (INLINE ACCORDION)
        // ==========================================
        AlertScheduleCard(alertSchedule, onChangeSchedule)
        Spacer(modifier = Modifier.height(18.dp))
        ModernPrivacyAccordionCard()
        Text("Independent project • Not affiliated with Google or Fitbit",
            color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp,
            modifier = Modifier.padding(top = 20.dp, start = 4.dp, end = 4.dp))

        Spacer(modifier = Modifier.height(28.dp))
    }

    // App Picker Dialog (Lists all installed apps on phone with real icons!)
    slotToEdit?.let { slotIndex ->
        val currentRule = rules.getOrNull(slotIndex)
        AppPickerDialog(
            slotNumber = slotIndex + 1,
            currentPackage = currentRule?.packageName.orEmpty(),
            onDismiss = { slotToEdit = null },
            onSelectApp = { selectedApp ->
                val pattern = currentRule?.pattern ?: AirHapticPattern.SINGLE
                onUpdateAppRule(slotIndex, selectedApp.packageName, pattern)
                slotToEdit = null
            }
        )
    }

    // Pattern Selection Dialog
    slotForPatternSelection?.let { slotIndex ->
        val rule = rules.getOrNull(slotIndex)
        if (rule != null) {
            val appName = InstalledAppsManager.getAppName(context, rule.packageName)
            SelectAppPatternDialog(
                appName = appName,
                packageName = rule.packageName,
                currentPattern = rule.pattern,
                isTestingVibration = isTestingVibration,
                activeTestingPattern = activeTestingPattern,
                onDismiss = {
                    if (isTestingVibration) {
                        activeTestingAppPackage = null
                        activeTestingPattern = null
                        onStopVibration()
                    }
                    slotForPatternSelection = null
                },
                onSelect = { pattern ->
                    onChangeAppPattern(slotIndex, pattern)
                },
                onTestPattern = { pattern ->
                    activeTestingAppPackage = rule.packageName
                    activeTestingPattern = pattern
                    onTestPattern(pattern)
                },
                onStopVibration = {
                    activeTestingAppPackage = null
                    activeTestingPattern = null
                    onStopVibration()
                }
            )
        }
    }

    // BLE Scanner Dialog
    if (showScannerDialog) {
        BleScannerDialog(
            bleScanner = bleScanner,
            currentPairedAddress = pairedDeviceAddress,
            onDismiss = {
                bleScanner.stopScan()
                showScannerDialog = false
            },
            onSelect = { device ->
                onSelectDevice(device.address, device.name)
                bleScanner.stopScan()
                showScannerDialog = false
            }
        )
    }

    // Manual MAC Dialog
    if (showManualAddressDialog) {
        Dialog(onDismissRequest = { showManualAddressDialog = false }) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SurfaceCardStroke)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "MANUAL DEVICE CONFIG",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = manualNameInput,
                        onValueChange = { manualNameInput = it },
                        label = { Text("Device Name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = CyberTeal,
                            unfocusedBorderColor = SurfaceCardStroke
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualAddressInput,
                        onValueChange = { manualAddressInput = it.uppercase() },
                        label = { Text("Bluetooth MAC Address") },
                        placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                        isError = manualAddressInput.isNotBlank() && !BluetoothAdapter.checkBluetoothAddress(manualAddressInput.trim()),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = CyberTeal,
                            unfocusedBorderColor = SurfaceCardStroke
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (BluetoothAdapter.checkBluetoothAddress(manualAddressInput.trim())) {
                                    onSelectDevice(manualAddressInput.trim(), manualNameInput.trim())
                                    showManualAddressDialog = false
                                } else {
                                    Toast.makeText(context, "Enter six pairs of letters or digits separated by colons", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal, contentColor = TextWhite)
                        ) {
                            Text("Save Target", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showManualAddressDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SurfaceCardStroke)
                        ) {
                            Text("Cancel", color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 1. MODERN HEADER CARD
// =========================================================================

@Composable
fun ModernHeaderCard(
    isArmed: Boolean,
    deviceName: String,
    deviceAddress: String,
    onOpenScanner: () -> Unit,
    onTestBand: () -> Unit,
    isTesting: Boolean,
    needsHapticRecovery: Boolean,
    onOpenManual: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceCardStroke)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Watch, contentDescription = null, tint = CyberTeal, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("YOUR BAND", color = TextMuted, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                Text(if (isArmed) "Saved" else "Setup needed", color = if (isArmed) EmeraldText else AmberText, fontSize = 12.sp)
            }
            Text(if (deviceAddress.isBlank()) "Choose your Fitbit" else deviceName.ifBlank { "Fitbit band" },
                color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(if (isArmed) "Connects when an alert arrives. Test below to check delivery."
                else "Allow Bluetooth access and select a nearby band to get started.",
                color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenScanner, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = DarkBg)) {
                    Icon(Icons.Rounded.BluetoothSearching, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (deviceAddress.isBlank()) "Find band" else "Change band", fontSize = 13.sp)
                }
                OutlinedButton(onClick = onOpenManual, modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, SurfaceCardStroke)) {
                    Text("Manual setup", color = TextMuted, fontSize = 12.sp)
                }
            }
            if (needsHapticRecovery) {
                Text("An alert was interrupted. Recovery restores the pre-alert vibration intensity, then sends a test.",
                    color = AmberText, fontSize = 12.sp)
            }
            if (isArmed) {
                TextButton(onClick = onTestBand, enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (isTesting) "Sending test…" else if (needsHapticRecovery) "Recover vibration setting" else "Send a test vibration", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun CoexistenceSyncBanner(
    lastStatus: String,
    onForceSyncHealth: () -> Unit,
    onResetConnection: () -> Unit = {},
    onAutoDetectBand: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {}
) {
    val isError = listOf("error", "failed", "unable", "interrupted", "timed out", "not found", "could not")
        .any { lastStatus.contains(it, ignoreCase = true) }
    val bluetoothOff = lastStatus.contains("turned off", true) || lastStatus.contains("disabled", true)
    Surface(shape = RoundedCornerShape(18.dp), color = SurfaceCard,
        border = BorderStroke(1.dp, if (isError) WarningAmber.copy(alpha = 0.5f) else SurfaceCardStroke)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isError) Icons.Rounded.Info else Icons.Rounded.History,
                    contentDescription = null, tint = if (isError) WarningAmber else TextMuted,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isError) "Check your connection" else "Latest activity", color = TextWhite,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(lastStatus.ifBlank { "No alerts yet. Try a test with your band nearby." },
                color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = if (bluetoothOff) onEnableBluetooth else onResetConnection,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (bluetoothOff) "Turn on Bluetooth" else "Reset link", color = CyberTeal, fontSize = 12.sp)
                }
                TextButton(onClick = onForceSyncHealth, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open companion app", color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

private data class CallOptionItem(
    val pattern: AirHapticPattern,
    val title: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun ModernCallAlertCard(
    isEnabled: Boolean,
    selectedPattern: AirHapticPattern,
    isTesting: Boolean,
    hasPhonePerm: Boolean,
    callDetectionStatus: String,
    onToggle: (Boolean) -> Unit,
    onSelectPattern: (AirHapticPattern) -> Unit,
    onRequestPerm: () -> Unit,
    onToggleTest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (isEnabled) FlameOrange.copy(alpha = 0.4f) else SurfaceCardStroke)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(callDetectionStatus, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(FlameOrange.copy(alpha = 0.15f))
                            .border(1.dp, FlameOrange.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhoneInTalk,
                            contentDescription = null,
                            tint = FlameOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Incoming Calls",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Wrist alerts when your phone rings",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = FlameOrange,
                        uncheckedTrackColor = SurfaceCardElevated,
                        uncheckedThumbColor = TextDim
                    )
                )
            }

            if (!hasPhonePerm) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRequestPerm,
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DarkBg),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Grant Phone Permission ↗", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isEnabled) {
                Spacer(modifier = Modifier.height(14.dp))

                // Pattern options selector (2 options: Urgent Alert and Continuous Ring)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val callOptions = listOf(
                        CallOptionItem(
                            AirHapticPattern.URGENT,
                            "Urgent",
                            "Pulsed Bursts",
                            Icons.Rounded.Bolt
                        ),
                        CallOptionItem(
                            AirHapticPattern.CONTINUOUS,
                            "Continuous",
                            "Repeating pulses",
                            Icons.Rounded.GraphicEq
                        )
                    )

                    callOptions.forEach { opt ->
                        val pattern = opt.pattern
                        val title = opt.title
                        val subtitle = opt.subtitle
                        val icon = opt.icon
                        val isSelected = selectedPattern == pattern
                        Surface(
                            onClick = { onSelectPattern(pattern) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) FlameOrange.copy(alpha = 0.16f) else DarkBg,
                            border = BorderStroke(
                                if (isSelected) 1.5.dp else 1.dp,
                                if (isSelected) FlameOrange else SurfaceCardStroke
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) FlameOrange.copy(alpha = 0.25f) else SurfaceCardElevated)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) FlameOrange else TextMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = title,
                                        color = if (isSelected) FlameOrange else TextWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = subtitle,
                                        color = if (isSelected) TextWhite.copy(alpha = 0.85f) else TextDim,
                                        fontSize = 10.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Single Play / Pause Button for testing
                Surface(
                    onClick = onToggleTest,
                    shape = RoundedCornerShape(10.dp),
                    color = if (isTesting) FlameOrange else SurfaceCardElevated,
                    border = BorderStroke(1.dp, if (isTesting) FlameOrange else SurfaceCardStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isTesting) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isTesting) "Pause Test" else "Play Test",
                            tint = if (isTesting) DarkBg else FlameOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTesting) "Pause Test" else "Play Test Alert",
                            color = if (isTesting) DarkBg else FlameOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. PRIORITY APPS CARD (ALLOWED ON TOP)
// =========================================================================

@Composable
fun ModernPriorityAppsCard(
    isEnabled: Boolean,
    hasListenerPerm: Boolean,
    rules: List<AirNotificationRule>,
    isTestingVibration: Boolean = false,
    activeTestingAppPackage: String? = null,
    onToggleMaster: (Boolean) -> Unit,
    onToggleRule: (Int, Boolean) -> Unit,
    onOpenPattern: (Int) -> Unit,
    onPickApp: (Int) -> Unit,
    onTestAppPattern: (AirNotificationRule) -> Unit = {},
    onStopVibration: () -> Unit = {},
    onResetDefaults: () -> Unit,
    onOpenListenerSettings: () -> Unit
) {
    val context = LocalContext.current
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    val activeCount = rules.count { it.isEnabled }
    val grantedRules = remember(rules) {
        rules.filter { it.isEnabled }
    }
    // Sort so allowed (enabled) apps always stay at the top!
    val sortedRules = remember(rules) {
        rules.sortedWith(compareByDescending { it.isEnabled })
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (!isEnabled) SurfaceCardStroke else if (hasListenerPerm) CyberTeal.copy(alpha = 0.35f) else WarningAmber.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row (Clicking left toggles expand/collapse)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { isExpanded = !isExpanded }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isEnabled) CyberTeal.copy(alpha = 0.15f) else SurfaceCardElevated)
                            .border(1.dp, if (isEnabled) CyberTeal.copy(alpha = 0.35f) else SurfaceCardStroke, RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = if (isEnabled) CyberTeal else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Priority Apps",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (!isEnabled) "Notification vibrations paused" else if (isExpanded) "Choose up to 5 apps and their patterns" else "${grantedRules.size} of 5 on",
                            color = if (!isEnabled) WarningAmber else TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Master Notification Vibration Switch
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggleMaster,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TextWhite,
                            checkedTrackColor = CyberTeal,
                            uncheckedTrackColor = SurfaceCardElevated,
                            uncheckedThumbColor = TextDim
                        )
                    )

                    // Arrow Only Toggle Button
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = CyberTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // COLLAPSED STATE: Only show small icons of apps that are granted in horizontal!
            AnimatedVisibility(
                visible = !isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    if (grantedRules.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(grantedRules, key = { it.packageName }) { rule ->
                                val appName = InstalledAppsManager.getAppName(context, rule.packageName)
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceCardElevated)
                                        .border(1.dp, CyberTeal.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .clickable { isExpanded = true }
                                ) {
                                    AppRealIcon(
                                        packageName = rule.packageName,
                                        fallbackText = appName,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    // Neon green active indicator dot
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(NeonEmerald)
                                    )
                                }
                            }

                            // Tap to expand button at the end
                            item {
                                Surface(
                                    onClick = { isExpanded = true },
                                    shape = RoundedCornerShape(12.dp),
                                    color = DarkBg,
                                    border = BorderStroke(1.dp, SurfaceCardStroke)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Expand",
                                            tint = CyberTeal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Manage",
                                            color = CyberTeal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            onClick = { isExpanded = true },
                            shape = RoundedCornerShape(12.dp),
                            color = DarkBg,
                            border = BorderStroke(1.dp, SurfaceCardStroke),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "No apps enabled yet • Tap to choose apps",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = CyberTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // EXPANDED STATE: Shows all apps with slots, toggles, patterns, swap, reset
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    if (!hasListenerPerm) {
                        Button(
                            onClick = onOpenListenerSettings,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DarkBg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Enable Notification Access in Settings ↗", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // The 5 App Slot Rows (rearranged with allowed on top!)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        sortedRules.take(5).forEach { rule ->
                            val originalIndex = rules.indexOfFirst { it.packageName.equals(rule.packageName, ignoreCase = true) }
                            val targetIndex = if (originalIndex >= 0) originalIndex else 0
                            val isThisAppTesting = isTestingVibration && activeTestingAppPackage.equals(rule.packageName, ignoreCase = true)
                            ModernAppSlotRow(
                                rule = rule,
                                isTesting = isThisAppTesting,
                                onToggleTest = {
                                    if (isThisAppTesting) {
                                        onStopVibration()
                                    } else {
                                        onTestAppPattern(rule)
                                    }
                                },
                                onToggle = { enabled -> onToggleRule(targetIndex, enabled) },
                                onOpenPattern = { onOpenPattern(targetIndex) },
                                onPickApp = { onPickApp(targetIndex) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Tap app icon or name to swap app",
                            color = TextDim,
                            fontSize = 11.sp
                        )

                        TextButton(
                            onClick = onResetDefaults,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = "Reset 5 Defaults", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// MODERN APP SLOT ROW (USES REAL NATIVE APP ICONS)
// =========================================================================

@Composable
fun ModernAppSlotRow(
    rule: AirNotificationRule,
    isTesting: Boolean = false,
    onToggleTest: () -> Unit = {},
    onToggle: (Boolean) -> Unit,
    onOpenPattern: () -> Unit,
    onPickApp: () -> Unit
) {
    val context = LocalContext.current
    val appName = remember(rule.packageName) {
        InstalledAppsManager.getAppName(context, rule.packageName)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (rule.isEnabled) SurfaceCardElevated else SurfaceCardElevated.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (rule.isEnabled) CyberTeal.copy(alpha = 0.35f) else SurfaceCardStroke
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Real App Icon + Name + Pattern Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPickApp() }
            ) {
                AppRealIcon(
                    packageName = rule.packageName,
                    fallbackText = appName,
                    modifier = Modifier.size(44.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appName,
                            color = if (rule.isEnabled) TextWhite else TextDim,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Swap App",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            onClick = onOpenPattern,
                            shape = RoundedCornerShape(6.dp),
                            color = if (rule.isEnabled) DarkBg else DarkBg.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (rule.isEnabled) CyberTeal.copy(alpha = 0.4f) else SurfaceCardStroke)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = if (rule.isEnabled) CyberTeal else TextDim,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = rule.pattern.displayName,
                                    color = if (rule.isEnabled) CyberTeal else TextDim,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = "Choose Pattern",
                                    tint = if (rule.isEnabled) CyberTeal.copy(alpha = 0.7f) else TextDim,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Right: Toggle Switch
            Switch(
                checked = rule.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TextWhite,
                    checkedTrackColor = CyberTeal,
                    uncheckedTrackColor = DarkBg,
                    uncheckedThumbColor = TextDim
                )
            )
        }
    }
}

// =========================================================================
// 4. BETTER PERMISSION READINESS CARD (MODERN UI)
// =========================================================================

@Composable
fun ModernPermissionsCard(
    hasPhonePerm: Boolean,
    hasBluetoothPerm: Boolean,
    hasListenerPerm: Boolean,
    isBatteryOptimized: Boolean,
    onRequestPhonePerm: () -> Unit,
    onRequestBluetoothPerm: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenListenerSettings: () -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    val allReady = hasPhonePerm && hasBluetoothPerm && hasListenerPerm && !isBatteryOptimized
    val grantedCount = (if (hasBluetoothPerm) 1 else 0) +
            (if (hasPhonePerm) 1 else 0) +
            (if (hasListenerPerm) 1 else 0) +
            (if (!isBatteryOptimized) 1 else 0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(
            1.dp,
            if (allReady) NeonEmerald.copy(alpha = 0.35f) else WarningAmber.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header Row (Clicking toggles expand/collapse)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (allReady) EmeraldBg else AmberBg)
                            .border(
                                1.dp,
                                if (allReady) NeonEmerald.copy(alpha = 0.4f) else WarningAmber.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp)
                            )
                    ) {
                        Icon(
                            imageVector = if (allReady) Icons.Rounded.VerifiedUser else Icons.Rounded.SecurityUpdateWarning,
                            contentDescription = null,
                            tint = if (allReady) NeonEmerald else WarningAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Permissions & background access",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (allReady) "Required access is enabled" else "$grantedCount of 4 granted",
                            color = if (allReady) EmeraldText else AmberText,
                            fontSize = 11.sp
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (allReady) EmeraldBg else AmberBg)
                            .border(
                                1.dp,
                                if (allReady) NeonEmerald.copy(alpha = 0.5f) else WarningAmber.copy(alpha = 0.5f),
                                CircleShape
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (allReady) "ALL SET" else "PENDING",
                            color = if (allReady) EmeraldText else AmberText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Arrow Only Toggle Button
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = CyberTeal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // COLLAPSED STATE: 4 compact horizontal status chips
            AnimatedVisibility(
                visible = !isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PermissionCompactChip(
                        modifier = Modifier.weight(1f),
                        label = "BLE",
                        isGranted = hasBluetoothPerm,
                        icon = Icons.Rounded.Bluetooth,
                        onClick = { if (!hasBluetoothPerm) onRequestBluetoothPerm() else isExpanded = true }
                    )
                    PermissionCompactChip(
                        modifier = Modifier.weight(1f),
                        label = "Calls",
                        isGranted = hasPhonePerm,
                        icon = Icons.Rounded.PhoneInTalk,
                        onClick = { if (!hasPhonePerm) onRequestPhonePerm() else isExpanded = true }
                    )
                    PermissionCompactChip(
                        modifier = Modifier.weight(1f),
                        label = "Notifs",
                        isGranted = hasListenerPerm,
                        icon = Icons.Rounded.NotificationsActive,
                        onClick = { if (!hasListenerPerm) onOpenListenerSettings() else isExpanded = true }
                    )
                    PermissionCompactChip(
                        modifier = Modifier.weight(1f),
                        label = "Battery",
                        isGranted = !isBatteryOptimized,
                        icon = Icons.Rounded.BatteryChargingFull,
                        onClick = { if (isBatteryOptimized) onRequestBatteryOptimization() else isExpanded = true }
                    )
                }
            }

            // EXPANDED STATE: Full 2x2 Grid of Permission Tiles
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PermissionTile(
                            modifier = Modifier.weight(1f),
                            title = "Bluetooth BLE",
                            description = if (hasBluetoothPerm) "Permission granted" else "Tap to grant",
                            isGranted = hasBluetoothPerm,
                            icon = Icons.Rounded.Bluetooth,
                            onClick = onRequestBluetoothPerm
                        )
                        PermissionTile(
                            modifier = Modifier.weight(1f),
                            title = "Call State",
                            description = if (hasPhonePerm) "Incoming detection ready" else "Tap to grant",
                            isGranted = hasPhonePerm,
                            icon = Icons.Rounded.PhoneInTalk,
                            onClick = onRequestPhonePerm
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PermissionTile(
                            modifier = Modifier.weight(1f),
                            title = "Notifications",
                            description = if (hasListenerPerm) "Priority listener active" else "Tap to grant",
                            isGranted = hasListenerPerm,
                            icon = Icons.Rounded.NotificationsActive,
                            onClick = onOpenListenerSettings
                        )
                        PermissionTile(
                            modifier = Modifier.weight(1f),
                            title = "Battery Mode",
                            description = if (!isBatteryOptimized) "Unrestricted background" else "Tap to whitelist",
                            isGranted = !isBatteryOptimized,
                            icon = Icons.Rounded.BatteryChargingFull,
                            onClick = onRequestBatteryOptimization
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionCompactChip(
    modifier: Modifier = Modifier,
    label: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isGranted) SurfaceCardElevated else AmberBg.copy(alpha = 0.25f),
        border = BorderStroke(
            1.dp,
            if (isGranted) SurfaceCardStroke else WarningAmber.copy(alpha = 0.4f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) NeonEmerald else WarningAmber,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (isGranted) TextWhite else AmberText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(3.dp))
            Icon(
                imageVector = if (isGranted) Icons.Rounded.Check else Icons.Rounded.PriorityHigh,
                contentDescription = null,
                tint = if (isGranted) NeonEmerald else WarningAmber,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}


@Composable
fun PermissionTile(
    modifier: Modifier = Modifier,
    title: String,
    description: String,
    isGranted: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = if (!isGranted) onClick else { {} },
        shape = RoundedCornerShape(14.dp),
        color = if (isGranted) SurfaceCardElevated else AmberBg.copy(alpha = 0.25f),
        border = BorderStroke(
            1.dp,
            if (isGranted) SurfaceCardStroke else WarningAmber.copy(alpha = 0.4f)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) DarkBg else WarningAmber.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) NeonEmerald else WarningAmber,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    if (isGranted) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Granted",
                            tint = NeonEmerald,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Text(
                    text = description,
                    color = if (isGranted) TextMuted else WarningAmber,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

// =========================================================================
// =========================================================================
// 5. PRIVACY & HOW IT WORKS (INLINE EXPANDABLE ACCORDION CARD)
// =========================================================================

@Composable
fun ModernPrivacyAccordionCard() {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = if (isExpanded) 14.dp else 9.dp
            )
        ) {
            // Single-line Header Row: Title + Arrow toggle (no description subtitle, no Learn More text)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberTeal.copy(alpha = 0.15f))
                            .border(1.dp, CyberTeal.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = null,
                            tint = CyberTeal,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Privacy & how it works",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = CyberTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Accordion Content (Smoothly expands inline without any popup dialog)
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PrivacyPillarCard(
                        icon = Icons.Rounded.Lock,
                        title = "Does it vibrate when phone is locked?",
                        description = "AirBridge is designed to deliver alerts while your phone is locked. Delivery depends on Bluetooth range, permissions and Android background restrictions. Test it with your phone locked before relying on it."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.PhoneInTalk,
                        title = "How does incoming call vibration work?",
                        description = "SIM calls use your selected pattern. Repeating alerts stop when Android reports that ringing has ended, or when the service reaches its safety limit. A current alert may finish before stopping."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.VideoCall,
                        title = "Can I get WhatsApp or other app-call alerts?",
                        description = "Open App calls, enable it and select your calling apps. It sends one bounded burst for a recognized incoming-call notification, independently of SIM and message alerts. Some app versions or call types may not be detected. Meeting reminders and ongoing calls are excluded; no caller names or message text are used."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.Sync,
                        title = "How does companion-app syncing work?",
                        description = "AirBridge releases its connection after alerts. Repeating calls release the connection between bursts, with a 5-second gap. Sync timing is controlled by the companion app; uninterrupted sync is not guaranteed. Opening the companion app from AirBridge pauses alerts for 5 minutes so you can sync."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.Bolt,
                        title = "Does it drain phone or Fitbit battery?",
                        description = "Battery use varies with alert frequency, signal strength and retries. The service starts for alerts rather than polling continuously. No verified daily battery benchmark is available yet."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.RestartAlt,
                        title = "What if connection fails or is interrupted?",
                        description = "Bring your band nearby and check Bluetooth. Try Reset link, then test again. Use Change band to explicitly select a device. If an alert is interrupted, check the vibration intensity setting in the companion app."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.AccountBalance,
                        title = "Does this app affect UPI or Banking apps?",
                        description = "AirBridge does not request an Accessibility Service, overlay access or screen capture. Banking apps apply their own security policies, so compatibility cannot be guaranteed."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.VisibilityOff,
                        title = "Does it read my messages or private data?",
                        description = "Android notification access can expose notification content. AirBridge filters by app and uses notification identifiers and timestamps to avoid repeated alerts; its listener does not extract message text. Device and alert settings are stored on your phone."
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacyPillarCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceCardElevated,
        border = BorderStroke(1.dp, SurfaceCardStroke),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DarkBg)
                    .border(1.dp, SurfaceCardStroke, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CyberTeal,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// =========================================================================
// REAL NATIVE APP ICON COMPOSABLE
// =========================================================================

@Composable
fun AppRealIcon(
    packageName: String,
    fallbackText: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        try {
            val iconDrawable = InstalledAppsManager.getAppIcon(context, packageName)
            iconDrawable?.toBitmap(128, 128)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = fallbackText,
            modifier = modifier.clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceCardElevated)
                .border(1.dp, SurfaceCardStroke, RoundedCornerShape(12.dp))
        ) {
            Text(
                text = fallbackText.take(2).uppercase(),
                color = CyberTeal,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =========================================================================
// APP PICKER DIALOG (DISCOVERS ALL APPS ON PHONE WITH REAL ICONS & SEARCH)
// =========================================================================

@Composable
fun AppPickerDialog(
    slotNumber: Int,
    currentPackage: String,
    onDismiss: () -> Unit,
    onSelectApp: (DeviceApp) -> Unit,
    pickerTitle: String? = null
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val allApps = remember {
        InstalledAppsManager.getInstalledLauncherApps(context)
    }

    val filteredApps = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) {
            allApps
        } else {
            val q = searchQuery.trim().lowercase()
            allApps.filter {
                it.appName.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.82f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = pickerTitle ?: "CHOOSE APP FOR SLOT #$slotNumber",
                            color = CyberTeal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Installed on this phone",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...", fontSize = 13.sp, color = TextMuted) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = CyberTeal, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(imageVector = Icons.Rounded.Clear, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = CyberTeal,
                        unfocusedBorderColor = SurfaceCardStroke,
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // List of apps with real icons
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = app.packageName.equals(currentPackage, ignoreCase = true)
                        Surface(
                            onClick = { onSelectApp(app) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) SurfaceCardElevated else DarkBg,
                            border = BorderStroke(1.dp, if (isSelected) CyberTeal else SurfaceCardStroke)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppRealIcon(
                                    packageName = app.packageName,
                                    fallbackText = app.appName,
                                    modifier = Modifier.size(38.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        color = if (isSelected) CyberTeal else TextWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = app.packageName,
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = CyberTeal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// PATTERN SELECTION DIALOG
// =========================================================================

@Composable
fun SelectAppPatternDialog(
    appName: String,
    packageName: String,
    currentPattern: AirHapticPattern,
    isTestingVibration: Boolean = false,
    activeTestingPattern: AirHapticPattern? = null,
    onDismiss: () -> Unit,
    onSelect: (AirHapticPattern) -> Unit,
    onTestPattern: (AirHapticPattern) -> Unit,
    onStopVibration: () -> Unit = {}
) {
    val patterns = listOf(
        AirHapticPattern.SINGLE to "1 short pulse for casual updates",
        AirHapticPattern.DOUBLE to "2 distinct pulses for direct messages",
        AirHapticPattern.TRIPLE to "3 rhythmic pulses for group chats",
        AirHapticPattern.LONG_GAP to "2 spaced pulses for reminders",
        AirHapticPattern.URGENT to "Rapid pulse groups for prominent alerts"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppRealIcon(
                        packageName = packageName,
                        fallbackText = appName,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "VIBRATION PATTERN",
                            color = CyberTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = appName,
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    patterns.forEach { (pattern, description) ->
                        val isSelected = pattern == currentPattern
                        val isTestingThis = isTestingVibration && activeTestingPattern == pattern
                        Surface(
                            onClick = { onSelect(pattern) },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) SurfaceCardElevated else DarkBg,
                            border = BorderStroke(1.dp, if (isSelected) CyberTeal else SurfaceCardStroke)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = CyberTeal,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Column {
                                        Text(
                                            text = pattern.displayName,
                                            color = if (isSelected) CyberTeal else TextWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = description,
                                            color = TextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Surface(
                                    onClick = {
                                        if (isTestingThis) {
                                            onStopVibration()
                                        } else {
                                            onTestPattern(pattern)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isTestingThis) CyberTeal.copy(alpha = 0.25f) else SurfaceCard,
                                    border = BorderStroke(1.dp, if (isTestingThis) CyberTeal else SurfaceCardStroke)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isTestingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                            contentDescription = if (isTestingThis) "Pause Vibration" else "Play Vibration",
                                            tint = if (isTestingThis) CyberTeal else if (isSelected) CyberTeal else TextWhite,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isTestingThis) "Pause" else "Play",
                                            color = if (isTestingThis) CyberTeal else if (isSelected) CyberTeal else TextWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal, contentColor = TextWhite)
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================
// 6. ADVANCED DIAGNOSTICS & TEST BENCH (COLLAPSIBLE)
// =========================================================================

@Composable
fun ModernCollapsibleDiagnosticsCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    activeTestingPattern: AirHapticPattern?,
    lastStatus: String,
    onTestPattern: (AirHapticPattern) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceCardStroke)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpanded() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vibration Test Bench & Logs",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        text = "TEST VIBRATION BENCH",
                        color = CyberTeal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val patterns = listOf(
                            AirHapticPattern.SINGLE,
                            AirHapticPattern.DOUBLE,
                            AirHapticPattern.TRIPLE,
                            AirHapticPattern.LONG_GAP,
                            AirHapticPattern.URGENT
                        )
                        items(patterns) { pattern ->
                            val isTesting = activeTestingPattern == pattern
                            Button(
                                onClick = { onTestPattern(pattern) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isTesting) CyberTeal else SurfaceCardElevated,
                                    contentColor = if (isTesting) DarkBg else TextWhite
                                ),
                                border = BorderStroke(1.dp, if (isTesting) CyberTeal else SurfaceCardStroke),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = if (isTesting) "Pulsing..." else pattern.wireName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "LAST BLE LOG",
                        color = CyberTeal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkBg,
                        border = BorderStroke(1.dp, SurfaceCardStroke),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (lastStatus.isNotBlank()) lastStatus else "No BLE activity logged yet",
                            color = TextWhite,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "No Accessibility Service required",
                        color = TextDim,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BleScannerDialog(
    bleScanner: BleScanner,
    currentPairedAddress: String,
    onDismiss: () -> Unit,
    onSelect: (DiscoveredBleDevice) -> Unit
) {
    val discoveredDevices by bleScanner.devices.collectAsState()
    val devices = discoveredDevices.filter { it.isFitbit }
    val isScanning by bleScanner.isScanning.collectAsState()
    val scanError by bleScanner.error.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard),
            border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Find your band",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = CyberTeal,
                            strokeWidth = 2.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = scanError ?: if (isScanning) "Keep your band nearby. Select it from the list." else "Only devices advertising a Fitbit name are listed. Other devices need Manual setup.",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isScanning) "Listening for BLE advertisements..." else "No named Fitbit found. Wake the band and rescan, or use Manual setup for a known address.",
                            color = TextDim,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(devices) { dev ->
                            val isCurrent = dev.address.equals(currentPairedAddress, ignoreCase = true)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (dev.isFitbit) CyberTeal.copy(alpha = 0.12f) else SurfaceCardElevated)
                                    .border(
                                        1.dp,
                                        if (dev.isFitbit) CyberTeal.copy(alpha = 0.5f) else SurfaceCardStroke,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onSelect(dev) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dev.name,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                            color = if (dev.isFitbit) CyberTeal else TextWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "SAVED",
                                                color = NeonEmerald,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${dev.address} • RSSI ${dev.rssi} dBm",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                Button(
                                    onClick = { onSelect(dev) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (dev.isFitbit) PrimaryTeal else SurfaceCardElevated,
                                        contentColor = TextWhite
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Select", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { bleScanner.startScan() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal, contentColor = TextWhite)
                    ) {
                        Text("Rescan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SurfaceCardStroke)
                    ) {
                        Text("Close", color = TextMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
