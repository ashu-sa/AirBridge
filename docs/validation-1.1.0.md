# AirBridge 1.1.0 development validation

Tested 27 October 2026. Preview build; do not advertise as fully compatible or battery-certified.

## Changes driven by testing

- Optional Workout and Sleep schedules start off. Up to six windows support weekdays, overlaps and overnight ranges. Start is inclusive and end is exclusive, in the phone time zone. An overnight window belongs to its start day. Schedule controls are collapsed initially, above the FAQ.
- Schedule checks run at receipt, queueing, retry and before the first settings write. Existing writes finish restoration. There is no schedule alarm or polling service.
- BLE stack initialization is lazy. Each bounded pattern releases its peer; repeating calls yield between bursts and release the wake lock during the gap.
- A DTLS retry stress run exposed a native-cleanup hang. Disposal moved off native callback threads, has a bounded wait, and blocks further sessions if cleanup fails.
- A synchronous recovery checkpoint is saved before changing haptic intensity. Only a verified readback clears it. Uncertain restoration blocks automatic alerts until manual recovery.
- Notification queues are bounded and stale notifications expire. Protocol parsing rejects overflowing lengths. Debug builds log delivery metadata, not message contents.
- Opening the companion app through AirBridge pauses alerts for five minutes. This is a mitigation, not proof that simultaneous sync cannot be interrupted.

## Evidence

| Check | Result and scope |
| --- | --- |
| Unit tests | 42 pass: schedule boundaries including a full week, DST/time-zone cases, protocol faults including 10,000 randomized malformed payloads, write/restore/readback failures, simulated process disposal and recovery, queue-admission stress, native-disposal threading. |
| Notification storm model | 100,000 simulated events; tests admission and bounded timing logic. These are not 100,000 physical-band alerts. |
| Build and lint | Debug app and instrumentation APKs build. Lint: zero errors, 110 warnings in the recorded run; some warnings concern changed code. |
| Emulator persistence | 10 schedule integration cases pass, including damaged/missing configuration failing closed and five-minute sync pause expiry. |
| Emulator Doze | A real notification from the separate sender is blocked outside the schedule while forced into deep idle; no BLE session starts. Device idle and simulated charging state were restored afterward. This does not establish in-window Doze delivery on Samsung. |
| Permission and radio faults | Opt-in emulator checks reject missing Nearby devices permission and disabled Bluetooth before native BLE initialization. |
| Real band patterns | Single, Double, Triple, Long Gap and Urgent complete and read back original LOW intensity. Each bounded session releases the peer. |
| Repeating call model | One burst releases the peer; closing the schedule stops repetition. |
| Recovery on band | A seeded checkpoint blocks an automatic alert; explicit manual recovery restores and verifies the setting. Actual forced process death during a physical write has not been tested. |
| Call receiver path | A simulated PHONE_STATE ringing event drives the automatic Continuous ring path on the physical band. Simulated answer stops it. This bypasses Android carrier broadcast delivery and is not a real incoming-call test. A secure-session retry occurred before delivery. |
| Controlled notification | A separate, shell-protected sender APK posts a fixed notification using its own UID. On the phone, it is blocked outside the schedule without opening BLE and succeeds inside a window, restoring LOW and releasing the peer. Earlier shell/test-APK sender attempts failed and are not counted as passing evidence. |

Physical setup: Samsung SM-M346B, Android 16/API 36, selected Google Fitbit Air. This is one phone and band, not a firmware compatibility matrix. No pairing or companion data was deleted.

## Unresolved checks

**Companion sync:** A clean companion-only baseline displayed “Synced just now”. Concurrent alert/sync attempts did not consistently establish a fresh completed sync. The companion screen also caches relative timestamps. Uninterrupted sync is not certified. Keep this as a release blocker; releasing peers and adding a sync pause reduce contention but do not prove its absence.

**Real calls:** An actual carrier incoming call, locked-screen delivery, dual-SIM transitions and VoIP calls still need checks. Android phone-state handling is not WhatsApp/Telegram call support. A short ring may end while the secure session is connecting; cancellation then suppresses a late pulse.

**Battery:** The physical phone was charging over USB. No app-only mAh, daily percentage or “less than 1%” claim is supported. An earlier idle observation used 0.33 CPU seconds over 261.6 seconds; CPU time is not battery drain. Mixed band stress observations are not an attribution study. Measure unplugged idle and comparable alert workloads, with a matched AirBridge-off baseline, before estimating consumption.

**Background delivery:** OEM restrictions and firmware changes remain risks. No device reset, unpairing, private message export or health-data publication was used.

## Reproduction

Use JDK 17 and Android SDK 34. Ordinary checks:

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
```

Hardware tests require a configured band and explicit opt-in (`-e hardware true`). They temporarily alter alert settings and restore a durable preference backup; leave the phone untouched during them. Synthetic notification tests require the separate sender:

```sh
./gradlew -PnotificationFixture=true :notification-fixture:assembleDebug
adb install -r notification-fixture/build/outputs/apk/debug/notification-fixture-debug.apk
adb shell pm grant com.airbridge.fixture android.permission.POST_NOTIFICATIONS
```

The sender is excluded from normal app builds and is never bundled in the distributed APK. Remove both test APKs after physical testing. Raw phone logs and companion screens are private and are not included in this repository.
