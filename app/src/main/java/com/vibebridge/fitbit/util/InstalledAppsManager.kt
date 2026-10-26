package com.vibebridge.fitbit.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import timber.log.Timber

data class DeviceApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)

object InstalledAppsManager {
    private var cachedApps: List<DeviceApp>? = null

    fun getInstalledLauncherApps(context: Context, forceRefresh: Boolean = false): List<DeviceApp> {
        if (!forceRefresh && cachedApps != null) {
            return cachedApps!!
        }

        return try {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val apps = mutableListOf<DeviceApp>()
            val seen = mutableSetOf<String>()

            for (info in resolveInfos) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName) continue
                if (seen.add(pkg)) {
                    val label = runCatching { info.loadLabel(pm).toString() }.getOrNull() ?: pkg
                    val icon = runCatching { info.loadIcon(pm) }.getOrNull()
                    apps.add(DeviceApp(pkg, label, icon))
                }
            }

            // Also ensure priority apps like WhatsApp, Telegram, Gmail, Messages, Instagram are present if installed
            val essentialPackages = listOf(
                "com.whatsapp",
                "org.telegram.messenger",
                "com.google.android.apps.messaging",
                "com.samsung.android.messaging",
                "com.instagram.android",
                "com.google.android.gm"
            )
            for (pkg in essentialPackages) {
                if (seen.add(pkg)) {
                    runCatching {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val label = pm.getApplicationLabel(appInfo).toString()
                        val icon = pm.getApplicationIcon(appInfo)
                        apps.add(DeviceApp(pkg, label, icon))
                    }
                }
            }

            val sorted = apps.sortedBy { it.appName.lowercase() }
            cachedApps = sorted
            sorted
        } catch (e: Exception) {
            Timber.e(e, "Error loading installed apps")
            emptyList()
        }
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }

    fun getAppName(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            when (packageName.lowercase()) {
                "com.whatsapp" -> "WhatsApp"
                "org.telegram.messenger" -> "Telegram"
                "com.google.android.apps.messaging" -> "Google Messages"
                "com.samsung.android.messaging" -> "Samsung Messages"
                "com.instagram.android" -> "Instagram"
                "com.google.android.gm" -> "Gmail"
                "com.slack" -> "Slack"
                "com.discord" -> "Discord"
                "org.thoughtcrime.securesms" -> "Signal"
                "com.microsoft.teams" -> "Teams"
                "com.microsoft.office.outlook" -> "Outlook"
                "com.twitter.android" -> "X"
                else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
    }
}
