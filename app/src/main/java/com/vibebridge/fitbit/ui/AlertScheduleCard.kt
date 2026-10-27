package com.vibebridge.fitbit.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vibebridge.fitbit.schedule.AlertSchedule
import com.vibebridge.fitbit.schedule.AlertWindow
import com.vibebridge.fitbit.ui.theme.*
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private fun timeLabel(minute: Int) = String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

@Composable
fun AlertScheduleCard(schedule: AlertSchedule, onChange: (AlertSchedule) -> Unit) {
    var editing by remember { mutableStateOf<AlertWindow?>(null) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, SurfaceCardStroke),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, contentDescription = null, tint = CyberTeal)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Text("Alert schedule", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (schedule.enabled) "Only during your chosen windows" else "Off • Alerts can arrive at any time",
                        color = TextMuted, fontSize = 12.sp)
                }
                Switch(checked = schedule.enabled, onCheckedChange = { onChange(schedule.copy(enabled = it)) },
                    modifier = Modifier.semantics { contentDescription = "Enable alert schedule" })
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse alert schedule" else "Expand alert schedule", tint = TextMuted)
                }
            }
            AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose when AirBridge may vibrate your band. Outside these windows it won't start an alert connection.",
                color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp)
            schedule.windows.forEach { window ->
                Surface(shape = RoundedCornerShape(14.dp), color = SurfaceCardElevated) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(window.label, color = TextWhite, fontWeight = FontWeight.Medium)
                            Text("${timeLabel(window.startMinute)} – ${timeLabel(window.endMinute)}${if (window.endMinute < window.startMinute) " (+1 day)" else ""}",
                                color = CyberTeal, fontSize = 13.sp)
                            Text(if (window.days.size == 7) "Every day" else window.days.sorted().joinToString(" · ") {
                                DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                                color = TextMuted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { editing = window }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Edit") }
                        Switch(checked = window.enabled, onCheckedChange = { enabled ->
                            onChange(schedule.copy(windows = schedule.windows.map { if (it.id == window.id) it.copy(enabled = enabled) else it }))
                        }, modifier = Modifier.semantics { contentDescription = "Enable ${window.label} window" })
                    }
                }
            }
            if (schedule.windows.size < AlertSchedule.MAX_WINDOWS) {
                TextButton(onClick = { editing = AlertWindow(UUID.randomUUID().toString(), "Custom", 9 * 60, 10 * 60) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add a window") }
            }
            if (schedule.enabled && schedule.windows.none { it.enabled }) {
                Text("No active windows. All alerts are paused.", color = AmberText, fontSize = 12.sp)
            }
            Text("Phone time zone: ${ZoneId.systemDefault().id}. Overnight windows use the day they start. An alert already in progress may finish to restore the band's setting.",
                color = TextMuted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            }
        }
    }
    editing?.let { window ->
        ScheduleWindowEditor(window, onDismiss = { editing = null }, onSave = { updated ->
            val exists = schedule.windows.any { it.id == updated.id }
            onChange(schedule.copy(windows = if (exists) schedule.windows.map { if (it.id == updated.id) updated else it }
                else schedule.windows + updated))
            editing = null
        }, onDelete = {
            onChange(schedule.copy(windows = schedule.windows.filterNot { it.id == window.id }))
            editing = null
        })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleWindowEditor(window: AlertWindow, onDismiss: () -> Unit,
                                 onSave: (AlertWindow) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var label by remember(window.id) { mutableStateOf(window.label) }
    var start by remember(window.id) { mutableStateOf(window.startMinute) }
    var end by remember(window.id) { mutableStateOf(window.endMinute) }
    var days by remember(window.id) { mutableStateOf(window.days) }
    val candidate = window.copy(label = label.trim(), startMinute = start, endMinute = end, days = days)
    AlertDialog(onDismissRequest = onDismiss, containerColor = SurfaceCard,
        title = { Text("Edit alert window") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it.take(40) }, label = { Text("Window name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> start = h * 60 + m }, start / 60, start % 60, true).show()
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("From ${timeLabel(start)}") }
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> end = h * 60 + m }, end / 60, end % 60, true).show()
                    }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("To ${timeLabel(end)}") }
                }
                Text("Start days", color = TextMuted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..7).forEach { day ->
                        val name = DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(name) }, modifier = Modifier.semantics { contentDescription = name })
                    }
                }
                if (end < start) Text("Ends on the following day.", color = CyberTeal)
                if (!candidate.isValid()) Text("Choose a name, at least one day and different start/end times.", color = AmberText)
                TextButton(onClick = onDelete) { Text("Remove window", color = CrimsonRed) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(candidate) }, enabled = candidate.isValid()) { Text("Save window") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
