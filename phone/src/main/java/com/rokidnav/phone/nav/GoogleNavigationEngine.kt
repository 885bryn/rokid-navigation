package com.rokidnav.phone.nav

import android.app.Activity
import android.content.Context
import com.rokidnav.protocol.ManeuverUpdate
import com.rokidnav.protocol.NavState
import com.rokidnav.protocol.PositionUpdate
import com.rokidnav.protocol.TripProgress
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Navigation SDK for Android. Responsibilities:
 *  - NavigationApi.getNavigator(activity, listener) -> Navigator
 *  - navigator.setDestinations(waypoints, CustomRoutesOptions(routeToken)) so guidance follows the
 *    exact route the user picked in our UI (from Routes API)
 *  - navigator.startGuidance(); listeners for arrival, route change, remaining time/distance
 *  - registerServiceForNavUpdates(...) -> [NavUpdatesService] -> [NavInfoMapper] -> maneuver flow
 *  - RoadSnappedLocationProvider -> position flow (~1 Hz, road-snapped, with bearing)
 *  - navigator.simulator for no-drive testing
 *
 * The phone screen shows the SDK's own NavigationView during a trip; that's fine and also useful
 * as the "source of truth" when debugging the glasses.
 *
 * VERIFY every SDK call against the Navigation SDK version in libs.versions.toml.
 */
class GoogleNavigationEngine(private val context: Context) : NavigationEngine {

    private val _navState = MutableStateFlow(NavState.IDLE)
    override val navState: StateFlow<NavState> = _navState.asStateFlow()

    private val _position = latest<PositionUpdate>()
    override val position = _position.asSharedFlow()

    private val _maneuver = latest<ManeuverUpdate>()
    override val maneuver = _maneuver.asSharedFlow()

    private val _progress = latest<TripProgress>()
    override val progress = _progress.asSharedFlow()

    private val _activeRoute = MutableStateFlow<ActiveRoute?>(null)
    override val activeRoute: StateFlow<ActiveRoute?> = _activeRoute.asStateFlow()

    // private var navigator: Navigator? = null

    override suspend fun initialize(activity: Activity): Boolean {
        // TODO(M5): NavigationApi.getNavigator(activity, object : NavigatorListener { ... })
        //  - onNavigatorReady: store navigator, register listeners, registerServiceForNavUpdates(
        //        context.packageName, NavUpdatesService::class.java.name, /*numNextStepsToPreview*/ 1)
        //  - onError(TERMS_NOT_ACCEPTED / NOT_AUTHORIZED / ...): return false with a readable reason
        return false
    }

    override suspend fun start(destination: Destination, option: RouteOption?): Boolean {
        _navState.value = NavState.ROUTING
        // TODO(M5): Waypoint.builder().setPlaceIdString(destination.placeId) (fall back to lat/lng)
        //  navigator.setDestinations(listOf(wp), CustomRoutesOptions.builder()
        //      .setRouteToken(option.routeToken).setTravelMode(DRIVING).build())
        //  on RouteStatus.OK: startGuidance(), publish activeRoute (polyline from option or
        //  navigator.currentRouteSegment), navState = ACTIVE
        return false
    }

    override suspend fun switchRoute(option: RouteOption): Boolean {
        // TODO(M6): same as start() with the new token; keep the destination. Publish new activeRoute.
        return false
    }

    override fun setVoiceMuted(muted: Boolean) {
        // TODO(M8): navigator.setAudioGuidance(if (muted) SILENT else VOICE_ALERTS_AND_GUIDANCE)
    }

    override fun simulate(enabled: Boolean) {
        // TODO(M5): navigator.simulator.simulateLocationsAlongExistingRoute() / unsetUserLocation()
    }

    override fun stop() {
        // TODO(M5): navigator.stopGuidance(); navigator.clearDestinations()
        _navState.value = NavState.ENDED
        _activeRoute.value = null
    }

    /** Called by [NavUpdatesService] on the main thread. */
    internal fun onManeuver(update: ManeuverUpdate) {
        _maneuver.tryEmit(update)
    }

    internal fun onNavStateFromFeed(state: NavState) {
        if (_navState.value != NavState.ENDED) _navState.value = state
    }

    private fun <T> latest() = MutableSharedFlow<T>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
}
