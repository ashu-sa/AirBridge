package com.fitbit.bluetooth.fbgatt

object BitGattHelper {
    fun removeOwnedConnection(fitbitGatt: FitbitGatt, connection: GattConnection) {
        val map = fitbitGatt.connectionMap
        map.entries.toList().filter { it.value === connection }.forEach { (device, owned) ->
            map.remove(device, owned)
        }
    }

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
