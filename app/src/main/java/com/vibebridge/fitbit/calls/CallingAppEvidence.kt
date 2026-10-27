package com.vibebridge.fitbit.calls

enum class CallingAppReason(val label: String) {
    OBSERVED("Call notification observed"), DECLARED("Calling support declared • unverified"),
    KNOWN("Known calling app"), MANUAL("Added manually")
}
data class CallingAppEvidence(
    val installed: Boolean, val known: Boolean = false, val declared: Boolean = false,
    val observed: Boolean = false, val selectedManually: Boolean = false, val isSystemDialer: Boolean = false
) {
    val reason: CallingAppReason? get() = when {
        !installed || isSystemDialer -> null
        observed -> CallingAppReason.OBSERVED
        known -> CallingAppReason.KNOWN
        declared -> CallingAppReason.DECLARED
        selectedManually -> CallingAppReason.MANUAL
        else -> null
    }
}
object KnownCallingApps {
    val packages = setOf("com.whatsapp", "com.whatsapp.w4b", "com.Slack",
        "com.google.android.apps.tachyon", "com.google.android.apps.meetings", "us.zoom.videomeetings",
        "org.telegram.messenger", "org.thoughtcrime.securesms", "com.discord", "com.microsoft.teams",
        "com.facebook.orca", "com.instagram.android", "com.snapchat.android", "com.viber.voip", "jp.naver.line.android")
}
