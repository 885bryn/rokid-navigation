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

    @Test fun tapTogglesMinimalWhenNoDetour() {
        store.onKey(GlassesKey.TAP)
        assertEquals(HudMode.Minimal, store.state.value.mode)
        store.onKey(GlassesKey.TAP)
        assertEquals(HudMode.Map, store.state.value.mode)
    }

    @Test fun longPressOpensMenuDoubleTapCloses() {
        store.onKey(GlassesKey.LONG_PRESS)
        assertEquals(HudMode.Menu(MenuPage.ROOT), store.state.value.mode)
        store.onKey(GlassesKey.DOUBLE_TAP)
        assertEquals(HudMode.Map, store.state.value.mode)
    }
}
