package com.fitbit.bluetooth.fbgatt

object BitGattHelper {
    fun clearConnections(fitbitGatt: FitbitGatt) {
        runCatching {
            fitbitGatt.connectionMap.values.toList().forEach { connection ->
                runCatching { connection.close() }
            }
            fitbitGatt.connectionMap.clear()
        }
    }

    fun removeConnectionForAddress(fitbitGatt: FitbitGatt, address: String) {
        runCatching {
            val map = fitbitGatt.connectionMap
            map.keys.filter { it.address.equals(address, ignoreCase = true) }
                .forEach { map.remove(it) }
        }
    }
}
