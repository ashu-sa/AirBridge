# AirBridge 1.3.4 validation

## App-call picker

Removed its progress bar and disabled-during-refresh controls. The inventory is discovered in the background while the card is collapsed. Saved package/label/evidence rows are reused after process restarts, then refreshed; expanding the card no longer starts a new scan. No other apps are installed by this feature. A first-ever inventory still requires Android package queries. Discovery does not enable alerts or change selected apps.

## APK size

| Signed release | 1.3.3 bytes | 1.3.4 bytes | Reduction |
| --- | ---: | ---: | ---: |
| Calls | 15,198,754 | 3,910,594 | 74.3% |
| Full | 15,199,186 | 3,943,974 | 74.1% |

R8 code and resource shrinking are enabled. Fitbit JNI/transport classes and members are preserved conservatively. The upstream BitGatt consumer rule that kept every public class is scoped to Fitbit classes; every other AAR entry is byte-identical. See [dependency notes](https://github.com/ashu-sa/AirBridge/blob/main/docs/dependencies.md) for origin, license and the exact change. Both native libraries in the final APKs are byte-identical to 1.3.3; no ABI, feature or native library was removed. Suppressions cover optional CBOR references already absent from the bundled remote-RPC library and compile-time annotation metadata, not JNI errors.

## Completed checks

- 102 release unit tests pass per edition.
- All 12 emulator regression tests pass: both edition contracts, four installed-app discovery/cache checks and six app-call integration checks. The new cache check verifies persistence after in-memory invalidation without changing alert settings.
- Both release/debug and instrumentation variants build. Debug builds are used only for internal tests; published APKs are signed non-debug release builds.
- Lint: Full zero errors / 51 warnings; Calls zero errors / 52 warnings.
- Both final APKs verify with the existing release certificate, label Air Bridge, version 1.3.4 / code 13 and no debug flag. Notification-listener declaration and implementation remain absent in Calls and present in Full.
- Shrunk Full release installed and launched on the emulator. Expanded App calls UI inspected: zero progress bars; change-app controls available.
- Shrunk Calls release updated on the physical Samsung phone without resetting settings. Saved Google Fitbit Air selected automatically; a real native/CoAP read completed with “Band connection verified; no vibration sent. Alerts reconnect on demand.” No vibration was sent for this check.
- In the preceding 1.3.3 phone test, the user confirmed Continuous vibration while ringing and stopping after answering. Perceived first vibration still took 4–5 seconds. This behavior is unchanged in 1.3.4.

## Documentation and limits

README and installation guide now describe optional Install without scanning / Install anyway only when the installer actually offers them, and temporary Play Protect controls with immediate re-enabling and scanning. Some scan dialogs provide no skip option; installation acceptance is not guaranteed. No Play Protect setting was changed during testing.

No feature was intentionally removed. Tests do not establish every calling app's detection behavior, a battery benchmark, uninterrupted companion-app sync or universal Play Protect acceptance. Saved inventory can briefly show prior results while background discovery refreshes after package changes. This update does not promise instant first-ever discovery or instant first vibration.
