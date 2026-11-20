package com.vibebridge.fitbit.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.vibebridge.fitbit.MainActivity
import org.json.JSONObject

/** Acknowledgement is delivery evidence, not proof that the wearer felt a vibration. */
data class CallDeliveryState(val confirmed: Boolean = false, val ended: Boolean = false, val pending: Boolean = true) {
    val needsNotice: Boolean get() = ended && !pending && !confirmed
}

/** Local, durable per-call state; never reads numbers, contacts or call logs. */
object CallAlertDelivery {
    private fun prefs(context: Context) = context.getSharedPreferences("air_bridge_call_delivery", Context.MODE_PRIVATE)
    private fun read(context: Context): MutableMap<Long, CallDeliveryState> = runCatching {
        val json = JSONObject(prefs(context).getString("sessions", "{}")!!)
        json.keys().asSequence().associate { key ->
            val item = json.getJSONObject(key)
            key.toLong() to CallDeliveryState(item.optBoolean("confirmed"), item.optBoolean("ended"), item.optBoolean("pending", true))
        }.toMutableMap()
    }.getOrDefault(linkedMapOf())
    private fun save(context: Context, sessions: Map<Long, CallDeliveryState>) {
        val json = JSONObject()
        sessions.forEach { (id, state) -> json.put(id.toString(), JSONObject()
            .put("confirmed", state.confirmed).put("ended", state.ended).put("pending", state.pending)) }
        check(prefs(context).edit().putString("sessions", json.toString()).commit())
    }

    @Synchronized fun begin(context: Context): Long {
        val sessions = read(context)
        val current = prefs(context).getLong("current", 0)
        sessions[current]?.takeIf { !it.ended }?.let { existing ->
            sessions[current] = existing.copy(pending = true)
            save(context, sessions)
            return current
        }
        val id = maxOf(System.currentTimeMillis(), current + 1)
        sessions[id] = CallDeliveryState()
        while (sessions.size > 16) sessions.remove(sessions.keys.minOrNull())
        save(context, sessions)
        prefs(context).edit().putLong("current", id).commit()
        return id
    }

    @Synchronized fun confirmed(context: Context, id: Long) = update(context, id) { it.copy(confirmed = true) }
    @Synchronized fun complete(context: Context, id: Long) = update(context, id) { it.copy(pending = false) }
    @Synchronized fun skip(context: Context, id: Long) {
        val sessions = read(context); sessions.remove(id); save(context, sessions)
    }
    @Synchronized fun ended(context: Context, workRunning: Boolean) {
        val id = prefs(context).getLong("current", 0)
        update(context, id) { it.copy(ended = true, pending = it.pending && workRunning) }
    }

    private fun update(context: Context, id: Long, change: (CallDeliveryState) -> CallDeliveryState) {
        if (id == 0L) return
        val sessions = read(context)
        val current = sessions[id] ?: return
        val next = change(current)
        if (next.ended && !next.pending) sessions.remove(id) else sessions[id] = next
        save(context, sessions)
        if (next.needsNotice) postNotice(context)
    }

    private fun postNotice(context: Context) {
        val text = "Air Bridge couldn't confirm a band vibration for your call. The band may have lost its connection. Open the app to check or reset the link."
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            com.vibebridge.fitbit.ble.AirBridgeDeviceSettings.saveLastStatus(context, text)
            return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(NotificationChannel("air_bridge_missed_call_alert", "Missed band alerts", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 24002, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val reset = PendingIntent.getActivity(context, 24003, Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_RESET_LINK, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notice = NotificationCompat.Builder(context, "air_bridge_missed_call_alert")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Call vibration wasn't confirmed")
            .setContentText("The band may have lost its connection. Check or reset the link.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true)
            .addAction(0, "Reset link", reset).build()
        runCatching { manager.notify(24002, notice) }
    }
}
