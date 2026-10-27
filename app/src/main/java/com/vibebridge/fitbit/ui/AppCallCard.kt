package com.vibebridge.fitbit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vibebridge.fitbit.calls.*
import com.vibebridge.fitbit.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppCallCard(config: AppCallConfig, onChange: (AppCallConfig) -> Unit,
                hasNotificationAccess: Boolean, onGrantAccess: () -> Unit) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    var patternMenu by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(CallingAppsDiscovery.cachedApps(config.packages + config.slots).orEmpty()) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshVersion by remember { mutableStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshVersion++ }
        val packageChanges = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                CallingAppsDiscovery.invalidate()
                refreshVersion++
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
            addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        androidx.core.content.ContextCompat.registerReceiver(context, packageChanges, filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); context.unregisterReceiver(packageChanges) }
    }
    LaunchedEffect(expanded, refreshVersion) {
        if (expanded) {
            // Expanding and toggling alerts reuse the inventory; only a cold scan shows loading.
            val selected = config.packages + config.slots
            val existing = CallingAppsDiscovery.cachedApps(selected)
            if (existing != null) apps = existing
            else {
                refreshing = apps.isEmpty()
                try {
                    apps = withContext(Dispatchers.IO) { CallingAppsDiscovery.find(context, selected) }
                } finally { refreshing = false }
            }
        }
    }
    val slots = CallingAppSlots.resolve(config.slots, config.packages, apps.map { it.packageName }.toSet(),
        apps.filter { it.reason != CallingAppReason.DECLARED && it.reason != CallingAppReason.MANUAL }.map { it.packageName })
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, if (!config.enabled) SurfaceCardStroke else if (hasNotificationAccess) CyberTeal.copy(alpha = 0.35f) else WarningAmber.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (config.enabled) CyberTeal.copy(alpha = 0.15f) else SurfaceCardElevated)
                        .border(1.dp, if (config.enabled) CyberTeal.copy(alpha = 0.35f) else SurfaceCardStroke, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.VideoCall, contentDescription = null,
                            tint = if (config.enabled) CyberTeal else TextMuted, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("App calls", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(if (config.enabled) "${config.packages.size} of 5 apps on" else "App-call vibrations paused",
                            color = if (config.enabled) TextMuted else WarningAmber, fontSize = 11.sp)
                    }
                }
                Switch(config.enabled, onCheckedChange = { onChange(config.copy(enabled = it)); if (it) expanded = true },
                    colors = SwitchDefaults.colors(checkedThumbColor = TextWhite, checkedTrackColor = CyberTeal,
                        uncheckedTrackColor = SurfaceCardElevated, uncheckedThumbColor = TextDim),
                    modifier = Modifier.semantics { contentDescription = "Enable app call alerts" })
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse app calls" else "Expand app calls", tint = CyberTeal, modifier = Modifier.size(20.dp))
                }
            }
            AnimatedVisibility(!expanded) {
                val selectedApps = (config.slots + CallingAppSlots.preferred + config.packages.sorted())
                    .filter { it in config.packages }.distinct().take(CallingAppSlots.MAX)
                if (selectedApps.isEmpty()) {
                    Text("No calling apps selected", color = TextMuted, fontSize = 12.sp,
                        modifier = Modifier.clickable { expanded = true }.padding(vertical = 4.dp))
                } else {
                    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        items(selectedApps, key = { it }) { pkg ->
                            val label = apps.firstOrNull { it.packageName == pkg }?.label
                                ?: com.vibebridge.fitbit.util.InstalledAppsManager.getAppName(context, pkg)
                            Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceCardElevated)
                                .border(1.dp, CyberTeal.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable { expanded = true }.semantics { contentDescription = "$label call alerts; expand app calls" },
                                contentAlignment = Alignment.Center) {
                                AppRealIcon(pkg, label, Modifier.size(32.dp))
                                Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(7.dp)
                                    .clip(CircleShape).background(NeonEmerald))
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Select apps for incoming-call alerts. Tap an icon or name to change an app.",
                        color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
                    if (!hasNotificationAccess) {
                        Text("Notification access is required for app calls.", color = AmberText, fontSize = 12.sp)
                        TextButton(onClick = onGrantAccess) { Text("Allow notification access") }
                    }
                    if (refreshing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    repeat(CallingAppSlots.MAX) { index ->
                        val pkg = slots.getOrNull(index)
                        val app = apps.firstOrNull { it.packageName == pkg }
                        val selected = pkg != null && pkg in config.packages
                        Surface(shape = RoundedCornerShape(16.dp),
                            color = if (selected) SurfaceCardElevated else SurfaceCardElevated.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, if (selected) CyberTeal.copy(alpha = 0.35f) else SurfaceCardStroke),
                            modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(Modifier.weight(1f).clickable(enabled = !refreshing) { editingSlot = index }
                                    .semantics { contentDescription = "Change call app ${index + 1}" },
                                    verticalAlignment = Alignment.CenterVertically) {
                                    if (pkg != null) AppRealIcon(pkg, app?.label ?: pkg, Modifier.size(44.dp))
                                    else Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(app?.label ?: "Choose app", color = if (selected) TextWhite else TextDim,
                                                fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1,
                                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Swap calling app",
                                                tint = TextMuted, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                                        }
                                        Text("Incoming calls", color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                                Switch(checked = selected, enabled = pkg != null && !refreshing,
                                    onCheckedChange = { enabled ->
                                        if (pkg != null) onChange(config.copy(slots = slots,
                                            packages = if (enabled) config.packages + pkg else config.packages - pkg))
                                    }, colors = SwitchDefaults.colors(checkedThumbColor = TextWhite, checkedTrackColor = CyberTeal,
                                        uncheckedTrackColor = DarkBg, uncheckedThumbColor = TextDim),
                                    modifier = Modifier.semantics { contentDescription = "Call alerts for ${app?.label ?: "empty slot"}" })
                            }
                        }
                    }
                    Box {
                        OutlinedButton(onClick = { patternMenu = true }) { Text("Call pattern: ${config.pattern.displayName}") }
                        DropdownMenu(expanded = patternMenu, onDismissRequest = { patternMenu = false }) {
                            AppCallSettings.patterns.forEach { pattern -> DropdownMenuItem(text = { Text(pattern.displayName) }, onClick = {
                                onChange(config.copy(pattern = pattern)); patternMenu = false
                            }) }
                        }
                    }
                    if (config.enabled && config.packages.isEmpty()) Text("Select at least one app above.", color = AmberText, fontSize = 12.sp)
                    Text("One burst per recognized incoming call. Your schedule and sync pause apply. Detection may miss some apps or call types; meeting reminders and ongoing calls are excluded.",
                        color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }
        }
    }
    editingSlot?.let { index ->
        CallingAppPicker(apps, onDismiss = { editingSlot = null }, onSelect = { pkg ->
            val (updated, enabled) = CallingAppSlots.replace(slots, config.packages, index, pkg)
            onChange(config.copy(slots = updated, packages = enabled)); editingSlot = null
        })
    }
}

@Composable
private fun CallingAppPicker(apps: List<DetectedCallingApp>, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    var search by remember { mutableStateOf("") }
    val filtered = apps.filter { it.label.contains(search, true) || it.packageName.contains(search, true) }
        .sortedWith(compareBy<DetectedCallingApp> { it.reason == CallingAppReason.DECLARED }.thenBy { it.label.lowercase() })
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceCard, title = { Text("Change calling app") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(search, onValueChange = { search = it }, singleLine = true, label = { Text("Search installed apps") })
                Text("Only installed calling-app candidates appear. Declared support can come from a library and is unverified.", color = TextMuted, fontSize = 11.sp)
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        Surface(onClick = { onSelect(app.packageName) }, color = SurfaceCardElevated, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AppRealIcon(app.packageName, app.label, Modifier.size(32.dp))
                                Spacer(Modifier.width(10.dp))
                                Column { Text(app.label, color = TextWhite);Text(app.reason.label, color = TextMuted, fontSize = 11.sp) }
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) Text("No matching calling apps detected.", color = TextMuted)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
