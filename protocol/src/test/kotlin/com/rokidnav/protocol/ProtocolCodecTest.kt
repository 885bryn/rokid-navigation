package com.rokidnav.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProtocolCodecTest {
    @Test fun roundTripsEveryMessageType() {
        val messages: List<NavMessage> = listOf(
            SessionStart(1, Units.METRIC, true, "Home"),
            ManeuverUpdate(2, Step(Maneuver.TURN_RIGHT, "Main St"), 300, 25, Step(Maneuver.TURN_LEFT)),
            TripProgress(3, 1_700_000_000_000, 15_000, 1_100, 240),
            Alert(4, "a1", AlertKind.POLICE, 600, 37.0, -122.0, "Police reported"),
            DetourOffer(5, "d1", 360, 800, "via I-280", expiresAtMs = 1L),
            GlassesAction(6, ActionType.ACCEPT_DETOUR, "d1"),
        )
        for (m in messages) {
            val (magic, body) = ProtocolCodec.encode(m)
            assertEquals(m, ProtocolCodec.decode(magic, body))
        }
    }

    @Test fun rejectsForeignFrames() {
        assertNull(ProtocolCodec.decode("message", "hello"))
        assertNull(ProtocolCodec.decode(ProtocolVersion.MAGIC, "{not json"))
    }

    @Test fun ignoresUnknownFieldsFromNewerSender() {
        val body = """{"t":"nav_state","seq":9,"state":"ACTIVE","futureField":42}"""
        assertEquals(NavStateUpdate(9, NavState.ACTIVE), ProtocolCodec.decode(ProtocolVersion.MAGIC, body))
    }
}
