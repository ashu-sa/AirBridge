package com.vibebridge.fitbit.ble

import org.junit.Assert.*
import org.junit.Test

class BandDiscoveryPolicyTest {
    private fun air(address: String = "AA", ble: Boolean = true, connectable: Boolean = true) =
        DiscoveredBleDevice("Google Fitbit Air", address, -50, true, bleSeen = ble, connectable = connectable)
    @Test fun recognizesAirNames() { listOf("Google Fitbit Air", "Fitbit-Air", "FITBIT AIR 123").forEach { assertTrue(BandDiscoveryPolicy.isAir(it)) } }
    @Test fun doesNotRecognizeOtherModelsOrAirpods() { listOf("Fitbit Inspire", "Fitbit Airpods", "Air", "Unknown").forEach { assertFalse(BandDiscoveryPolicy.isAir(it)) } }
    @Test fun selectsSingleNearbyAirOnFirstSetup() { assertEquals("AA", BandDiscoveryPolicy.automatic(listOf(air()), "")?.address) }
    @Test fun requiresChoiceWhenMultipleAirDevices() { assertNull(BandDiscoveryPolicy.automatic(listOf(air(), air("BB")), "")) }
    @Test fun doesNotAutoSelectCachedPairedAir() { assertNull(BandDiscoveryPolicy.automatic(listOf(air(ble = false)), "")) }
    @Test fun ignoresNonconnectableAdvertisements() { assertNull(BandDiscoveryPolicy.automatic(listOf(air(connectable = false)), "")) }
    @Test fun prefersSavedAddressRegardlessOfName() { val saved = air("saved").copy(name = "Unnamed Bluetooth device");assertEquals(saved, BandDiscoveryPolicy.automatic(listOf(air(), saved), "SAVED")) }
    @Test fun doesNotReplaceMissingSavedBandWithAnotherAir() { assertNull(BandDiscoveryPolicy.automatic(listOf(air()), "BB")) }
    @Test fun doesNotSelectGenericNearbyBluetoothDevice() { assertNull(BandDiscoveryPolicy.automatic(listOf(air().copy(name = "Headphones", isFitbit = false)), "")) }
    @Test fun mergesCachedNameWithLiveAdvertisement() { val paired = air(ble = false).copy(paired = true);val live = air().copy(name = "Unnamed Bluetooth device");val merged = BandDiscoveryPolicy.merge(paired, live);assertEquals("Google Fitbit Air", merged.name);assertTrue(merged.bleSeen);assertTrue(merged.paired) }
    @Test fun classicDiscoveryDoesNotDowngradeLiveBleEvidence() { val live = air();val classic = air(ble = false).copy(source = "Classic");assertTrue(BandDiscoveryPolicy.merge(live, classic).bleSeen) }
    @Test fun acceptsSavedAlreadyConnectedBleDeviceWithoutAdvertisement() { val connected=air().copy(source="Connected Bluetooth LE",rssi=Int.MIN_VALUE);assertEquals(connected,BandDiscoveryPolicy.automatic(listOf(connected),"AA")) }
    @Test fun duplicateAdvertisementsDoNotMakeAirAmbiguous() { assertEquals("AA", BandDiscoveryPolicy.automatic(listOf(air(), air()), "")?.address) }
}
