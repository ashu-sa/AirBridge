# AirBridge 1.3.1 validation

## Bluetooth changes

Discovery lists visible BLE advertisements, discoverable classic Bluetooth, paired devices and devices already connected over GATT. Classic-only devices are shown but cannot be selected for BLE alerts. Paired-only entries explicitly have unknown range. Devices that are off or undiscoverable cannot be guaranteed to appear.

Scanning is user initiated, bounded to 20 seconds, and stops when the dialog closes or the app pauses. Classic discovery starts after a five-second BLE head start. Selection stops discovery before checking the band.

A live saved band is selected automatically. First setup selects only one uniquely identified Fitbit Air after discovery completes; multiple bands require a choice. A missing saved band is never silently replaced by a different nearby band. Cached pairing alone does not trigger automatic connection.

Selection runs a read-only connection check, then releases the peer. It does not send a haptic write, restore an old intensity checkpoint or keep a permanent connection.

## Completed checks

- 91 release unit tests pass in each edition, including 13 discovery policy tests and three read-only connection tests.
- Release/debug APKs and both instrumentation APKs build successfully.
- Lint: Full zero errors / 51 warnings; Calls zero errors / 52 warnings.
- All 11 emulator instrumentation tests pass: both edition contracts, three installed-app discovery checks and six app-call routing/cancellation/settings regression checks using a separate notification sender.
- Physical Samsung Android 16 phone: Full updated without uninstalling or resetting settings. Discovery displayed nearby BLE devices and the paired Air. With connected-GATT discovery added, opening the picker automatically selected the saved Google Fitbit Air and completed the read-only check. The dashboard reported: “Band connection verified; no vibration sent. Alerts reconnect on demand.” No test pulse was sent for this verification.
- Both release APKs are non-debug version 1.3.1 / code 10 and verify with the same private release certificate as 1.3.0. Calls contains neither the notification-listener declaration nor its implementation class; Full contains both. Neither manifest declares SMS-reading or an accessibility service.
- Removed an unused legacy XML dashboard containing inaccurate privacy claims; the app uses the Compose dashboard.

## Documentation and privacy

README, installation guide and in-app FAQ describe optional temporary Play Protect scanning controls, immediate restoration and scanning, and the Calls alternative. No device protection setting was changed during testing. Global FAQ wording replaces region-specific banking-app examples.

Notification processing is local and ignores message text, caller names and OTP text. No AirBridge cloud backend or analytics is used. Both APKs still declare Internet permission for the bundled connection stack; offline processing is not a technical network ban or a safety certification.

## Limits

This verifies discovery and a real read-only band connection, not end-to-end physical call vibration. Real SIM/app-call vibration, unplugged battery drain and uninterrupted Fitbit sync remain unverified. Browser installation and Play Protect acceptance on every phone are not established. No appeal was submitted. Optional installation instructions do not guarantee acceptance after protection resumes.
