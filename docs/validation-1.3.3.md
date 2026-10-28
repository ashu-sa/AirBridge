# AirBridge 1.3.3 validation

## SIM-call vibration change

Urgent and Continuous previously used identical three-group patterns, followed by a five-second gap and a new Bluetooth session. They now use distinct cadences within one connection while Android reports ringing:

- **Urgent:** three pulse groups, 400 ms between groups and a 1,000 ms pause between bursts, in addition to each 700 ms toggle/restore delay and protocol response time.
- **Continuous:** repeated single groups with a 350 ms toggle/restore delay and 120 ms gap, plus protocol response time. This is closely spaced feedback, not a direct motor-on command or a promise of uninterrupted motor vibration.

The band protocol changes haptic intensity to trigger feedback; increasing a timer does not establish control over motor duration. Original intensity is restored between groups. A durable checkpoint is kept until the session ends and readback verifies restoration. Cancellation is checked before each group and every 50 ms during delays; a command or physical pulse already in flight can finish. Failed writes attempt restoration; uncertain restoration retains the recovery checkpoint.

The session stops on answer/end, Stop, disabled alerts, schedule/sync pause, or the existing 60-second ringing limit. A session generation prevents a later call from reactivating an earlier queued session. Bluetooth is released afterward. Ordinary notifications and app-call routing remain one-shot. The manual Continuous preview is a single short group, not a simulated ringing call.

There is no artificial five-second wait or reconnection between SIM-call groups. Initial delivery still requires device resolution, Bluetooth/Gattlink/DTLS setup and a settings read; immediate or sub-second first vibration is not established. No permanent idle connection or advance haptic write was added. Keeping a connection during ringing can defer companion-app sync until it is released; uninterrupted sync is not established.

## Completed checks

- 102 release unit tests pass per edition. Seven new ringing tests cover a single-session continuous cadence, distinct urgent bursts, cancellation/restoration, cancellation before writing, duration cap, a lost write response and bounded restoration failure with retained checkpoint.
- All 11 emulator regression tests pass: both edition contracts, three installed-app discovery checks and six app-call integration checks, including stopping an ended app call before BLE starts.
- Both release/debug APKs and instrumentation variants build. Lint: Full zero errors / 51 warnings; Calls zero errors / 52 warnings.
- Release artifacts are non-debug version 1.3.3 / code 12, signed with the existing release certificate and labelled Air Bridge. Calls omits the notification-listener declaration and implementation; Full retains them.

## Physical verification pending

The phone disconnected before this update could be installed. Real incoming-call cadence, perceived first-vibration latency, answer-to-stop time, battery cost and companion-app sync behavior are pending a connected-phone test. Automated timing uses a fake settings client and is not a measured motor or radio benchmark. This release does not claim instant delivery, a continuous motor command or a verified battery/sync improvement.
