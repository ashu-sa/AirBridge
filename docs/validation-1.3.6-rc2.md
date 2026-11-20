# 1.3.6-rc2: reliability candidate

Published as a GitHub prerelease, not a stable release. Google Health coexistence is still unresolved.

## Changes

- Await the SDK's Rx disconnect transaction before disposing the peer and closing this session's GATT clients. Track connections allocated before the secure handshake completes.
- Cache in-flight cleanup and wait for it before stopping or retrying. A process-wide barrier prevents a replacement service from initializing BLE during earlier native disposal. A cleanup timeout fails closed until the process is restarted.
- Keep disposal off native/Bluetooth callback threads, including asynchronous disconnect completion. Remove only session-owned map entries; avoid blanket connection closure on the main thread.
- Stop treating a Bluetooth disconnect as a band tap that silences a call. No double-tap feature is claimed.
- Try a saved device address directly on the first attempt; reserve the former eight-second discovery scan for retries when the address is not already bonded or connected. This removes a redundant delay, not the secure handshake.
- Add optional Android Companion Device Manager association for the selected band, using the narrow background foreground-service permission. System consent is required. This does not create a permanent connection, change the Bluetooth bond, or remove Google Health's association. Access can be removed in the app.
- Distinguish actual status 133, generic GATT errors, permission failures, timeouts and uncertain haptics restoration. Use actionable background-start errors.
- Correct README/FAQ and historical validation wording: closing our client is not proof of recovering Google Health's secure session.

## Verification

- 114 unit tests pass per edition. Release lint passes with zero errors.
- Both candidate editions use the existing private release certificate; non-debug APKs, ARM64, approximately 4 MB each.
- Emulator edition/companion-setup checks passed for both editions (four tests).
- All 26 targeted emulator checks passed with no skipped tests: eight after-call notice checks plus edition/companion checks for each edition (20), and six Full app-call regression checks. They include actual notification posting, the Bluetooth-permission rejection path through the real SIM-call handler, late acknowledgements, duplicate callbacks, process restart, intentional skip and overlapping records. Notification-posting assertions wait for Android’s asynchronous notification queue.
- Physical RC1 baseline: Google Health displayed error 3000 while Air Bridge was stopped. Restarting Google Health restored “Synced just now”. One subsequent RC1 pulse completed with the original HIGH setting restored; Google Health subsequently synced again. An extra registered GATT client remained, so RC2 additionally avoids replacing an SDK client during registration. A registration alone is not evidence of an active connection.
- Both signed RC1 and RC2 were installed without resetting settings. Android companion association was approved and persisted across the update. RC2 real-call and post-alert coexistence checks remain pending.

## After-call notice

Regular SIM calls can show an after-call phone notification if no haptic write was acknowledged. It waits for in-flight delivery to finish, has a Reset link action, and requires permission to show notifications. It does not read numbers or contacts. Acknowledgement is protocol evidence, not proof that the wearer felt a vibration. Disabled alerts and scheduling exclusions do not start a tracked alert. Manual tests and app-call alerts do not currently use this notice.

## Open issues

- Repeated reports of Google Health sync and alarm/vibration settings failures remain. The cited protocol investigation describes a stale secure session even after a bridge closes its own client: https://github.com/paceaitian/fitbit-air-notification-bridge/blob/agent/add-dncs-coexistence-probe/docs/OPEN_PROBLEMS.md
- The native `libgg.so` FD_SET abort observed on the Samsung phone is not fixed by these Kotlin changes. The bundled native library is unchanged.
- Physical background delivery, measured first-pulse latency and long-term coexistence must be checked after installing the candidate and consenting to companion setup. No instant delivery or daily battery result is claimed.
