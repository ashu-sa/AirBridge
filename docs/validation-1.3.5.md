# AirBridge 1.3.5 validation

## Companion App Coexistence & Sync Collision Fix (Error 3000)

Addressed an issue where official Fitbit / Google Health companion syncs threw Error 3000 ("sync interrupted") even when no vibration alerts were in-flight.

### Root Cause
`AirBridgeOneShotService` successfully disconnected its DTLS/CoAP stack after each alert, but its underlying `GattConnection` was retained inside `FitbitGatt.connectionMap`. Android's Bluetooth stack kept `com.vibebridge.fitbit` registered as an active GATT client (`gatt_if`) holding a peripheral connection to the band. When Google Health attempted periodic synchronization, the band rejected the concurrent connection attempt.

### Fix
- In `AirBridgeOneShotService.releasePeerAndWait()`, actively invoke `conn.close()` and clear all entries in `FitbitGatt.connectionMap` immediately upon completing an alert or encountering an error.
- Enhanced `BitGattHelper.removeConnectionForAddress()` to explicitly close target connections before removal.
- This fully unregisters AirBridge from Android's Bluetooth stack (`unregisterClient(clientIf)`), leaving the Fitbit Air's single peripheral link completely open for official companion app syncs.

## APK Size

| Signed release | 1.3.4 bytes | 1.3.5 bytes | Change |
| --- | ---: | ---: | ---: |
| Calls | 3,910,594 | 3,910,594 | Identical |
| Full | 3,943,974 | 3,943,974 | Identical |

Both native libraries in the final APKs remain byte-identical; all 102 release unit tests per edition pass.

## Completed Checks

- **102 release unit tests** pass per edition (`testFullDebugUnitTest` and `testCallsDebugUnitTest`).
- **Physical Hardware Verification (Samsung Galaxy M34 + Fitbit Air):**
  - **Single Pulse Test:** Band pulsed once. Logged `BluetoothGatt.close()` and `unregisterClient(clientIf=16)`. Immediate Google Health pull-to-refresh sync succeeded with 0 errors.
  - **Continuous Call Ring Test:** Band pulsed continuously for 21 pulses. Logged clean teardown on call end with `unregisterClient(clientIf=20)`. Google Health pull-to-refresh sync succeeded with 0 errors.
  - **Consecutive Pulse Test:** Third pulse triggered and completed. Logged `unregisterClient(clientIf=21)`. Navigated to Google Health "Today" dashboard: Steps (2,101), Daily Readiness (87), Sleep score, and Battery level (95%) synced cleanly with zero Error 3000 incidents.
- Both APKs are signed with the official private release certificate (SHA-256: `92804cbff23efd818e9efd0f7262c064537fe2336520c04d3f2ef90b2643c58d`), preserving user settings and data when updating from 1.3.4.
