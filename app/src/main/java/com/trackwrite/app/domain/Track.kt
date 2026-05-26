package com.trackwrite.app.domain

import java.time.Duration
import java.time.Instant

data class Track(
    val id: String,
    val name: String,
    val points: List<TrackPoint>,
) {
    init {
        require(points.zipWithNext().all { (a, b) -> !b.recordedAt.isBefore(a.recordedAt) }) {
            "Track points must be sorted by recorded time."
        }
    }

    val startTime: Instant? = points.firstOrNull()?.recordedAt
    val endTime: Instant? = points.lastOrNull()?.recordedAt
    val duration: Duration = when {
        points.size < 2 -> Duration.ZERO
        else -> Duration.between(points.first().recordedAt, points.last().recordedAt)
    }
}
