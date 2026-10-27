package com.vibebridge.fitbit.calls
import org.junit.Assert.*
import org.junit.Test
class CallingAppSlotsTest {
    private val preferred=CallingAppSlots.preferred
    @Test fun preferredInstalledAppsComeFirst() { assertEquals(preferred,CallingAppSlots.resolve(emptyList(),emptySet(),preferred.toSet(),preferred.reversed())) }
    @Test fun neverShowsAnUninstalledDefault() { assertEquals(listOf(preferred[2]),CallingAppSlots.resolve(emptyList(),emptySet(),setOf(preferred[2]),preferred)) }
    @Test fun capsAtFiveWithoutDuplicates() { val a=(preferred+(1..10).map { "app.$it" });assertEquals(5,CallingAppSlots.resolve(emptyList(),emptySet(),a.toSet(),a+a).size) }
    @Test fun manualOrderPersists() { val a=listOf("app.one","app.two")+preferred;assertEquals(a,CallingAppSlots.resolve(a,emptySet(),a.toSet(),preferred)) }
    @Test fun existingEnabledAppsNeverBecomeHidden() { val a=(1..5).map { "app.$it" }.toSet();assertEquals(a,CallingAppSlots.resolve(emptyList(),a,a+preferred,preferred).toSet()) }
    @Test fun replacingEnabledSlotKeepsItsAlertEnabled() { val (slots,on)=CallingAppSlots.replace(listOf("app.one","app.two"),setOf("app.one"),0,"app.new");assertEquals(listOf("app.new","app.two"),slots);assertEquals(setOf("app.new"),on) }
    @Test fun choosingAnExistingAppSwapsRatherThanDuplicates() { val (slots,on)=CallingAppSlots.replace(listOf("app.one","app.two"),setOf("app.one"),0,"app.two");assertEquals(listOf("app.two","app.one"),slots);assertEquals(setOf("app.one"),on) }
}
