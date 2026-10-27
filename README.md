# AirBridge

**Wrist alerts for calls and the apps you choose.**

AirBridge is an independent Android app for Google Fitbit Air. It sends distinct vibration patterns over Bluetooth so you can recognise an alert without checking your phone.

[Download 1.2.2 APK](https://github.com/ashu-sa/AirBridge/releases/download/v1.2.2/AirBridge-1.2.2.apk) · [Installation options](docs/install.md) · [Report an issue](https://github.com/ashu-sa/AirBridge/issues)

## Features

- **Phone calls:** Urgent or repeating Continuous alerts.
- **App calls:** Choose up to five installed calling apps, including WhatsApp, Instagram and Telegram. Tap an app to replace it.
- **Priority apps:** Up to five notification rules with individual vibration patterns.
- **Schedules:** Editable Workout and Sleep presets, weekdays and overnight windows. Off by default.
- **Connection controls:** Test vibrations, find your band, pause alerts for sync and recover interrupted connections.

## Screenshots

<table>
<tr>
<td><img src="screenshots/dashboard_main.png" width="260" alt="AirBridge dashboard"></td>
<td><img src="screenshots/app_calls.png" width="260" alt="App-call controls"></td>
<td><img src="screenshots/app_calls_collapsed.png" width="260" alt="Collapsed app-call icons"></td>
</tr><tr>
<td><img src="screenshots/alert_schedule.png" width="260" alt="Optional Workout and Sleep schedules"></td>
</tr>
</table>

## Get started

1. Install the APK on an ARM64 phone running Android 8 or newer. If you have a debug preview installed, uninstall it first; its settings will be reset.
2. Allow Bluetooth, notification access and phone permission for the features you want.
3. Select your Fitbit Air and send a test vibration.
4. Choose your call alerts, apps and optional schedule.

**Experimental:** Live call delivery, battery drain and uninterrupted Fitbit sync are still being verified. App-call detection depends on the calling app’s notifications. Other Fitbit models are unverified. See [testing notes](docs/validation-1.2.1.md).

## Privacy

AirBridge's Bluetooth alerts work offline. No AirBridge cloud account, backend or analytics is used. Notification data is not uploaded; the listener does not extract message text, caller names or OTP codes. Settings stay on your phone. Notification access is sensitive, so enable only the features you need. AirBridge is not affiliated with Google or Fitbit.

**Installation options:** Install the APK normally, or use the optional [USB/ADB guide](docs/install.md) if your phone blocks browser downloads. The full app keeps notification and app-call alerts. Phone security policies may still block installation; no universal workaround is promised.

## Build

Use JDK 17 and Android SDK 34:

```bash
git clone https://github.com/ashu-sa/AirBridge.git
cd AirBridge
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## License

Apache 2.0, copyright 2026 Ashutosh Sahoo. Bundled libraries retain their own licenses.
