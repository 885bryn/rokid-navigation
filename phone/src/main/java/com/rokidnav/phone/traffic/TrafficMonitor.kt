package com.rokidnav.phone.traffic

import com.rokidnav.phone.nav.NavigationEngine
import com.rokidnav.phone.nav.RouteOption
import com.rokidnav.protocol.TrafficSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * While navigating, every [pollIntervalMs]: call Routes API from the current position (with heading)
 * to the destination, then
 *  1. convert the primary route's speedReadingIntervals into [TrafficSegment]s (distance-based) and
 *     emit on [segments]
 *  2. run [DetourEvaluator] on the alternatives and emit on [detours]
 *
 * Cost note: each poll is a billed Routes API call. 2–3 min while moving is a reasonable default;
 * stop polling when stationary or on the last ~2 km.
 */
class TrafficMonitor(
    private val routes: RoutesApiClient,
    private val engine: NavigationEngine,
    private val evaluator: DetourEvaluator = DetourEvaluator(),
    private val pollIntervalMs: Long = 150_000,
) {
    private val _segments = MutableSharedFlow<List<TrafficSegment>>(replay = 1)
    val segments: Flow<List<TrafficSegment>> = _segments.asSharedFlow()

    private val _detours = MutableSharedFlow<DetourEvaluator.Decision>(extraBufferCapacity = 1)
    val detours: Flow<DetourEvaluator.Decision> = _detours.asSharedFlow()

    fun start(scope: CoroutineScope) {
        // TODO(M6): launch a loop gated on engine.navState == ACTIVE; use latest engine.position
        // and engine.progress; map speed intervals to meters along polyline via PolylineCodec + Geo.
    }

    fun stop() {
        // TODO(M6)
    }

    fun dismiss(option: RouteOption, nowMs: Long) = evaluator.markDismissed(option, nowMs)
}
