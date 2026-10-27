# Preview 1.2.1 validation

Adds installed calling-app discovery and a five-slot selector with real installed-app icons. WhatsApp, Instagram and Telegram appear first when installed; existing selected apps and user replacements are retained. Tap an icon/name to replace a slot; selecting an already displayed app swaps slots without duplicates. No separate Add manually button.

## Completed checks

- 75 unit tests pass, including evidence classification, five-slot ordering, missing/uninstalled defaults, replacement and duplicate swapping.
- Debug APK and instrumentation APK build successfully. Android lint: zero errors, 110 warnings.
- Two read-only discovery instrumentation tests pass on the connected Samsung SM-M346B (Android 16): results are installed/enabled with calling evidence; scanning does not enable or select alerts.
- Physical UI inspected: five real app icons, requested first-three ordering, collapsed/expanded card. Replacement logic is covered by unit tests; the on-device picker interaction was not verified because selections changed during inspection. Discovery itself preserves settings. Scheduling was off at the read-only check; app-call selections changed later, so no final selection reset was performed.

## Detection limits

Known calling apps and previously observed recognizable incoming-call notifications supply default slots. Android calling declarations are weaker evidence: bundled libraries can declare support in apps that do not offer a useful incoming-call experience. Those candidates remain labeled unverified in the replacement picker rather than filling default slots. Discovery runs on opening/resuming the controls, without background polling or launching other apps. Only package identifiers are stored for observed incoming-call notifications; no message content or caller data is recorded.

The six routing integration checks in validation-1.2.0.md apply to the earlier preview and were not rerun for this selector update. Live third-party call delivery, the pending real SIM-call retest, Fitbit sync coexistence and unplugged battery drain remain unverified. This preview is not a compatibility certification.

## Priority Apps visual alignment

The App calls card now uses the same header icon tile, rounded app rows, teal selection borders, app-name replacement affordance and per-app switches as Priority Apps. The bounded call pattern remains shared below the rows. This is a presentation change; the five-slot model and alert routing are unchanged.

The row-style update builds and passes lint (zero errors, 110 warnings) and was installed over the existing phone build. The phone was locked during the subsequent visual check; no settings were changed or physical alerts triggered for this styling update. The earlier connected-phone screenshot shows the previous icon layout.
