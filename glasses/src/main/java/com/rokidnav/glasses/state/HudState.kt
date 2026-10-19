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
    /** Mid-trip and nothing from the phone for [HudStore.PHONE_SILENT_MS]: the phone's session ended
     *  (e.g. the HUD was backgrounded), so guidance on screen would be frozen. */
    val phoneSilent: Boolean = false,
    val navState: NavState = NavState.IDLE,
    val units: Units = Units.METRIC,
    val clock24h: Boolean = false,
    val destinationName: String = "",

    val route: List<LatLng> = emptyList(),
    val routeTotalMeters: Int = 0,
    val traffic: List<TrafficSegment> = emptyList(),
    val contextRoads: List<HudRoad> = emptyList(),
    val position: PositionUpdate? = null,
    val previousPosition: PositionUpdate? = null,
    val maneuver: ManeuverUpdate? = null,
    val progress: TripProgress? = null,

    /** Active alerts sorted nearest-first; the HUD shows only the first. */
    val alerts: List<Alert> = emptyList(),
    /** When each alert id first appeared (clock ms): the chip is highlighted briefly, then goes quiet. */
    val alertSeenAt: Map<String, Long> = emptyMap(),
    val detour: DetourOffer? = null,

    val mode: HudMode = HudMode.Compact,
    val mapZoom: Int = 1,         // 0 = close (~300 m ahead), 1 = default (~800 m), 2 = far (~2 km)
    val voiceMuted: Boolean = false,
    val nowMs: Long = 0,
)

data class HudRoad(val points: List<LatLng>, val name: String, val major: Boolean)

sealed interface HudMode {
    /** Bottom corners leave the road in the upper/central field clear. Tap to expand. */
    data object Compact : HudMode
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
