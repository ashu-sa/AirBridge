# AirBridge

**Distinct wrist alerts for incoming calls and selected Android app notifications.**

AirBridge is an independent Android project that connects to a Fitbit band over Bluetooth Low Energy (BLE) and triggers vibration patterns for calls and selected notifications. It is designed to help you recognise different alerts without checking your phone.

The current implementation is built around the device identified in the app as **Fitbit Air**. Compatibility with other Fitbit models has not been established. AirBridge is not affiliated with or endorsed by Google or Fitbit.

## Screenshots

<table>
  <tr>
    <td align="center"><img src="screenshots/dashboard_main.png" width="260" alt="AirBridge dashboard showing connection status, incoming call controls and priority apps"><br>Dashboard and alert controls</td>
    <td align="center"><img src="screenshots/pattern_selector.png" width="260" alt="Vibration pattern selector"><br>Vibration pattern selection</td>
  </tr>
  <tr>
    <td align="center"><img src="screenshots/reset_recovery.png" width="260" alt="Connection reset and recovery screen"><br>Connection recovery</td>
    <td align="center"><img src="screenshots/faq_privacy.png" width="260" alt="In-app FAQ and privacy information"><br>In-app information</td>
  </tr>
</table>

Screenshots show the updated Android interface. The Saved badge identifies the chosen band; successful alert delivery is reported separately under Latest activity.

## Features

- **Incoming call alerts:** Choose Urgent or Continuous alert modes.
- **Priority app rules:** Configure up to five app rules with individual vibration patterns.
- **Pattern previews:** Try patterns on the band from the selection dialog, or send a single test vibration directly from the band card.
- **Connection tools:** Use band detection and reset controls when troubleshooting a connection.
- **On-demand BLE sessions:** The service connects to deliver an alert and cleans up the connection afterward, with the aim of reducing interference with the companion app.
- **No Accessibility Service:** The manifest does not request an Accessibility Service, overlay access or screen capture.

Available patterns include Single, Double, Triple, Long Gap and Urgent. Continuous call mode repeats alert bursts within the service's configured limits.

## How it works

```text
Incoming call or selected notification
                  |
                  v
Android receiver / notification listener
                  |
                  v
Foreground BLE service
                  |
                  v
Golden Gate / Gattlink / DTLS connection
                  |
                  v
CoAP /settings requests to change haptic intensity
                  |
                  v
Restore original intensity and clean up the connection
```

The current implementation generates patterns by changing the band's haptic intensity setting and attempting to restore its original value. It does not use a dedicated `/app/haptics/pulse` endpoint. Interrupted connections can affect delivery or restoration; check the band's vibration setting if a session fails.

## Requirements and compatibility

- Android 8.0 or later, as declared by the project's minimum SDK.
- An ARM64 Android device; the current build configuration includes `arm64-v8a` native libraries.
- Bluetooth enabled and a compatible band nearby.
- Notification access for app alerts and phone-state permission for incoming call detection.

The project currently compiles and targets Android API 34. This alone does not establish Android 15 support or 16 KB native page-size compatibility. A verified device and firmware compatibility list is not yet documented.

## Build and setup

Use JDK 17 and an Android SDK installation with API 34. The repository includes local AAR dependencies in `app/libs`.

```bash
git clone https://github.com/ashu-sa/AirBridge.git
cd AirBridge
./gradlew assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

To install it on a connected device with ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open AirBridge, grant the permissions for the features you want to use, detect or select the band, and test a vibration pattern before enabling call and notification alerts. Permission prompts vary by Android version; older versions may require location permission for BLE scanning. Background delivery can also depend on your phone's battery settings.

Check [GitHub Releases](https://github.com/ashu-sa/AirBridge/releases) for published builds. If no APK is available, build from source using the steps above.

## Permissions and privacy

AirBridge uses notification access to process notifications for configured apps and phone-state access to detect ringing. Notification access is sensitive: Android grants the listener broader visibility than the app rules used to filter alerts.

The manifest also declares Bluetooth, foreground-service, wake-lock, notification, network and battery-optimisation permissions. Location permission is limited to Android 11 and earlier for BLE scanning. App discovery is scoped to launcher apps and named companion or default apps rather than requesting visibility of every installed package. The BLE stack uses network-style communication internally.

The alert path is designed around local phone-to-band communication. A permission list or source review alone is not proof of zero outbound traffic or a security audit. No universal guarantee is made about banking-app compatibility.

## Development validation

Version 1.0.1 has been built and checked with 14 unit tests and Android lint (zero errors; existing warnings remain). On a Samsung SM-M346B running Android 16, a manual single-pulse test completed and the band setting was read back as LOW. The updated app also rejects the old external test-broadcast commands.

This is limited development testing, not a compatibility certification. An earlier secure-session timeout was observed during testing. Long-running background delivery, real incoming calls, battery use and companion-app coexistence still need broader testing. Public releases require a private release signing key; the project no longer signs release builds with the debug key.

## Current limitations

- Alert latency and battery consumption have not been documented with reproducible benchmarks.
- Connection cleanup is intended to support coexistence with the companion app; uninterrupted sync is not guaranteed.
- Background restrictions, firmware changes and Bluetooth interruptions may affect alerts.
- Source availability does not establish compatibility across Fitbit models.

## Feedback

Bug reports and compatibility reports are welcome through [GitHub Issues](https://github.com/ashu-sa/AirBridge/issues). Include your phone model, Android version, band model and firmware, reproduction steps, and expected versus actual behaviour. Remove personal notification content and device identifiers from logs before sharing them.

## License

The existing README declares Apache License 2.0, copyright 2026 Ashutosh Sahoo. See the [Apache 2.0 license terms](https://www.apache.org/licenses/LICENSE-2.0). Bundled third-party libraries remain subject to their respective licenses.
