# Release signing

Version 1.2.2 introduces the private release certificate. Earlier debug previews must be uninstalled before installing this release; uninstalling resets local settings. Future releases must reuse this certificate.

Signing material is kept outside the repository and is never uploaded with releases. Back up the local key and its credentials securely; losing them prevents compatible updates.

The Gradle release build reads these environment variables:

- `AIRBRIDGE_KEYSTORE_FILE`
- `AIRBRIDGE_KEYSTORE_PASSWORD`
- `AIRBRIDGE_KEY_ALIAS`
- `AIRBRIDGE_KEY_PASSWORD`

With those supplied, run `./gradlew assembleRelease`. Without them, the release APK is unsigned. Do not commit keys or credentials or substitute the debug signing key.
