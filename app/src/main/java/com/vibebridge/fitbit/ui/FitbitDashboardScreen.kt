package com.vibebridge.fitbit.ui

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

    val isArmed = hasPhonePerm && hasBluetoothPerm

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 20.dp)
    ) {

        // ==========================================
        // 1. MINIMALIST TOP HEADER & STATUS
        // ==========================================
        ModernHeaderCard(
            isArmed = isArmed,
            deviceName = pairedDeviceName,
            deviceAddress = pairedDeviceAddress,
            onOpenScanner = {
                showScannerDialog = true
                bleScanner.startScan()
            },
            onResetConnection = onResetConnection,
            onOpenManual = {
                manualAddressInput = pairedDeviceAddress
                manualNameInput = pairedDeviceName
                showManualAddressDialog = true
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Coexistence sync status & error recovery banner
        CoexistenceSyncBanner(
            lastStatus = lastStatus,
            onForceSyncHealth = onForceSyncHealth,
            onResetConnection = onResetConnection,
            onAutoDetectBand = onAutoDetectBand,
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
        ModernPrivacyAccordionCard()

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
                        placeholder = { Text("e.g. 5E:75:11:59:E2:43") },
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
                                if (manualAddressInput.isNotBlank()) {
                                    onSelectDevice(manualAddressInput.trim(), manualNameInput.trim())
                                    showManualAddressDialog = false
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
    onResetConnection: () -> Unit = {},
    onOpenManual: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (isArmed) CyberTeal.copy(alpha = 0.35f) else WarningAmber.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isArmed) NeonEmerald else WarningAmber)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = if (isArmed) "CONNECTED • DIRECT BLE" else "STANDBY • CHECK PERMISSIONS",
                            color = if (isArmed) EmeraldText else AmberText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = deviceName,
                        color = TextWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Quick Reset Button
                    Surface(
                        onClick = onResetConnection,
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCardElevated,
                        border = BorderStroke(1.dp, SurfaceCardStroke)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset",
                                tint = CyberTeal,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reset",
                                color = CyberTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Bluetooth pill button
                    Surface(
                        onClick = onOpenScanner,
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCardElevated,
                        border = BorderStroke(1.dp, SurfaceCardStroke)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bluetooth,
                                contentDescription = null,
                                tint = CyberTeal,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = deviceAddress.takeLast(8),
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// COEXISTENCE SYNC & ERROR RECOVERY BANNER
// =========================================================================

@Composable
fun CoexistenceSyncBanner(
    lastStatus: String,
    onForceSyncHealth: () -> Unit,
    onResetConnection: () -> Unit = {},
    onAutoDetectBand: () -> Unit = {},
    onEnableBluetooth: () -> Unit = {}
) {
    val isError = lastStatus.contains("Error", ignoreCase = true) ||
            lastStatus.contains("timeout", ignoreCase = true) ||
            lastStatus.contains("disabled", ignoreCase = true) ||
            lastStatus.contains("glitch", ignoreCase = true) ||
            lastStatus.contains("interrupted", ignoreCase = true) ||
            lastStatus.contains("failed", ignoreCase = true)
    val isBtDisabled = lastStatus.contains("Bluetooth is turned off", ignoreCase = true) ||
            lastStatus.contains("Bluetooth is disabled", ignoreCase = true)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (isError) AmberBg.copy(alpha = 0.25f) else SurfaceCard,
        border = BorderStroke(1.dp, if (isError) WarningAmber.copy(alpha = 0.5f) else SurfaceCardStroke)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
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
                        modifier = Modifier
                            .size(8.dp)
                            .background(if (isError) WarningAmber else NeonEmerald, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isError) "Connection Interrupted" else "Google Health Coexistence",
                        color = if (isError) AmberText else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Quick Reset pill
                    Surface(
                        onClick = onResetConnection,
                        shape = RoundedCornerShape(8.dp),
                        color = if (isError) WarningAmber.copy(alpha = 0.2f) else SurfaceCardElevated,
                        border = BorderStroke(1.dp, if (isError) WarningAmber.copy(alpha = 0.4f) else SurfaceCardStroke)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = "Reset",
                                tint = if (isError) WarningAmber else CyberTeal,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Reset",
                                color = if (isError) AmberText else CyberTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Sync Health pill
                    Surface(
                        onClick = onForceSyncHealth,
                        shape = RoundedCornerShape(8.dp),
                        color = CyberTeal.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Sync,
                                contentDescription = "Sync",
                                tint = CyberTeal,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Sync Health",
                                color = CyberTeal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (lastStatus.isNotBlank()) lastStatus else "Bluetooth releases in ~1.5s after pulses • Step & sleep sync never blocked",
                color = if (isError) TextWhite else TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 3
            )

            // When error is present, display recovery action buttons
            if (isError) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isBtDisabled) {
                        Button(
                            onClick = onEnableBluetooth,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DarkBg),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Bluetooth, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Turn On Bluetooth", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onResetConnection,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberTeal, contentColor = DarkBg),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Link", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onAutoDetectBand,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyberTeal),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = CyberTeal, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto-Find Band", color = CyberTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 2. MODERN CALL ALERT CARD
// =========================================================================

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
                            text = "Vibrate band on ring without waking screen",
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
                            "Steady Buzz",
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
                            text = if (!isEnabled) "Notification vibrations paused" else if (isExpanded) "Allowed apps stay at top" else "${grantedRules.size} of 5 on",
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
                            text = "System Readiness",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (allReady) "All permissions active" else "$grantedCount of 4 granted",
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
                            description = if (hasBluetoothPerm) "Connected & ready" else "Tap to grant",
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
                        text = "FAQs & How AirBridge Works",
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
                        description = "Yes, 100%. AirBridge runs with background Android wake-locks. When a call or notification arrives while your phone is locked or in your pocket, AirBridge connects directly via BLE and vibrates your Fitbit Air instantly without waking or unlocking your screen."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.PhoneInTalk,
                        title = "How does incoming call vibration work?",
                        description = "When your phone rings, AirBridge continuously pulses the Urgent Alert pattern on your Fitbit Air until you answer, reject, or silence the call."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.Sync,
                        title = "Why does Google Health say 'Synced X min ago'?",
                        description = "This is Google Health's default battery-saver schedule. It syncs in 15–30 minute batches in the background. Pull down in Google Health anytime for an instant live sync. AirBridge releases Bluetooth in 1–2s so Google sync is never blocked."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.Bolt,
                        title = "Does it drain phone or Fitbit battery?",
                        description = "Virtually zero (< 1% per day). AirBridge uses event-driven Android listeners and stays completely asleep until an alert arrives. On the band, Bluetooth Low Energy uses micro-joules, consuming only ~1–2% battery across a full day."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.RestartAlt,
                        title = "What if connection fails or is interrupted?",
                        description = "If Bluetooth drops or band was out of range, tap 'Reset' or 'Auto-Find Band' at the top. AirBridge flushes stuck Android GATT caches, clears stale DTLS handshakes, and re-links in seconds."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.AccountBalance,
                        title = "Does this app affect UPI or Banking apps?",
                        description = "Not at all! Unlike older notification tools, AirBridge requires NO Accessibility Service, NO overlay draw permissions, and NO screen capture. Android Banking and UPI apps (GPay, PhonePe, Paytm, Banking apps) detect zero risk and work normally."
                    )

                    PrivacyPillarCard(
                        icon = Icons.Rounded.VisibilityOff,
                        title = "Does it read my messages or private data?",
                        description = "Never. AirBridge only inspects which app sent the notification (e.g. WhatsApp, Gmail) to trigger your custom vibration pattern. It never reads or stores message text, photos, sender details, or contacts. 100% offline & on-device."
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
    onSelectApp: (DeviceApp) -> Unit
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
                            text = "CHOOSE APP FOR SLOT #$slotNumber",
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
        AirHapticPattern.URGENT to "4 rapid urgent pulses for emergencies"
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
                        text = "100% Zero-Accessibility • Background BLE GATTlink / DTLS 1.2",
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
    val devices by bleScanner.devices.collectAsState()
    val isScanning by bleScanner.isScanning.collectAsState()

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
                        text = "BLE RADAR SCANNER",
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
                    text = if (isScanning) "Searching for nearby Fitbit Air devices..." else "Scan finished.",
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
                            text = if (isScanning) "Listening for BLE advertisements..." else "No devices found.",
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
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = dev.name,
                                            color = if (dev.isFitbit) CyberTeal else TextWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "PAIRED",
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
                                    modifier = Modifier.height(30.dp)
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
