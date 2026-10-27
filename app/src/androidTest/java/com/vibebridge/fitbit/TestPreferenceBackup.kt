package com.vibebridge.fitbit

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

/** Test-only, durable user-preference backup so a deliberate process kill cannot lose settings. */
internal object TestPreferenceBackup {
    private val names=listOf("air_bridge_device","air_bridge_schedule","air_notification_bridge","air_bridge_call_diagnostics","air_bridge_app_calls")
    private fun store(context:Context)=context.getSharedPreferences("air_bridge_test_backup",Context.MODE_PRIVATE)
    fun saveIfAbsent(context:Context) {
        if(store(context).contains("snapshot"))return
        val root=JSONObject()
        names.forEach { name ->
            val values=JSONObject()
            context.getSharedPreferences(name,Context.MODE_PRIVATE).all.filterKeys { it!="last_status" }.forEach { (key,value) ->
                val type=when(value) { is Boolean->"boolean";is Long->"long";is Int->"int";is String->"string";is Set<*>->"strings";else->error("Unsupported test preference") }
                values.put(key,JSONObject().put("type",type).put("value",if (value is Set<*>) JSONArray(value.toList()) else value))
            }
            root.put(name,values)
        }
        check(store(context).edit().putString("snapshot",root.toString()).commit())
    }
    fun restore(context:Context) {
        val raw=store(context).getString("snapshot",null) ?: return
        val root=JSONObject(raw)
        names.forEach { name ->
            val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
            val status=prefs.getString("last_status",null)
            val values=root.optJSONObject(name) ?: return@forEach
            val edit=prefs.edit().clear()
            values.keys().forEach { key ->
                val item=values.getJSONObject(key)
                when(item.getString("type")) {
                    "boolean"->edit.putBoolean(key,item.getBoolean("value"))
                    "long"->edit.putLong(key,item.getLong("value"))
                    "int"->edit.putInt(key,item.getInt("value"))
                    "string"->edit.putString(key,item.getString("value"))
                    "strings"->{ val a=item.getJSONArray("value");edit.putStringSet(key,(0 until a.length()).map { a.getString(it) }.toSet()) }
                }
            }
            if(status!=null)edit.putString("last_status",status)
            check(edit.commit())
        }
        check(store(context).edit().clear().commit())
    }
}
