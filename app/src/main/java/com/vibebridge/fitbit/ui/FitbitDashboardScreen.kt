package com.vibebridge.fitbit.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vibebridge.fitbit.AirBridgeNotificationSettings
import com.vibebridge.fitbit.AirHapticPattern
import com.vibebridge.fitbit.AirNotificationRule
import com.vibebridge.fitbit.ble.BleScanner
import com.vibebridge.fitbit.ble.DiscoveredBleDevice
import com.vibebridge.fitbit.ui.theme.*

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
    callPattern: AirHapticPattern,
    rulesText: String,
    bleScanner: BleScanner,
    onToggleCallAlert: (Boolean) -> Unit,
    onChangeCallPattern: (AirHapticPattern) -> Unit,
    onSelectDevice: (String, String) -> Unit,
    onTestCallAlert: () -> Unit,
    onTestPattern: (AirHapticPattern) -> Unit,
    onSaveRulesText: (String) -> Boolean,
    onRequestPhonePerm: () -> Unit,
    onRequestBluetoothPerm: () -> Unit,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onOpenNotificationListenerSettings: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showScannerDialog by remember { mutableStateOf(false) }
    var showManualAddressDialog by remember { mutableStateOf(false) }
    var manualAddressInput by remember { mutableStateOf(pairedDeviceAddress) }
    var manualNameInput by remember { mutableStateOf(pairedDeviceName) }

    var isTestingCall by remember { mutableStateOf(false) }
    var activeTestingPattern by remember { mutableStateOf<AirHapticPattern?>(null) }
    var showRuleEditor by remember { mutableStateOf(false) }
    var editedRulesText by remember(rulesText) { mutableStateOf(rulesText) }

    val isArmed = hasPhonePerm && hasBluetoothPerm

    val parsedRules = remember(rulesText) {
        AirBridgeNotificationSettings.loadRules(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {

        // ==========================================
        // 1. HERO CARD WITH LIVE RADAR & TARGET BAND
        // ==========================================
        HeroBleRadarCard(
            isArmed = isArmed,
            deviceName = pairedDeviceName,
            deviceAddress = pairedDeviceAddress,
            lastStatus = lastStatus,
            onOpenScanner = {
                showScannerDialog = true
                bleScanner.startScan()
            },
            onOpenManualInput = {
                manualAddressInput = pairedDeviceAddress
                manualNameInput = pairedDeviceName
                showManualAddressDialog = true
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 2. INCOMING CALL COMMAND CENTER
        // ==========================================
        CallFeatureCard(
            isCallAlertEnabled = isCallAlertEnabled,
            selectedPattern = callPattern,
            isTesting = isTestingCall,
            hasPhonePerm = hasPhonePerm,
            onToggleEnabled = onToggleCallAlert,
            onSelectPattern = onChangeCallPattern,
            onRequestPerm = onRequestPhonePerm,
            onTestCall = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isTestingCall = true
                onTestCallAlert()
                Handler(Looper.getMainLooper()).postDelayed({
                    isTestingCall = false
                }, 3000)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 3. PATTERN TEST BENCH (QUICK PREVIEW)
        // ==========================================
        PatternTestBenchCard(
            activePattern = activeTestingPattern,
            onTest = { pattern ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                activeTestingPattern = pattern
                onTestPattern(pattern)
                val duration = 800L * pattern.groupCount + pattern.betweenGroupDelaysMillis.sum()
                Handler(Looper.getMainLooper()).postDelayed({
                    activeTestingPattern = null
                }, duration + 500)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 4. APP NOTIFICATION ROUTING BENCH
        // ==========================================
        AppRoutingCard(
            hasListenerPerm = hasNotificationListenerPerm,
            rules = parsedRules,
            rawText = editedRulesText,
            isEditing = showRuleEditor,
            onToggleEditing = { showRuleEditor = !showRuleEditor },
            onTextChange = { editedRulesText = it },
            onSave = {
                val ok = onSaveRulesText(editedRulesText)
                if (ok) {
                    showRuleEditor = false
                    Toast.makeText(context, "Routing rules saved!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Invalid format. Use package=pattern", Toast.LENGTH_LONG).show()
                }
            },
            onOpenListenerSettings = onOpenNotificationListenerSettings
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ==========================================
        // 5. HARDWARE & PERMISSION CHECKLIST
        // ==========================================
        PermissionChecklistCard(
            hasPhonePerm = hasPhonePerm,
            hasBluetoothPerm = hasBluetoothPerm,
            hasListenerPerm = hasNotificationListenerPerm,
            isBatteryOptimized = isBatteryOptimized,
            onRequestPhonePerm = onRequestPhonePerm,
            onRequestBluetoothPerm = onRequestBluetoothPerm,
            onRequestBatteryOptimization = onRequestIgnoreBatteryOptimizations,
            onOpenListenerSettings = onOpenNotificationListenerSettings
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 6. ZERO-ACCESSIBILITY ARCHITECTURE BADGE
        // ==========================================
        ZeroAccessibilityArchitectureCard()

        Spacer(modifier = Modifier.height(24.dp))
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

    // Manual MAC Address Dialog
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
// 1. HERO BLE RADAR CARD
// =========================================================================

@Composable
fun HeroBleRadarCard(
    isArmed: Boolean,
    deviceName: String,
    deviceAddress: String,
    lastStatus: String,
    onOpenScanner: () -> Unit,
    onOpenManualInput: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Alpha"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (isArmed) CyberTeal.copy(alpha = 0.45f) else WarningAmber.copy(alpha = 0.45f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isArmed) CyberTeal.copy(alpha = 0.15f) else WarningAmber.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        radius = 650f
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceCardElevated)
                            .border(1.dp, SurfaceCardStroke, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "PURE BLE • GATT / DTLS 1.2",
                            color = CyberTeal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isArmed) EmeraldBg else AmberBg)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isArmed) NeonEmerald else WarningAmber)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isArmed) "ARMED" else "STANDBY",
                            color = if (isArmed) EmeraldText else AmberText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(56.dp)
                    ) {
                        if (isArmed) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(NeonEmerald.copy(alpha = pulseAlpha))
                            )
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(if (isArmed) NeonEmerald else WarningAmber)
                        ) {
                            Icon(
                                imageVector = if (isArmed) Icons.Rounded.BluetoothConnected else Icons.Rounded.BluetoothSearching,
                                contentDescription = null,
                                tint = DarkBg,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = deviceName,
                            color = TextWhite,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = deviceAddress,
                            color = CyberTeal,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-time status bubble
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkBg.copy(alpha = 0.7f))
                        .border(1.dp, SurfaceCardStroke, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = lastStatus,
                            color = TextWhite,
                            fontSize = 11.sp,
                            maxLines = 2,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onOpenScanner,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal, contentColor = TextWhite)
                    ) {
                        Icon(imageVector = Icons.Rounded.Radar, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "BLE Radar Scan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onOpenManualInput,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SurfaceCardStroke),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                    ) {
                        Icon(imageVector = Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// =========================================================================
// 2. CALL FEATURE CARD
// =========================================================================

@Composable
fun CallFeatureCard(
    isCallAlertEnabled: Boolean,
    selectedPattern: AirHapticPattern,
    isTesting: Boolean,
    hasPhonePerm: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onSelectPattern: (AirHapticPattern) -> Unit,
    onRequestPerm: () -> Unit,
    onTestCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (isCallAlertEnabled) FlameOrange.copy(alpha = 0.5f) else SurfaceCardStroke)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FlameOrange.copy(alpha = 0.2f))
                    ) {
                        Icon(imageVector = Icons.Rounded.PhoneInTalk, contentDescription = null, tint = FlameOrange, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "INCOMING CALL VIBRATION",
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Direct BLE buzz without turning on phone screen",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = isCallAlertEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TextWhite,
                        checkedTrackColor = FlameOrange,
                        uncheckedTrackColor = SurfaceCardElevated
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
                    Text(text = "Grant Phone State Permission ↗", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Call Vibration Pattern:",
                color = TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Pattern options
            val patterns = listOf(
                AirHapticPattern.URGENT,
                AirHapticPattern.CONTINUOUS,
                AirHapticPattern.DOUBLE,
                AirHapticPattern.TRIPLE
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(patterns) { pattern ->
                    val isSelected = selectedPattern == pattern
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPattern(pattern) },
                        label = { Text(pattern.displayName, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = FlameOrange,
                            selectedLabelColor = TextWhite,
                            containerColor = SurfaceCardElevated,
                            labelColor = TextWhite
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) FlameOrange else SurfaceCardStroke
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onTestCall,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isTesting) FlameOrange else FlameOrange.copy(alpha = 0.85f),
                    contentColor = TextWhite
                )
            ) {
                Icon(imageVector = Icons.Rounded.Vibration, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTesting) "Vibrating Fitbit Air Now..." else "Test Call Vibration on Band",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =========================================================================
// 3. PATTERN TEST BENCH CARD
// =========================================================================

@Composable
fun PatternTestBenchCard(
    activePattern: AirHapticPattern?,
    onTest: (AirHapticPattern) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceCardStroke)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HAPTIC PATTERN BENCH",
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(text = "PURE BLE", color = CyberTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = "Tap any rhythm below to execute directly on the wristband via CoAP POST /settings.",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val list = listOf(
                    AirHapticPattern.SINGLE,
                    AirHapticPattern.DOUBLE,
                    AirHapticPattern.TRIPLE,
                    AirHapticPattern.LONG_GAP,
                    AirHapticPattern.URGENT
                )
                items(list) { pattern ->
                    val isTesting = activePattern == pattern
                    Button(
                        onClick = { onTest(pattern) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTesting) CyberTeal else SurfaceCardElevated,
                            contentColor = if (isTesting) DarkBg else TextWhite
                        ),
                        border = BorderStroke(1.dp, if (isTesting) CyberTeal else SurfaceCardStroke),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = if (isTesting) "Pulsing..." else pattern.wireName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// =========================================================================
// 4. APP ROUTING CARD
// =========================================================================

@Composable
fun AppRoutingCard(
    hasListenerPerm: Boolean,
    rules: List<AirNotificationRule>,
    rawText: String,
    isEditing: Boolean,
    onToggleEditing: () -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onOpenListenerSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (hasListenerPerm) SurfaceCardStroke else WarningAmber.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "APP NOTIFICATION ROUTING",
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "${rules.size} ACTIVE",
                    color = if (hasListenerPerm) NeonEmerald else WarningAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Route app notifications (WhatsApp, Telegram, etc.) with custom vibration patterns.",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            if (!hasListenerPerm) {
                Button(
                    onClick = onOpenListenerSettings,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, contentColor = DarkBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Grant Notification Access in Settings ↗", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (!isEditing) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rules.forEach { rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceCardElevated)
                                .border(1.dp, SurfaceCardStroke, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rule.packageName,
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(DarkBg)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = rule.pattern.wireName,
                                    color = CyberTeal,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = onToggleEditing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SurfaceCardStroke),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                ) {
                    Icon(imageVector = Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Edit App Rules (package=pattern)", fontSize = 11.sp)
                }
            } else {
                OutlinedTextField(
                    value = rawText,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextWhite,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkBg,
                        unfocusedContainerColor = DarkBg,
                        focusedBorderColor = CyberTeal,
                        unfocusedBorderColor = SurfaceCardStroke
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal, contentColor = TextWhite)
                    ) {
                        Text(text = "Save Rules", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onToggleEditing,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SurfaceCardStroke),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted)
                    ) {
                        Text(text = "Cancel", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 5. PERMISSION CHECKLIST CARD
// =========================================================================

@Composable
fun PermissionChecklistCard(
    hasPhonePerm: Boolean,
    hasBluetoothPerm: Boolean,
    hasListenerPerm: Boolean,
    isBatteryOptimized: Boolean,
    onRequestPhonePerm: () -> Unit,
    onRequestBluetoothPerm: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenListenerSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceCardStroke)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "SYSTEM HEALTH & PERMISSIONS",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            PermissionRow(
                title = "Bluetooth & Nearby Scanning",
                desc = "Required for BLE GATT and DTLS 1.2 handshake",
                isGranted = hasBluetoothPerm,
                onClick = onRequestBluetoothPerm
            )

            Spacer(modifier = Modifier.height(6.dp))

            PermissionRow(
                title = "Phone State & Calls",
                desc = "Required to detect incoming calls instantly",
                isGranted = hasPhonePerm,
                onClick = onRequestPhonePerm
            )

            Spacer(modifier = Modifier.height(6.dp))

            PermissionRow(
                title = "Notification Listener",
                desc = "Required to route WhatsApp and app alerts",
                isGranted = hasListenerPerm,
                onClick = onOpenListenerSettings
            )

            Spacer(modifier = Modifier.height(6.dp))

            PermissionRow(
                title = "Unrestricted Battery Mode",
                desc = "Prevents OS battery killer from stopping background service",
                isGranted = !isBatteryOptimized,
                onClick = onRequestBatteryOptimization
            )
        }
    }
}

@Composable
fun PermissionRow(
    title: String,
    desc: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceCardElevated)
            .border(1.dp, SurfaceCardStroke, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = desc, color = TextMuted, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isGranted) EmeraldBg else AmberBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isGranted) "GRANTED" else "FIX ↗",
                color = if (isGranted) EmeraldText else AmberText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =========================================================================
// 6. ZERO ACCESSIBILITY ARCHITECTURE CARD
// =========================================================================

@Composable
fun ZeroAccessibilityArchitectureCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardElevated),
        border = BorderStroke(1.dp, CyberTeal.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Rounded.VerifiedUser, contentDescription = null, tint = CyberTeal, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Zero Accessibility • Clean Background BLE",
                    color = TextWhite,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Unlike accessibility screen-tapping which pops the Fitbit app onto the phone screen and interrupts you, this app operates purely via low-energy Bluetooth GATT packets, DTLS 1.2 BOOTSTRAP, and CoAP Protobuf. The band pulses cleanly on your wrist while your phone screen stays completely untouched.",
                color = TextMuted,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

// =========================================================================
// 7. BLE SCANNER DIALOG
// =========================================================================

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
