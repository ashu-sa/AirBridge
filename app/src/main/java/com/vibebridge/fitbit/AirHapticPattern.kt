package com.vibebridge.fitbit

enum class AirHapticPattern(
    val wireName: String,
    val displayName: String,
    /** Delays between consecutive toggle/restore pulse groups (in milliseconds) */
    val betweenGroupDelaysMillis: List<Long>
) {
    SINGLE("single", "Single Pulse", emptyList()),
    DOUBLE("double", "Double Pulse", listOf(650L)),
    TRIPLE("triple", "Triple Pulse", listOf(650L, 650L)),
    LONG_GAP("long_gap", "Long Gap Double", listOf(1_800L)),
    URGENT("urgent", "Urgent Alert (Calls)", listOf(450L, 1_350L, 450L)),
    CONTINUOUS("continuous", "Continuous Ring", listOf(500L));

    val groupCount: Int
        get() = betweenGroupDelaysMillis.size + 1

    companion object {
        fun fromWireName(value: String): AirHapticPattern? = values().firstOrNull {
            it.wireName.equals(value.trim(), ignoreCase = true)
        }
    }
}
