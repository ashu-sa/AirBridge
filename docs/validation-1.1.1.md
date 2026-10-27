# 1.1.1 call-detection follow-up

27 October 2026. This follows a user report that regular SIM calls did not vibrate on the physical phone. The available app logs contained successful notification deliveries but no ringing event from the reported attempt. That points toward event detection, but it does not establish the root cause: a retained log may be incomplete.

## Change

The manifest PHONE_STATE receiver remains. While the app process is alive and call alerts are enabled, AirBridge also registers an event-driven call-state observer for active SIM subscriptions. It uses TelephonyCallback.CallStateListener on Android 12+ and PhoneStateListener on older supported releases, with the existing READ_PHONE_STATE permission. Subscription changes rebuild the observers. Turning off call alerts unregisters them. No call-log permission, phone numbers, contact access, polling loop, new foreground service or persistent BLE connection is introduced.

SIM states are combined so an idle SIM does not cancel another ringing SIM. Both observation paths share the same schedule, recovery and alert checks. Repeated ringing events use the service's existing call-session guard. The incoming-call card displays the most recent detected ringing timestamp, stored locally without a phone number.

The callback is a fallback while Android keeps the app process alive, not a guarantee that OEM background restrictions cannot stop it. The manifest receiver remains necessary for cold starts.

API reference: [Android call-state callback](https://developer.android.com/reference/android/telephony/TelephonyCallback.CallStateListener).

## Evidence and limits

- 49 unit tests pass, including seven call-state aggregation cases: startup state, ringing/answer/idle, simultaneous ringing SIMs, unknown/invalid events, and failed observer registration.
- Debug and instrumentation APKs build; lint completes with zero errors and 110 warnings.
- An emulator-generated GSM incoming call (not a direct invocation of the receiver) produces callback states RINGING, OFFHOOK and IDLE. The manifest broadcast is also observed. With scheduling enabled outside its windows, the call is recorded and blocked before BLE starts.
- After USB reconnection, 1.1.1 was installed in place on the Samsung. Both SIM callbacks registered and the phone reported its active-call state. A pending recovery checkpoint for original LOW intensity was found; this definitively blocked automatic alerts at that point. The earlier missing ringing log remains inconclusive as a cause of the original report.
- The manual recovery action restored and read back LOW, completed a single-pulse protocol transaction, cleared the checkpoint and released BLE. A software transaction does not prove a felt motor vibration. User confirmation and a new real incoming call after recovery are still required; this report does not claim the real-call failure is fixed.
- Earlier physical-band evidence belongs to preview 1.1.0. Uninterrupted companion sync and unplugged battery drain remain unresolved as described in the earlier validation report.
