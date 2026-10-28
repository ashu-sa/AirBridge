package com.vibebridge.fitbit.ble

data class DiscoveredBleDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isFitbit: Boolean,
    val bleSeen: Boolean = true,
    val connectable: Boolean = true,
    val paired: Boolean = false,
    val source: String = "Nearby BLE"
)

object BandDiscoveryPolicy {
    fun isAir(name: String): Boolean = Regex("\\bfitbit[\\s_-]+air\\b", RegexOption.IGNORE_CASE).containsMatchIn(name)
    fun automatic(devices: List<DiscoveredBleDevice>, savedAddress: String): DiscoveredBleDevice? {
        val nearby = devices.filter { it.bleSeen && it.connectable }
        if (savedAddress.isNotBlank()) return nearby.firstOrNull { it.address.equals(savedAddress, true) }
        return nearby.filter { isAir(it.name) }.distinctBy { it.address.uppercase() }.singleOrNull()
    }
    fun merge(old: DiscoveredBleDevice?, next: DiscoveredBleDevice): DiscoveredBleDevice {
        if (old == null) return next
        val name = if (next.name in setOf("Unnamed Bluetooth device", "BLE Device")) old.name else next.name
        val live = if (next.bleSeen || !old.bleSeen) next else old
        return live.copy(name = name, isFitbit = name.contains("fitbit", true), paired = old.paired || next.paired)
    }
}
