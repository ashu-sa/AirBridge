# AirBridge 1.3.2 validation

Both Android application labels and dashboard titles are **Air Bridge**. Edition package IDs and feature separation are unchanged; download filenames still identify Calls and Full.

The band name has a small accessible status icon and text: green Connected during an active AirBridge endpoint session, amber Action required for missing Bluetooth/setup or pending recovery, red Connection failed after an unsuccessful attempt, a progress icon while working, and neutral Ready when idle. Past successful checks do not imply a permanent connection. The connection flow clears the live flag when releasing its peer. Bluetooth state changes update the dashboard without a scan or polling loop.

## Completed checks

- 95 release unit tests pass per edition. Four new status tests cover stale success, prerequisite/recovery priority, active reconnection after a failure, retry progress and final failure.
- All 11 emulator regression tests pass, including both installed-edition contracts now asserting the Air Bridge label, three app-discovery checks and six app-call integration checks.
- Both release/debug and instrumentation variants build. Lint: Full zero errors / 51 warnings; Calls zero errors / 52 warnings.
- Release APKs: non-debug 1.3.2 / code 11, same release certificate as 1.3.1, application label Air Bridge in both; notification-listener declaration and implementation present only in Full.
- Updated the physical phone without resetting settings. UI inspection confirms Air Bridge, saved Google Fitbit Air, the neutral Bluetooth status icon and Ready · connects for alerts. No vibration was triggered for this UI check.
- Refreshed both README dashboard screenshots from the emulator. No private phone content is published.

## Limits

Connected and failed icon rendering is covered by state logic/build checks; no real-band failure was deliberately induced for this release. The preceding 1.3.1 read-only physical connection check passed. Real call vibration, unplugged battery drain, uninterrupted Fitbit sync and universal Play Protect acceptance remain unverified. This update does not change notification processing, permissions or installation protection settings.
