package com.rokidnav.glasses.state

import com.rokidnav.protocol.Alert
import com.rokidnav.protocol.DetourOffer
import com.rokidnav.protocol.LatLng
import com.rokidnav.protocol.ManeuverUpdate
import com.rokidnav.protocol.NavState
import com.rokidnav.protocol.PositionUpdate
import com.rokidnav.protocol.TrafficSegment
import com.rokidnav.protocol.TripProgress
import com.rokidnav.protocol.Units

/** Immutable snapshot the HudView draws. Everything the screen shows must come from here. */
data class HudState(
    val linkConnected: Boolean = false,
    val navState: NavState = NavState.IDLE,
    val units: Units = Units.IMPERIAL,
    val clock24h: Boolean = false,
    val destinationName: String = "",

    val route: List<LatLng> = emptyList(),
    val routeTotalMeters: Int = 0,
    val traffic: List<TrafficSegment> = emptyList(),
    val position: PositionUpdate? = null,
    val maneuver: ManeuverUpdate? = null,
    val progress: TripProgress? = null,

    /** Active alerts sorted nearest-first; the HUD shows only the first. */
    val alerts: List<Alert> = emptyList(),
    val detour: DetourOffer? = null,

    val mode: HudMode = HudMode.Map,
    val mapZoom: Int = 1,         // 0 = close (~300 m ahead), 1 = default (~800 m), 2 = far (~2 km)
    val voiceMuted: Boolean = false,
    val nowMs: Long = 0,
)

sealed interface HudMode {
    /** Default: maneuver banner + map + status strip. */
    data object Map : HudMode
    /** Banner + status only, map hidden — less light in the eye, e.g. on highways. */
    data object Minimal : HudMode
    /** Long-press menu. [index] is the highlighted item. */
    data class Menu(val page: MenuPage, val index: Int = 0) : HudMode
}

enum class MenuPage { ROOT, TRIP_INFO, TRAFFIC, SETTINGS, CONFIRM_END }

/** Root menu items in display order. */
enum class MenuItem(val label: String) {
    TRIP_INFO("Trip info"),
    TRAFFIC("Traffic & routes"),
    TOGGLE_VOICE("Voice on/off"),
    SETTINGS("Display"),
    END("End navigation"),
}
