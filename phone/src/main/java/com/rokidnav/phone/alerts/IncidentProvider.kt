package com.rokidnav.phone.alerts

import com.rokidnav.protocol.AlertKind
import com.rokidnav.protocol.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge

/**
 * Source of road alerts (police, cameras, crashes, closures...). Google's public APIs do NOT expose
 * police reports — see docs/DATA_SOURCES.md before implementing one. Each provider returns incidents
 * near the route; the coordinator turns them into protocol [com.rokidnav.protocol.Alert]s with
 * distance-ahead computed against the active route.
 */
interface IncidentProvider {
    val name: String

    /** Called when the route changes; providers may poll a corridor around it. */
    fun incidentsAlong(routePolyline: String): Flow<List<Incident>>
}

data class Incident(
    val id: String,
    val kind: AlertKind,
    val location: LatLng,
    val description: String,
    val reportedAtMs: Long,
)

class CompositeIncidentProvider(private val providers: List<IncidentProvider>) : IncidentProvider {
    override val name = "composite"
    override fun incidentsAlong(routePolyline: String): Flow<List<Incident>> =
        merge(*providers.map { it.incidentsAlong(routePolyline) }.toTypedArray())
}

/** Debug-only: drops a fake police alert ~1 km ahead so the glasses UI can be exercised. */
class MockIncidentProvider(private val enabled: Boolean) : IncidentProvider {
    override val name = "mock"
    override fun incidentsAlong(routePolyline: String): Flow<List<Incident>> {
        if (!enabled) return emptyFlow()
        return flow {
            // TODO(M7): pick a point ~1 km along the polyline and emit one POLICE incident.
            emit(emptyList())
        }
    }
}
