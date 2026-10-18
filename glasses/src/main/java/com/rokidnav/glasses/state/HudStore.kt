package com.rokidnav.glasses.state

import com.rokidnav.glasses.input.GlassesKey
import com.rokidnav.protocol.ActionType
import com.rokidnav.protocol.Alert
import com.rokidnav.protocol.AlertCleared
import com.rokidnav.protocol.DetourOffer
import com.rokidnav.protocol.DetourResolved
import com.rokidnav.protocol.GlassesAction
import com.rokidnav.protocol.ManeuverUpdate
import com.rokidnav.protocol.NavMessage
import com.rokidnav.protocol.NavStateUpdate
import com.rokidnav.protocol.PolylineCodec
import com.rokidnav.protocol.PositionUpdate
import com.rokidnav.protocol.RouteAssembler
import com.rokidnav.protocol.RouteChunk
import com.rokidnav.protocol.SessionStart
import com.rokidnav.protocol.SettingsUpdate
import com.rokidnav.protocol.TrafficSegments
import com.rokidnav.protocol.TripProgress
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single source of truth for the HUD. Two inputs: protocol messages from the phone, and keys.
 * Keep it free of Android View code so it can be unit tested (see docs/HUD_DESIGN.md for the
 * input map this implements).
 */
class HudStore(private val sendAction: (GlassesAction) -> Unit) {
    private val _state = MutableStateFlow(HudState())
    val state: StateFlow<HudState> = _state.asStateFlow()

    private val routeAssembler = RouteAssembler()

    fun onMessage(m: NavMessage) {
        when (m) {
            is SessionStart -> _state.update {
                it.copy(units = m.units, clock24h = m.clock24h, destinationName = m.destinationName)
            }
            is RouteChunk -> routeAssembler.accept(m)?.let { encoded ->
                _state.update { it.copy(route = PolylineCodec.decode(encoded), routeTotalMeters = m.totalMeters) }
            }
            is PositionUpdate -> _state.update { it.copy(position = m) }
            is ManeuverUpdate -> _state.update { it.copy(maneuver = m) }
            is TripProgress -> _state.update { it.copy(progress = m) }
            is TrafficSegments -> _state.update { it.copy(traffic = m.segments) }
            is Alert -> _state.update { s ->
                s.copy(alerts = (s.alerts.filterNot { it.id == m.id } + m).sortedBy { it.distanceAheadMeters })
            }
            is AlertCleared -> _state.update { s -> s.copy(alerts = s.alerts.filterNot { it.id == m.id }) }
            is DetourOffer -> _state.update { it.copy(detour = m) }
            is DetourResolved -> _state.update { s -> if (s.detour?.offerId == m.offerId) s.copy(detour = null) else s }
            is NavStateUpdate -> _state.update { it.copy(navState = m.state) }
            is SettingsUpdate -> _state.update {
                it.copy(voiceMuted = m.voiceMuted, units = m.units, mapZoom = m.mapZoomLevel)
            }
            else -> Unit
        }
    }

    fun onKey(key: GlassesKey) {
        val s = _state.value
        val detour = s.detour
        when (val mode = s.mode) {
            is HudMode.Menu -> onMenuKey(mode, key)
            else -> when {
                // A pending detour owns TAP / DOUBLE_TAP until resolved or expired.
                detour != null && key == GlassesKey.TAP -> {
                    sendAction(GlassesAction(0, ActionType.ACCEPT_DETOUR, detour.offerId))
                    _state.update { it.copy(detour = null) }
                }
                detour != null && key == GlassesKey.DOUBLE_TAP -> {
                    sendAction(GlassesAction(0, ActionType.DISMISS_DETOUR, detour.offerId))
                    _state.update { it.copy(detour = null) }
                }
                key == GlassesKey.LONG_PRESS -> setMode(HudMode.Menu(MenuPage.ROOT))
                key == GlassesKey.TAP -> setMode(if (mode == HudMode.Map) HudMode.Minimal else HudMode.Map)
                key == GlassesKey.SWIPE_FORWARD -> _state.update { it.copy(mapZoom = (it.mapZoom - 1).coerceAtLeast(0)) }
                key == GlassesKey.SWIPE_BACK -> _state.update { it.copy(mapZoom = (it.mapZoom + 1).coerceAtMost(2)) }
                key == GlassesKey.DOUBLE_TAP -> _state.update { s2 -> s2.copy(alerts = s2.alerts.drop(1)) } // dismiss top alert locally
                else -> Unit
            }
        }
    }

    private fun onMenuKey(menu: HudMode.Menu, key: GlassesKey) {
        // TODO(M8): implement per docs/HUD_DESIGN.md "Menu". Sketch:
        //  SWIPE_FORWARD/BACK -> move index (wrap); TAP -> open item / perform action;
        //  DOUBLE_TAP or BACK -> up one page, ROOT -> close menu.
        //  END requires CONFIRM_END page (tap = END_NAVIGATION action, double-tap = cancel).
        //  Menu auto-closes after 10 s of no input (handled in tickLoop).
        if (key == GlassesKey.DOUBLE_TAP || key == GlassesKey.BACK) setMode(HudMode.Map)
    }

    private fun setMode(mode: HudMode) = _state.update { it.copy(mode = mode) }

    /** 10 Hz clock for animations, expiries (detour offer, alerts passed, menu timeout). */
    suspend fun tickLoop() {
        while (true) {
            val now = System.currentTimeMillis()
            _state.update { s ->
                val detour = s.detour?.takeIf { it.expiresAtMs > now }
                s.copy(nowMs = now, detour = detour)
            }
            delay(100)
        }
    }
}
