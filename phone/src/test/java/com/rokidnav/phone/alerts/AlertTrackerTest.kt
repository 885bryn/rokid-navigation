package com.rokidnav.phone.alerts

import com.rokidnav.protocol.*
import org.junit.Assert.*
import org.junit.Test

class AlertTrackerTest {
    private val route = listOf(LatLng(0.0, 0.0), LatLng(0.0, 0.05))
    private val incident = Incident("p1", AlertKind.POLICE, LatLng(0.0, 0.01), "Police reported", 1000, "WzSabre")
    private var seq = 0L
    private fun position(lon: Double) = PositionUpdate(0, 0.0, lon, 90f, 10f, timestampMs = 2000)
    @Test fun countsDownClearsAfterPassingAndDoesNotReplayClearedAlert() {
        val tracker = AlertTracker(route)
        val first = tracker.update(listOf(incident), position(0.0), 2000) { ++seq }.single() as Alert
        assertEquals(1112, first.distanceAheadMeters)
        assertEquals("WzSabre", first.source)
        val next = tracker.update(listOf(incident), position(0.005), 2000) { ++seq }.single() as Alert
        assertEquals(556, next.distanceAheadMeters)
        assertTrue(tracker.update(listOf(incident), position(0.005), 2000) { ++seq }.isEmpty())
        assertEquals("p1", (tracker.update(listOf(incident), position(0.011), 2000) { ++seq }.single() as AlertCleared).id)
        assertTrue(tracker.update(listOf(incident), position(0.011), 2000) { ++seq }.isEmpty())
    }
    @Test fun rejectsSideRoadOppositeDirectionExpiredFutureAndDistantReports() {
        val tracker = AlertTracker(route)
        val rejected = listOf(
            incident.copy(location = LatLng(0.001, 0.01)),
            incident.copy(headings = listOf(270.0)),
            incident.copy(expiresAtMs = 1500),
            incident.copy(reportedAtMs = 100_000),
            incident.copy(location = LatLng(0.0, 0.04)),
        )
        assertTrue(tracker.update(rejected, position(0.0), 2000) { ++seq }.isEmpty())
        assertTrue(tracker.update(listOf(incident.copy(headings = listOf(90.0))), position(0.0), 2000) { ++seq }.single() is Alert)
        assertTrue(tracker.update(emptyList(), position(0.0), 2000) { ++seq }.single() is AlertCleared)
    }
}
