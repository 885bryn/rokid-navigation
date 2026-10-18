package com.rokidnav.protocol

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(val lat: Double, val lng: Double)

/** Google encoded polyline format (precision 5), as returned by Routes API / Navigation SDK. */
object PolylineCodec {
    fun decode(encoded: String): List<LatLng> {
        val out = ArrayList<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var result = 0
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            out += LatLng(lat / 1e5, lng / 1e5)
        }
        return out
    }

    fun encode(points: List<LatLng>): String {
        val sb = StringBuilder()
        var prevLat = 0
        var prevLng = 0
        for (p in points) {
            val lat = Math.round(p.lat * 1e5).toInt()
            val lng = Math.round(p.lng * 1e5).toInt()
            encodeValue(lat - prevLat, sb)
            encodeValue(lng - prevLng, sb)
            prevLat = lat
            prevLng = lng
        }
        return sb.toString()
    }

    private fun encodeValue(value: Int, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }
}

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    /**
     * Local flat projection around [origin] in meters (x = east, y = north). Accurate enough for the
     * few-km window the glasses map shows; the HUD renderer rotates these by heading.
     */
    fun toLocalMeters(origin: LatLng, p: LatLng): Pair<Double, Double> {
        val x = Math.toRadians(p.lng - origin.lng) * EARTH_RADIUS_M * cos(Math.toRadians(origin.lat))
        val y = Math.toRadians(p.lat - origin.lat) * EARTH_RADIUS_M
        return x to y
    }

    fun bearingDeg(a: LatLng, b: LatLng): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (atan2(y, x) * 180 / PI + 360) % 360
    }
}
