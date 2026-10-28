# Golden Gate invokes JVM callbacks from JNI. Preserve its bundled bridge and
# transport libraries conservatively; shrink the app/Compose and unused icons.
-keep class com.fitbit.** { *; }
-keep interface com.fitbit.** { *; }
-keep class org.jetbrains.annotations.** { *; }
-dontwarn org.jetbrains.annotations.**
# Optional remote-RPC CBOR types were absent from the original AAR bundle and
# are not used by AirBridge's settings/CoAP path. Do not suppress JNI warnings.
-dontwarn co.nstant.in.cbor.**
# Compile-time Error Prone annotation metadata, not an Android runtime API.
-dontwarn javax.lang.model.element.Modifier
