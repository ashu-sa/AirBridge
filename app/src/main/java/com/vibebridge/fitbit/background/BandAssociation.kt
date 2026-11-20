package com.vibebridge.fitbit.background

import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.os.Build

/** System-approved background start access; never opens GATT or changes the Bluetooth bond. */
object BandAssociation {
    @Suppress("DEPRECATION")
    fun isAssociated(context: Context, address: String): Boolean {
        if (address.isBlank()) return false
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 33) manager.myAssociations.any {
                it.deviceMacAddress?.toString()?.equals(address, true) == true
            } else manager.associations.any { it.equals(address, true) }
        }.getOrDefault(false)
    }

    fun requestFor(address: String): AssociationRequest = AssociationRequest.Builder()
        .addDeviceFilter(BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder().setDeviceAddress(address).build()).build())
        .setSingleDevice(true)
        .build()

    @Suppress("DEPRECATION")
    fun remove(context: Context, address: String) {
        val manager = context.getSystemService(CompanionDeviceManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            manager.myAssociations.filter { it.deviceMacAddress?.toString()?.equals(address, true) == true }
                .forEach { manager.disassociate(it.id) }
        } else if (manager.associations.any { it.equals(address, true) }) manager.disassociate(address)
    }
}
