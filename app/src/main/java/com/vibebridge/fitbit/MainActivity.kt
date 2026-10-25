package com.vibebridge.fitbit

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import com.vibebridge.fitbit.ui.FitbitDashboardScreen
import com.vibebridge.fitbit.ui.theme.FitbitVibeTheme

class MainActivity : ComponentActivity() {

    private var hasPhonePerm by mutableStateOf(false)
    private var isA11yRunning by mutableStateOf(false)
    private var hasOverlayPerm by mutableStateOf(false)
    private var hasNotificationListenerPerm by mutableStateOf(false)
    private var notificationRulesText by mutableStateOf("")

    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        refreshState()

        setContent {
            FitbitVibeTheme {
                FitbitDashboardScreen(
                    hasPhonePerm = hasPhonePerm,
                    isA11yRunning = isA11yRunning,
                    hasOverlayPerm = hasOverlayPerm,
                    hasNotificationListenerPerm = hasNotificationListenerPerm,
                    rulesText = notificationRulesText,
                    onSaveRulesText = { newText ->
                        val success = AirBridgeNotificationSettings.saveRulesText(this, newText)
                        if (success) {
                            notificationRulesText = AirBridgeNotificationSettings.loadText(this)
                        }
                        success
                    },
                    onRequestPhonePerm = {
                        requestPermissions(
                            arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG),
                            101
                        )
                    },
                    onOpenAccessibility = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onOpenOverlaySettings = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        }
                    },
                    onOpenNotificationListenerSettings = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.vibebridge.fitbit.SERVICE_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(serviceReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(serviceReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(serviceReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    private fun isAccessibilityServiceEnabled(): Boolean {
        if (FitbitVibeAccessibilityService.instance != null) return true
        val expected = "$packageName/${FitbitVibeAccessibilityService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(expected) || enabledServices.contains(packageName)
    }

    private fun refreshState() {
        hasPhonePerm = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        isA11yRunning = isAccessibilityServiceEnabled()
        hasOverlayPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        hasNotificationListenerPerm = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        notificationRulesText = AirBridgeNotificationSettings.loadText(this)
    }
}
