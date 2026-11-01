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
            val targets = map.filter { it.key.address.equals(address, ignoreCase = true) }
            targets.forEach { (device, connection) ->
                runCatching { connection.close() }
                map.remove(device)
            }
        }
    }
}
