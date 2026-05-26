package com.trackwrite.app.domain

import java.time.Duration
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class TrackStats(
    val pointCount: Int,
    val duration: Duration,
    val distanceMeters: Double,
) {
    val averageSpeedMetersPerSecond: Double =
        if (duration.seconds <= 0) 0.0 else distanceMeters / duration.seconds
}

fun Track.stats(): TrackStats =
    TrackStats(
        pointCount = points.size,
        duration = duration,
        distanceMeters = points.zipWithNext().sumOf { (a, b) ->
            haversineMeters(a.position, b.position)
        },
    )

fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
    val earthRadiusMeters = 6_371_000.0
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val deltaLat = Math.toRadians(b.latitude - a.latitude)
    val deltaLon = Math.toRadians(b.longitude - a.longitude)

    val h = sin(deltaLat / 2).pow(2) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)

    return 2 * earthRadiusMeters * atan2(sqrt(h), sqrt(1 - h))
}
