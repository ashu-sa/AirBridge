# AirBridge 1.0.1 development review

Review date: 27 October 2026.

## Changes

| Problem found | Change |
| --- | --- |
| Dashboard inferred a live connection from permissions alone | Band selection is labelled Saved; delivery appears separately under Latest activity. |
| Any nearby Fitbit-like candidate could replace the saved device during an alert | Background delivery only targets the selected address. Device changes require explicit selection. |
| Generic devices were easy to select as a band | Scanner lists devices advertising a Fitbit name. Manual setup remains available for known addresses, with address validation. A name is not a compatibility guarantee. |
| Exported phone receiver accepted custom test and stop commands | Removed custom action filters and handlers. The receiver only handles the protected Android phone-state action. |
| Notification deduplication extracted title and message text | Deduplication uses notification keys and timestamps. Cooldowns are committed only after a service start is accepted. |
| Failed restoration readback was converted to success | Failed or mismatched readback now reports an error. Uncertain restoration does not retry the entire vibration pattern. |
| Stop/reset could disconnect during restoration | In-flight work finishes before connection cleanup. Call-repeat callbacks are cancelled separately from scan timeouts. |
| Manual call tests skipped retries; secure-session errors appeared only as generic timeouts | Bounded retries also cover manual tests. DTLS errors propagate to the service with an actionable message. Permission and restoration errors are not retried. |
| Permission prompts requested unnecessary call-log and modern-Android location access | Removed the call-log request; location is limited to Android 11 and earlier. Launcher queries replace QUERY_ALL_PACKAGES. |
| Release builds used the debug signing key and verbose logs | Release builds require explicit private signing; verbose runtime logs are debug-only. |
| Interface included unsupported battery, banking and sync guarantees | Replaced those claims, simplified connection actions, added a direct band test and refreshed typography, accents and touch targets. |

## Validation

- `./gradlew testDebugUnitTest assembleDebug lintDebug` passes with JDK 17+ and Android SDK 34 available.
- 14 unit tests pass: four existing rule-parser tests, five notification-gate tests, and five retry-policy tests.
- Android lint: zero errors, 107 warnings. No lint baseline or global suppression was added.
- Updated an existing installation on Samsung SM-M346B, Android 16 / API 36, using the matching signing key without uninstalling it.
- Single-pulse test on the updated app: secure connection established, one group completed, final intensity read back as LOW; foreground alert service ended afterward.
- Reset during a manual Continuous ring test: the app reported stopping, completed three groups, read back LOW, then reported reset and ended the foreground alert service.
- An explicit old TEST_PULSE broadcast did not start an alert or change the latest successful status on the updated app.
- Emulator manual setup: an invalid Bluetooth address remained in the dialog, was marked as an error, and was not saved.
- Inspected dashboard, pattern selector, permissions and FAQ screens on the physical phone.

An earlier connection test encountered repeated DTLS errors and a timeout. A comparison test with the original installed APK succeeded, as did the updated build's later single-pulse and reset tests. The underlying intermittent handshake condition has not been established; retry/error handling was improved, but it is not evidence that all connection failures are resolved.

The original installed APK used a hard-coded default band address when no address was saved. The repository source did not contain that fallback. The original band was restored as an explicit local selection during testing; no personal band address has been added to source control.

## Remaining validation before a public production release

- Real incoming calls, answer/reject transitions, rapid notification bursts, phone lock/Doze, permission revocation and long-running background delivery.
- Interrupted restoration on actual hardware, companion-app syncing and additional device/firmware combinations.
- Reproducible battery and latency measurements.
- Native-library 16 KB compatibility and an appropriate current target SDK if distributing through an app store.
- Private release signing and review of third-party AAR distribution licenses. The preview APK is debug-signed for testing.

Call alerts remain enabled on the test phone. App notification alerts were already paused and are still paused; enable Priority Apps in the app to receive them.
