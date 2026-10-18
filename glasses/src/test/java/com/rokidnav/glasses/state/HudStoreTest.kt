package com.rokidnav.glasses.state

import com.rokidnav.glasses.input.GlassesKey
import com.rokidnav.protocol.ActionType
import com.rokidnav.protocol.DetourOffer
import com.rokidnav.protocol.GlassesAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HudStoreTest {
    private val sent = mutableListOf<GlassesAction>()
    private val store = HudStore { sent += it }

    @Test fun tapAcceptsPendingDetour() {
        store.onMessage(DetourOffer(1, "d1", 300, 0, "via A", expiresAtMs = Long.MAX_VALUE))
        store.onKey(GlassesKey.TAP)
        assertEquals(ActionType.ACCEPT_DETOUR, sent.single().action)
        assertEquals("d1", sent.single().targetId)
        assertNull(store.state.value.detour)
    }

    @Test fun tapExpandsAndCollapsesWhenNoDetour() {
        store.onKey(GlassesKey.TAP)
        assertEquals(HudMode.Map, store.state.value.mode)
        store.onKey(GlassesKey.TAP)
        assertEquals(HudMode.Compact, store.state.value.mode)
    }

    @Test fun longPressOpensMenuDoubleTapCloses() {
        store.onKey(GlassesKey.LONG_PRESS)
        assertEquals(HudMode.Menu(MenuPage.ROOT), store.state.value.mode)
        store.onKey(GlassesKey.DOUBLE_TAP)
        assertEquals(HudMode.Compact, store.state.value.mode)
    }

    @Test fun alertCountsDownClearsAndEndsWithTrip() {
        val alert = com.rokidnav.protocol.Alert(1, "p1", com.rokidnav.protocol.AlertKind.POLICE,
            1000, 0.0, 0.01, "Police reported", "WzSabre")
        store.onMessage(alert)
        store.onMessage(alert.copy(seq = 2, distanceAheadMeters = 500))
        assertEquals(500, store.state.value.alerts.single().distanceAheadMeters)
        store.onMessage(com.rokidnav.protocol.AlertCleared(3, "p1"))
        assertEquals(emptyList<com.rokidnav.protocol.Alert>(), store.state.value.alerts)
        store.onMessage(alert.copy(seq = 4))
        store.onMessage(com.rokidnav.protocol.NavStateUpdate(5, com.rokidnav.protocol.NavState.ENDED))
        assertEquals(emptyList<com.rokidnav.protocol.Alert>(), store.state.value.alerts)
    }

    @Test fun dismissedAlertStaysDismissedDuringCountdownAndNewAlertPreemptsMenu() {
        val alert=com.rokidnav.protocol.Alert(1,"p1",com.rokidnav.protocol.AlertKind.POLICE,1000,0.0,0.0)
        store.onMessage(alert)
        store.onKey(GlassesKey.DOUBLE_TAP)
        store.onMessage(alert.copy(seq=2,distanceAheadMeters=500))
        assertEquals(0,store.state.value.alerts.size)
        store.onKey(GlassesKey.LONG_PRESS)
        store.onMessage(alert.copy(seq=3,id="p2"))
        assertEquals(HudMode.Compact,store.state.value.mode)
    }

    @Test fun endTripRequiresConfirmAndMenuTimeoutIsDeterministic() {
        var now=1000L
        val actions=mutableListOf<GlassesAction>()
        val hud=HudStore(clock={now}){actions+=it}
        hud.onKey(GlassesKey.LONG_PRESS)
        repeat(4){hud.onKey(GlassesKey.SWIPE_FORWARD)}
        hud.onKey(GlassesKey.TAP)
        assertEquals(MenuPage.CONFIRM_END,(hud.state.value.mode as HudMode.Menu).page)
        assertEquals(0,actions.size)
        hud.onKey(GlassesKey.TAP)
        assertEquals(ActionType.END_NAVIGATION,actions.single().action)
        hud.onKey(GlassesKey.LONG_PRESS)
        now+=10_000
        hud.tick()
        assertEquals(HudMode.Compact,hud.state.value.mode)
    }
    @Test fun staleMessagesCannotReplaceNewerTurnsAndSessionResetsSequence() {
        store.onMessage(com.rokidnav.protocol.ManeuverUpdate(20,com.rokidnav.protocol.Step(com.rokidnav.protocol.Maneuver.TURN_RIGHT),300,20))
        store.onMessage(com.rokidnav.protocol.ManeuverUpdate(19,com.rokidnav.protocol.Step(com.rokidnav.protocol.Maneuver.TURN_LEFT),500,40))
        assertEquals(com.rokidnav.protocol.Maneuver.TURN_RIGHT,store.state.value.maneuver!!.current.maneuver)
        store.onMessage(com.rokidnav.protocol.SessionStart(1,destinationName="New trip"))
        assertNull(store.state.value.maneuver)
        store.onMessage(com.rokidnav.protocol.ManeuverUpdate(2,com.rokidnav.protocol.Step(com.rokidnav.protocol.Maneuver.TURN_LEFT),200,10))
        assertEquals(com.rokidnav.protocol.Maneuver.TURN_LEFT,store.state.value.maneuver!!.current.maneuver)
    }
    @Test fun incompleteRouteRequestsOneResyncAndMenuReturnsToExpandedMode() {
        var now=1000L;var resyncs=0
        val hud=HudStore(clock={now},requestResync={resyncs++}){}
        hud.onMessage(com.rokidnav.protocol.RouteChunk(1,"r",0,2,"??",100))
        now+=5000;hud.tick();hud.tick()
        assertEquals(1,resyncs)
        hud.onKey(GlassesKey.TAP)
        hud.onKey(GlassesKey.LONG_PRESS)
        now+=10000;hud.tick()
        assertEquals(HudMode.Map,hud.state.value.mode)
    }
}
