package com.rokidnav.phone.traffic

import com.rokidnav.phone.nav.RouteOption
import com.rokidnav.protocol.LatLng
import okhttp3.OkHttpClient

/**
 * Routes API (REST) — used for the route picker and for periodic detour checks.
 *
 * POST https://routes.googleapis.com/directions/v2:computeRoutes
 * Headers: X-Goog-Api-Key, X-Goog-FieldMask:
 *   routes.routeToken,routes.duration,routes.staticDuration,routes.distanceMeters,
 *   routes.polyline.encodedPolyline,routes.description,routes.travelAdvisory.speedReadingIntervals
 * Body: origin/destination (location or placeId), travelMode=DRIVE,
 *   routingPreference=TRAFFIC_AWARE_OPTIMAL, computeAlternativeRoutes=true,
 *   extraComputations=["TRAFFIC_ON_POLYLINE"], plus heading on origin when moving
 *   (origin.location.heading) so detours don't suggest a U-turn.
 *
 * routeToken is what lets the Navigation SDK follow the exact route shown to the user.
 */
class RoutesApiClient(
    private val apiKey: String,
    private val http: OkHttpClient = OkHttpClient(),
) {
    suspend fun computeRoutes(
        origin: LatLng,
        originHeadingDeg: Int?,
        destination: LatLng,
        destinationPlaceId: String?,
        alternatives: Boolean = true,
    ): Result<List<RouteOption>> {
        // TODO(M4): build JSON body, execute on Dispatchers.IO, parse with kotlinx.serialization.
        // Durations come back as strings like "1234s".
        return Result.failure(NotImplementedError("RoutesApiClient.computeRoutes"))
    }
}
