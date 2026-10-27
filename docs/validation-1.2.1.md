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

## Collapsed icons and band icon

Collapsed App calls now shows a horizontal strip of selected app icons with the same 46dp tiles and selection dots as Priority Apps. Tapping an icon expands the controls. No selection is required to render the empty-state prompt. The generic watch glyph in Your Band is replaced by an original screenless Fitbit Air tracker/loop illustration, following the form described in Google’s product introduction: https://blog.google/products-and-platforms/devices/fitbit/fitbit-air/. The illustration is an app asset, not an official Google logo.

## Repeated-loading fix

Removed selection and slot changes from discovery effect triggers. Reopening the section and resuming the activity reuse a process-memory inventory for up to five minutes. Package add/remove/change/replace broadcasts and newly observed incoming-call apps invalidate it. Existing rows remain visible during stale-cache refresh; a loading bar is reserved for a cold inventory scan. The package receiver follows the card lifecycle and is unregistered on disposal.

Validation: 75 unit tests pass; debug build and lint pass (zero errors, 110 warnings). Three emulator discovery instrumentation tests pass, including identity checks proving repeated discovery reuses the same inventory until invalidation, and checks that discovery preserves alert settings. Updated APK installed on the connected phone without changing settings. Live call delivery and battery/sync limitations above still apply.

## Published non-debug release 1.2.2

The release uses versionCode 7 and a new private signing certificate, with signing configuration supplied through environment variables. The key and credentials remain outside the repository. Existing debug installations require uninstalling before switching to this certificate. The refreshed screenshots show the connected phone's current controls; only status/navigation bars were cropped. No live call, battery or sync certification is implied.
