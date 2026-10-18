package com.rokidnav.phone.alerts

import com.rokidnav.protocol.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** Projects once per fix, never per HUD frame. Side roads and opposite directions are excluded. */
class AlertTracker(private val route: List<LatLng>) {
    private var active = emptyMap<String, Alert>()

    fun update(incidents: List<Incident>, position: PositionUpdate, nowMs: Long, nextSeq: () -> Long): List<NavMessage> {
        val fix = Geo.projectOntoPolyline(route, LatLng(position.lat, position.lng))
        val ahead = if (fix == null || fix.distanceMeters > 60) emptyList() else incidents.mapNotNull { incident ->
            if (incident.expiresAtMs?.let { it <= nowMs } == true || incident.reportedAtMs > nowMs + 60_000) return@mapNotNull null
            val point = Geo.projectOntoPolyline(route, incident.location) ?: return@mapNotNull null
            if (point.distanceMeters > 60) return@mapNotNull null
            if (incident.headings.isNotEmpty() && incident.headings.none {
                abs((it - point.bearingDeg + 540) % 360 - 180) <= 60
            }) return@mapNotNull null
            val meters = point.metersAlong - fix.metersAlong
            if (meters < 0 || meters > 2000) return@mapNotNull null
            Alert(0, incident.id, incident.kind, meters.roundToInt(), incident.location.lat,
                incident.location.lng, incident.description, incident.source, incident.reportedAtMs)
        }
        val next = ahead.associateBy { it.id }
        val messages = ArrayList<NavMessage>()
        (active.keys - next.keys).forEach { messages += AlertCleared(nextSeq(), it) }
        next.forEach { (id, alert) ->
            if (active[id] != alert) messages += alert.copy(seq = nextSeq())
        }
        active = next
        return messages
    }
}
