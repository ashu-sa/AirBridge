# 1.2.0 app-call alerts

Development preview, 27 October 2026. Adds an optional App calls card below SIM call controls. The master switch starts off, with no apps selected. Shortcuts select WhatsApp, Slack, Google Meet (current/original packages) and Zoom. Other installed launcher apps can be selected independently of the five message rules. App calls use notification access and do not require the SIM-call switch or message-alert master to be enabled.

## Behavior and limits

- Android/AndroidX incoming CallStyle metadata is recognized; explicit ongoing, screening and unknown call types are rejected. A conservative legacy fallback requires the call category, multiple actions and full-screen or insistent ringing metadata, with no chronometer. Group summaries are excluded.
- Detection uses metadata only. No caller name, phone number, person object, notification body or action label is read or stored by the detection code. A selected app is not a certified compatibility entry.
- One bounded pattern is attempted per incoming notification phase. Repeated updates are suppressed. Answer/ongoing transitions and notification removal invalidate queued tokens; reusing an Android notification ID creates a new generation, so old queued work cannot revive.
- Incoming app calls are queued ahead of ordinary messages. Message coalescing cannot remove their queued alert. Once settings writes start, an in-flight burst finishes restoration before stopping.
- Existing schedule, five-minute sync pause, BLE permissions, restoration recovery and bounded retry checks still apply. Disabling app calls or removing an app prevents new writes. No polling service, persistent BLE link or extra production runtime permission is added.
- A notification may be missing, ambiguous, delayed or filtered by Android. Slack huddles, Meet/Zoom invitations, outgoing calls and app-version differences need live checks. Ordinary meeting reminders are not incoming-call notifications. An incoming legacy notification with ambiguous metadata is not guessed from its text.

## Validation

- 59 unit tests pass, including 10 new cases covering classification, ordinary messages/meeting reminders, ongoing/screening calls, 100,000 duplicate updates, expiry, bounded storage, reused IDs and package-scoped tokens.
- Six emulator integration tests pass with actual notifications from a separate sender APK: incoming CallStyle routing independently of SIM/message switches; suppression of repeated updates and ongoing calls; feature/selection/schedule/sync-pause gates; legacy notification lifecycle; settings round-trip and damaged selection failing closed; and an ended call queued to the service being cancelled before BLE initialization.
- Most routing checks deliberately deny BLE permission in the emulator so no band is actuated. The ended-call check grants it and verifies that cancellation still prevents BLE initialization. The permission is restored after the suite.
- Debug and instrumentation APKs build. Android lint: zero errors, 110 warnings. The app-call UI and installed-app picker are visually checked on the emulator; screenshot is an emulator capture.
- Preview 1.2.0 is installed in place on the Samsung SM-M346B. App calls start off; SIM settings, selected band and schedules are preserved. No test sender or test instrumentation APK was installed on the physical phone for this feature.

Earlier sender attempts were rejected by Android 16's CallStyle posting rules; the separate fixture was corrected and the final six-test run passed. Its full-screen-intent permission is test-fixture-only and is not included in AirBridge's production manifest. The fixture is excluded from ordinary builds.

**Still pending:** live calls from WhatsApp, Slack, Meet and Zoom, a felt band vibration after charging, the real SIM-call retest, uninterrupted companion sync and unplugged battery measurement. The emulator routing checks are not evidence that all third-party call types work or that Fitbit sync cannot be interrupted.

Android references: [CallStyle notifications](https://developer.android.com/develop/ui/compose/notifications/call-style), [CallStyle metadata in AOSP](https://android.googlesource.com/platform/frameworks/base/+/android16-qpr2-release/core/java/android/app/Notification.java).
