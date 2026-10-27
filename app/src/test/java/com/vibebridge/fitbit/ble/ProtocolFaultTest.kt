package com.vibebridge.fitbit.ble

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

class ProtocolFaultTest {
    @Test fun realSettingsMessagesRoundTripAllSupportedIntensities() {
        (1..3).forEach { value ->
            assertEquals(value, AirDirectSettingsProtocol.parseHapticsIntensity(AirDirectSettingsProtocol.encodeHapticsSettingsResponse(value)))
        }
    }
    @Test fun everyTruncationIsRejectedOrHasNoSetting() {
        val full=AirDirectSettingsProtocol.encodeHapticsSettingsResponse(2)
        for(length in 0 until full.size) {
            try { assertNull(AirDirectSettingsProtocol.parseHapticsIntensity(full.copyOf(length))) }
            catch (_:IllegalArgumentException) { }
        }
    }
    @Test fun oversizedAndOverflowedLengthsCannotAllocateOrWrap() {
        val packets=listOf(
            byteArrayOf(10,0xff.toByte(),0xff.toByte(),0xff.toByte(),0xff.toByte(),7),
            byteArrayOf(10,0x80.toByte(),0x80.toByte(),0x80.toByte(),0x80.toByte(),16),
            byteArrayOf(18,0xff.toByte(),0xff.toByte(),0xff.toByte(),0xff.toByte(),7))
        packets.forEach { bytes ->
            try { AirDirectSettingsProtocol.parseHapticsIntensity(bytes);fail("Accepted an impossible length") }
            catch (_:IllegalArgumentException) { }
        }
    }
    @Test(timeout=5_000) fun tenThousandCorruptedResponsesCannotHangOrCrashTheParser() {
        val random=Random(20261027)
        repeat(10_000) {
            val bytes=ByteArray(random.nextInt(129));random.nextBytes(bytes)
            try { AirDirectSettingsProtocol.parseHapticsIntensity(bytes) }
            catch (_:IllegalArgumentException) { }
        }
    }
}
