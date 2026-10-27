package com.vibebridge.fitbit.calls

import org.junit.Assert.*
import org.junit.Test

class IncomingAppCallTest {
    @Test fun modernIncomingCallNeedsNoMessageText() { assertTrue(CallNotificationMetadata(callType=1).isIncoming()) }
    @Test fun ongoingScreeningAndUnknownTypesAreNotIncoming() { listOf(-1,0,2,3,99).forEach { assertFalse(CallNotificationMetadata("call",it,true,2,true).isIncoming()) } }
    @Test fun legacyIncomingRequiresCategoryActionsAndRingingMetadata() {
        assertTrue(CallNotificationMetadata("call",fullScreen=true,actionCount=2).isIncoming())
        assertTrue(CallNotificationMetadata("call",insistent=true,actionCount=2).isIncoming())
        assertFalse(CallNotificationMetadata("call",actionCount=2).isIncoming())
        assertFalse(CallNotificationMetadata("call",fullScreen=true,actionCount=1).isIncoming())
    }
    @Test fun messagesAndMeetingRemindersAreNotCalls() { listOf("msg","event","reminder",null).forEach { assertFalse(CallNotificationMetadata(it,fullScreen=true,actionCount=3,insistent=true).isIncoming()) } }
    @Test fun summariesAndLegacyChronometersDoNotAlert() {
        assertFalse(CallNotificationMetadata(callType=1,groupSummary=true).isIncoming())
        assertFalse(CallNotificationMetadata("call",fullScreen=true,actionCount=2,chronometer=true).isIncoming())
    }
    @Test fun duplicateUpdatesAlertOnlyOnce() { val s=CallNotificationSessions();assertNotNull(s.begin("key","app",0));repeat(100_000){assertNull(s.begin("key","app",it.toLong()))} }
    @Test fun notificationRemovalAndReusedIDDoNotReviveOldToken() {
        val s=CallNotificationSessions();val first=s.begin("key","app",0)!!;s.end("key")
        val second=s.begin("key","app",1)!!;assertNotEquals(first,second);assertFalse(s.active(first,"app",2));assertTrue(s.active(second,"app",2))
    }
    @Test fun tokenIsScopedToApp() { val s=CallNotificationSessions();val t=s.begin("key","one",0)!!;assertFalse(s.active(t,"two",1)) }
    @Test fun abandonedCallsExpireAndStorageIsBounded() {
        val s=CallNotificationSessions(limit=2);val a=s.begin("a","app",0)!!;s.begin("b","app",1);s.begin("c","app",2)
        assertFalse(s.active(a,"app",3));val c=s.begin("d","app",3)!!;assertFalse(s.active(c,"app",90_003))
    }
    @Test fun featureStartsOffAndSelectionIsIndependentOfMessageRules() {
        assertFalse(AppCallConfig().allows("com.whatsapp"))
        val config=AppCallConfig(true,setOf("com.Slack"))
        assertTrue(config.allows("com.Slack"));assertFalse(config.allows("com.slack"));assertFalse(config.allows("com.whatsapp"))
    }
}
