# AirBridge 1.3.0 editions validation

## Edition separation

- **Calls:** `com.vibebridge.fitbit.calls`, label AirBridge Calls. No notification-listener declaration or implementation class in the release APK. Launcher-app discovery queries are replaced by a scoped Fitbit companion query. App calls, Priority Apps and notification-access UI are absent. Automatic app-alert triggers are also gated off in the service.
- **Full:** `com.vibebridge.fitbit`, label AirBridge Full. Retains the listener, app-call controls and priority-notification rules, and can update private-key-signed 1.2.2.
- Both include SIM-call detection, schedules, manual vibration tests and recovery. Separate app data; only enable one edition's alerts for a given band at a time.
- Both are non-debug builds, versionCode 9, signed with the existing private release certificate. No new signing key was created.

## Completed checks

- 75 release unit tests pass for each edition.
- Both release/debug APKs and variant-specific instrumentation APKs build successfully.
- Lint: Full zero errors / 109 warnings; Calls zero errors / 110 warnings.
- Installed Calls contract test passes: listener component absent, listener implementation class absent, phone-state permission and call receiver present, and BLE foreground-service type correct.
- Installed Full contract plus three discovery checks pass (four tests).
- Full app-call regression suite passes all six tests using a separate notification sender: routing, deduplication, message/ongoing exclusion, settings/schedule/sync gates, cancellation/removal and stopping an ended call before BLE starts.
- Emulator-generated GSM ringing is recorded in Calls' call diagnostics; the emulator call was then cancelled. This verifies phone-state detection, not physical band vibration.
- UI inspection: Calls title/SIM controls present; App calls, Priority Apps and notification-access chip absent. Full title and app-call controls retained. Dashboard screenshots are emulator captures; the unchanged app-call row/schedule examples are connected-phone captures from the preceding release.
- Release APK checks verify signatures match 1.2.2, correct package IDs/version, no debug flag, and notification-listener declaration and DEX class present only in Full. No SMS-read/receive or Accessibility Service permission was found in either packaged manifest.

AGP 8.3 reports a false positive for the common foreground-service declaration while checking flavor manifests. A suppression is limited to service onCreate; the type is explicitly passed through ServiceCompat and verified in the packaged manifests and installed-edition tests. No global lint baseline or error suppression was added.

## Limits

The phone that displayed the Play Protect block was disconnected. Calls removes the reported notification-listener trigger, but successful browser installation on that phone and universal Play Protect acceptance are not established. No appeal was submitted. The release does not disable device verification or conceal permissions.

Real SIM/app-call vibration, unplugged battery drain and uninterrupted Fitbit sync remain unverified. No physical phone was uninstalled or reset for this release.
