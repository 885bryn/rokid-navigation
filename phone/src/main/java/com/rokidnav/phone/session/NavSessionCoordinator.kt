package com.rokidnav.phone.session

import com.rokidnav.phone.alerts.IncidentProvider
import com.rokidnav.phone.glasses.GlassesLink
import com.rokidnav.phone.glasses.LinkState
import com.rokidnav.phone.nav.NavigationEngine
import com.rokidnav.phone.traffic.DetourEvaluator
import com.rokidnav.phone.traffic.TrafficMonitor
import com.rokidnav.protocol.ActionType
import com.rokidnav.protocol.DetourOffer
import com.rokidnav.protocol.GlassesAction
import com.rokidnav.protocol.GlassesHello
import com.rokidnav.protocol.NavMessage
import com.rokidnav.protocol.NavStateUpdate
import com.rokidnav.protocol.ResyncRequest
import com.rokidnav.protocol.RouteChunker
import com.rokidnav.protocol.SeqCounter
import com.rokidnav.protocol.TrafficSegments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The hub. Owns no UI and no SDK objects: it only wires flows together.
 *
 *   NavigationEngine ─┐
 *   TrafficMonitor  ──┼──> protocol messages ──> GlassesLink.send
 *   IncidentProvider ─┘
 *   GlassesLink.incoming (actions) ──> engine / traffic / settings
 *
 * Also keeps the "last known" value of each stateful message so a full resync can be sent when the
 * link becomes Ready again (resume after AI assistant, Bluetooth blip, glasses app restart).
 */
class NavSessionCoordinator(
    private val scope: CoroutineScope,
    private val link: GlassesLink,
    private val engine: NavigationEngine,
    private val traffic: TrafficMonitor,
    private val incidents: IncidentProvider,
) {
    private val seq = SeqCounter()
    private val lastSent = LinkedHashMap<String, NavMessage>()  // key -> latest stateful message
    private var pendingDetour: DetourEvaluator.Decision? = null
    private var pendingOfferId: String? = null

    fun start() {
        scope.launch { engine.maneuver.collect { sendStateful("maneuver", it.copy(seq = seq.next())) } }
        scope.launch { engine.progress.collect { sendStateful("progress", it.copy(seq = seq.next())) } }
        scope.launch { engine.position.collect { link.send(it.copy(seq = seq.next())) } }
        scope.launch {
            engine.navState.collect { sendStateful("nav_state", NavStateUpdate(seq.next(), it)) }
        }
        scope.launch { engine.activeRoute.filterNotNull().collect { sendRoute() } }
        scope.launch {
            traffic.segments.collect { segs ->
                val routeId = engine.activeRoute.value?.routeId ?: return@collect
                sendStateful("traffic", TrafficSegments(seq.next(), routeId, segs))
            }
        }
        scope.launch { traffic.detours.collect(::offerDetour) }
        // TODO(M7): collect incidents.incidentsAlong(route) on each route change; compute distance
        //  ahead along the polyline; send Alert when within ~2 km, AlertCleared once passed.
        scope.launch { link.incoming.collect(::onGlassesMessage) }
        scope.launch {
            link.state.collect { if (it == LinkState.Ready) resyncAll("link ready") }
        }
        traffic.start(scope)
    }

    private fun sendStateful(key: String, message: NavMessage) {
        lastSent[key] = message
        link.send(message)
    }

    private fun sendRoute() {
        val route = engine.activeRoute.value ?: return
        RouteChunker.split(route.routeId, route.encodedPolyline, route.totalMeters, seq::next)
            .forEach { link.send(it) }
    }

    private fun resyncAll(reason: String) {
        // Order matters: route first so the map has geometry, then state.
        sendRoute()
        lastSent.values.forEach { link.send(it) }
    }

    private fun offerDetour(decision: DetourEvaluator.Decision) {
        val id = "d${seq.next()}"
        pendingDetour = decision
        pendingOfferId = id
        link.send(
            DetourOffer(
                seq = seq.next(),
                offerId = id,
                savesSeconds = decision.savesSeconds,
                extraMeters = decision.extraMeters,
                via = decision.option.description,
                expiresAtMs = System.currentTimeMillis() + 30_000,
                encodedPolyline = decision.option.encodedPolyline,
            ),
        )
        // TODO(M6): auto-expire after 30 s -> DetourResolved(accepted=false); also show on phone UI.
    }

    private fun onGlassesMessage(message: NavMessage) {
        when (message) {
            is GlassesHello -> resyncAll("hello")
            is ResyncRequest -> resyncAll(message.reason)
            is GlassesAction -> onAction(message)
            else -> Unit // InputEvent: logged by the debug screen only
        }
    }

    private fun onAction(action: GlassesAction) {
        when (action.action) {
            ActionType.ACCEPT_DETOUR -> {
                val d = pendingDetour?.takeIf { pendingOfferId == action.targetId } ?: return
                scope.launch { engine.switchRoute(d.option) }
                // TODO(M6): send DetourResolved(accepted = true)
            }
            ActionType.DISMISS_DETOUR -> {
                pendingDetour?.let { traffic.dismiss(it.option, System.currentTimeMillis()) }
                // TODO(M6): send DetourResolved(accepted = false)
            }
            ActionType.END_NAVIGATION -> engine.stop()
            ActionType.TOGGLE_VOICE -> TODO("M8")
            ActionType.ZOOM_IN, ActionType.ZOOM_OUT -> Unit // handled locally on glasses; echoed for logging
            ActionType.SHOW_ALTERNATIVES -> TODO("M8: force a traffic poll and offer best alternative")
            ActionType.TOGGLE_UNITS -> TODO("M8")
        }
    }
}
