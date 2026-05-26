package com.trackwrite.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TrackStatsTest {
    @Test
    fun calculatesDurationPointCountAndDistance() {
        val start = Instant.parse("2026-05-26T10:00:00Z")
        val track = Track(
            id = "track-1",
            name = "Line",
            points = listOf(
                TrackPoint(GeoPoint(0.0, 0.0), start),
                TrackPoint(GeoPoint(0.0, 0.001), start.plusSeconds(60)),
            ),
        )

        val stats = track.stats()

        assertEquals(2, stats.pointCount)
        assertEquals(60, stats.duration.seconds)
        assertTrue(stats.distanceMeters in 111.0..112.0)
        assertTrue(stats.averageSpeedMetersPerSecond > 1.8)
    }
}
