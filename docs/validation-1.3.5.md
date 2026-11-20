# AirBridge 1.3.5 validation

## Bluetooth cleanup changes; coexistence remains unresolved

Version 1.3.5 added explicit GATT client cleanup. Later user reports still describe sync failures and unavailable alarm/vibration settings. The observations below are historical test reports, not proof of reliable daily coexistence or an official definition of error 3000.

### Original hypothesis (not established as the complete cause)
`AirBridgeOneShotService` successfully disconnected its DTLS/CoAP stack after each alert, but its underlying `GattConnection` was retained inside `FitbitGatt.connectionMap`. Android's Bluetooth stack kept `com.vibebridge.fitbit` registered as an active GATT client (`gatt_if`) holding a peripheral connection to the band. A retained client was suspected of contributing to synchronization failures. A closed client alone does not establish that Google Health’s secure protocol session remains usable.

### Fix
- In `AirBridgeOneShotService.releasePeerAndWait()`, actively invoke `conn.close()` and clear all entries in `FitbitGatt.connectionMap` immediately upon completing an alert or encountering an error.
- Enhanced `BitGattHelper.removeConnectionForAddress()` to explicitly close target connections before removal.
- The intent is to unregister AirBridge’s own client. This does not guarantee restoration of Google Health’s private secure session.

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
  - **Consecutive Pulse Test:** Third pulse triggered and completed. Logged `unregisterClient(clientIf=21)`. The historical check reported updated metrics in Google Health without an observed error 3000. That limited check did not establish long-term compatibility.
- Both APKs are signed with the official private release certificate (SHA-256: `92804cbff23efd818e9efd0f7262c064537fe2336520c04d3f2ef90b2643c58d`), preserving user settings and data when updating from 1.3.4.
