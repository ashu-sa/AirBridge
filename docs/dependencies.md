# BitGatt consumer rule adjustment

`app/libs/bitgatt-v0.9.11.aar` comes from `com.github.fitbit:bitgatt:v0.9.11` at https://jitpack.io/com/github/fitbit/bitgatt/v0.9.11/bitgatt-v0.9.11.aar . Upstream source: https://github.com/Fitbit/bitgatt/tree/v0.9.11 ; license: Mozilla Public License 2.0 (https://www.mozilla.org/MPL/2.0/).

Only `proguard.txt` is changed. Its final blanket rule:

```proguard
-keep public class * {
    public protected *;
}
```

is scoped to `com.fitbit.**`. Every other AAR entry, including classes.jar, resources and metadata, is byte-for-byte identical to the upstream artifact. The POM has no transitive dependencies. The original Maven dependency is replaced by this local AAR, avoiding duplicate classes.

The app additionally preserves all Fitbit classes and members for JNI callbacks. This allows R8 to remove unused app/UI dependency code without changing Fitbit's bridge code. No native library is removed or modified. License and upstream source remain available at the links above.

Original artifact SHA-256: `9e1f87ef7aa23878eb0dae552bda2cb6248cedf8fa11c76fea27d8cabafb88bc`.

To reproduce, download the original artifact, verify that hash, and replace only `-keep public class *` with `-keep public class com.fitbit.**` in its proguard.txt entry; preserve all other entries.
