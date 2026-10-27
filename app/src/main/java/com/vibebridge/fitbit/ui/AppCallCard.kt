package com.vibebridge.fitbit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibebridge.fitbit.AirBridgeNotificationSettings
import com.vibebridge.fitbit.calls.*
import com.vibebridge.fitbit.ui.theme.*

@Composable
fun AppCallCard(config: AppCallConfig, onChange: (AppCallConfig) -> Unit,
                hasNotificationAccess: Boolean, onGrantAccess: () -> Unit) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var patternMenu by remember { mutableStateOf(false) }
    val suggestedPackages = AppCallSettings.suggested.flatMap { it.packages }.toSet()
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceCardStroke), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.VideoCall, contentDescription = null, tint = CyberTeal)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("App calls", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(if (config.enabled) "Incoming calls from selected apps" else "Off • WhatsApp, Slack, Meet, Zoom & more",
                        color = TextMuted, fontSize = 11.sp)
                }
                Switch(config.enabled, onCheckedChange = { onChange(config.copy(enabled = it)); if (it) expanded = true },
                    modifier = Modifier.semantics { contentDescription = "Enable app call alerts" })
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse app calls" else "Expand app calls", tint = TextMuted)
                }
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("One alert burst per recognized incoming call, separate from SIM calls and message alerts. Your schedule and sync pause still apply.",
                        color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    if (!hasNotificationAccess) {
                        Text("Notification access is required for app calls.", color = AmberText, fontSize = 12.sp)
                        TextButton(onClick = onGrantAccess) { Text("Allow notification access") }
                    }
                    AppCallSettings.suggested.forEach { app ->
                        val selected = app.packages.any { it in config.packages }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.label, color = TextWhite, modifier = Modifier.weight(1f))
                            Switch(selected, onCheckedChange = { enabled ->
                                val updated = if (enabled) config.packages + app.packages else config.packages - app.packages
                                if (updated.size <= AppCallSettings.MAX_APPS) onChange(config.copy(packages = updated))
                            }, modifier = Modifier.semantics { contentDescription = "Call alerts for ${app.label}" })
                        }
                    }
                    (config.packages - suggestedPackages).sorted().forEach { pkg ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(AirBridgeNotificationSettings.getAppName(context, pkg), color = TextWhite, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onChange(config.copy(packages = config.packages - pkg)) }) { Text("Remove") }
                        }
                    }
                    TextButton(onClick = { showPicker = true }, enabled = config.packages.size < AppCallSettings.MAX_APPS,
                        modifier = Modifier.fillMaxWidth()) { Text("Add another calling app") }
                    Box {
                        OutlinedButton(onClick = { patternMenu = true }) { Text("Call pattern: ${config.pattern.displayName}") }
                        DropdownMenu(expanded = patternMenu, onDismissRequest = { patternMenu = false }) {
                            AppCallSettings.patterns.forEach { pattern ->
                                DropdownMenuItem(text = { Text(pattern.displayName) }, onClick = {
                                    onChange(config.copy(pattern = pattern)); patternMenu = false
                                })
                            }
                        }
                    }
                    if (config.enabled && config.packages.isEmpty()) Text("Choose at least one calling app.", color = AmberText, fontSize = 12.sp)
                    Text("Requires a recognizable incoming-call notification. Some app versions or call types may not be detected. Meeting reminders and ongoing calls don't trigger call alerts.",
                        color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
    if (showPicker) AppPickerDialog(slotNumber = 0, currentPackage = "", pickerTitle = "ADD A CALLING APP",
        onDismiss = { showPicker = false }, onSelectApp = { app ->
            if (config.packages.size < AppCallSettings.MAX_APPS) onChange(config.copy(packages = config.packages + app.packageName))
            showPicker = false
        })
}
