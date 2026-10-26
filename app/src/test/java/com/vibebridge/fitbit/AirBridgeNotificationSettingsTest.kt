package com.vibebridge.fitbit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirBridgeNotificationSettingsTest {

    @Test
    fun parseRules_respectsMax5Limit() {
        val input = """
            com.app1=single:true
            com.app2=double:true
            com.app3=triple:true
            com.app4=urgent:false
            com.app5=long_gap:true
            com.app6=single:true
        """.trimIndent()

        val result = AirBridgeNotificationSettings.parseRules(input)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Maximum 5 rules supported") })
    }

    @Test
    fun parseRules_parsesEnabledAndPattern() {
        val input = """
            com.whatsapp=double:true
            org.telegram.messenger=single:false
            com.google.android.apps.messaging=urgent:true
        """.trimIndent()

        val result = AirBridgeNotificationSettings.parseRules(input)
        assertTrue(result.isValid)
        assertEquals(3, result.rules.size)

        val whatsapp = result.rules[0]
        assertEquals("com.whatsapp", whatsapp.packageName)
        assertEquals(AirHapticPattern.DOUBLE, whatsapp.pattern)
        assertTrue(whatsapp.isEnabled)

        val telegram = result.rules[1]
        assertEquals("org.telegram.messenger", telegram.packageName)
        assertEquals(AirHapticPattern.SINGLE, telegram.pattern)
        assertFalse(telegram.isEnabled)
    }

    @Test
    fun parseRules_legacyFormatDefaultsToEnabled() {
        val input = """
            com.whatsapp=double
            org.telegram.messenger=single
        """.trimIndent()

        val result = AirBridgeNotificationSettings.parseRules(input)
        assertTrue(result.isValid)
        assertEquals(2, result.rules.size)
        assertTrue(result.rules[0].isEnabled)
        assertTrue(result.rules[1].isEnabled)
    }

    @Test
    fun normalizeRules_includesEnabledStatus() {
        val rules = listOf(
            AirNotificationRule("com.whatsapp", AirHapticPattern.DOUBLE, true),
            AirNotificationRule("org.telegram.messenger", AirHapticPattern.SINGLE, false)
        )
        val normalized = AirBridgeNotificationSettings.normalizeRules(rules)
        assertEquals("com.whatsapp=double:true\norg.telegram.messenger=single:false", normalized)
    }
}
