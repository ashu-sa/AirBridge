package com.vibebridge.fitbit

enum class AirHapticPattern(
    val wireName: String,
    val displayName: String,
    val restoreDelayMillis: Long = 450L,
    /** Delays between consecutive toggle/restore pulse groups (in milliseconds) */
    val betweenGroupDelaysMillis: List<Long> = emptyList()
) {
    SINGLE("single", "Single pulse", 400L, emptyList()),
    DOUBLE("double", "Double pulse", 350L, listOf(300L)),
    TRIPLE("triple", "Triple pulse", 300L, listOf(220L, 220L)),
    LONG_GAP("long_gap", "Long Gap Double", 400L, listOf(1_000L)),
    URGENT("urgent", "Urgent alert", 700L, listOf(400L, 400L)),
    CONTINUOUS("continuous", "Continuous ring", 350L, emptyList());

    val groupCount: Int
        get() = betweenGroupDelaysMillis.size + 1

    companion object {
        fun fromWireName(value: String): AirHapticPattern? = values().firstOrNull {
            it.wireName.equals(value.trim(), ignoreCase = true)
        }
    }
}
