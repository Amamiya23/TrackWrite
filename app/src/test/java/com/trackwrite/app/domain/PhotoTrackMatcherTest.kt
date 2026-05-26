package com.trackwrite.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class PhotoTrackMatcherTest {
    private val matcher = PhotoTrackMatcher()
    private val start = Instant.parse("2026-05-26T10:00:00Z")
    private val track = Track(
        id = "track-1",
        name = "Morning walk",
        points = listOf(
            TrackPoint(GeoPoint(30.0, 120.0, 10.0), start),
            TrackPoint(GeoPoint(31.0, 121.0, 20.0), start.plusSeconds(60)),
        ),
    )

    @Test
    fun exactTrackPointUsesPointDirectly() {
        val result = matcher.match(start, track)

        assertTrue(result is PhotoMatch.Matched)
        result as PhotoMatch.Matched
        assertEquals(MatchSource.ExactTrackPoint, result.source)
        assertEquals(30.0, result.position.latitude, 0.0)
        assertEquals(Duration.ZERO, result.timeDifference)
    }

    @Test
    fun betweenTwoPointsInterpolatesLocation() {
        val result = matcher.match(start.plusSeconds(30), track)

        assertTrue(result is PhotoMatch.Matched)
        result as PhotoMatch.Matched
        assertEquals(MatchSource.InterpolatedTrackSegment, result.source)
        assertEquals(30.5, result.position.latitude, 0.000001)
        assertEquals(120.5, result.position.longitude, 0.000001)
        assertEquals(15.0, result.position.altitudeMeters ?: -1.0, 0.000001)
    }

    @Test
    fun cameraOffsetIsAppliedBeforeMatching() {
        val result = matcher.match(
            capturedAt = start.minusSeconds(60),
            track = track,
            options = MatchOptions(cameraOffset = Duration.ofSeconds(60)),
        )

        assertTrue(result is PhotoMatch.Matched)
        result as PhotoMatch.Matched
        assertEquals(MatchSource.ExactTrackPoint, result.source)
    }

    @Test
    fun beforeStartCanUseEndpointFallbackWithinLimit() {
        val result = matcher.match(start.minusSeconds(30), track)

        assertTrue(result is PhotoMatch.Matched)
        result as PhotoMatch.Matched
        assertEquals(MatchSource.StartFallback, result.source)
        assertEquals(Duration.ofSeconds(30), result.timeDifference)
    }

    @Test
    fun afterEndFallbackRespectsMaxDifference() {
        val result = matcher.match(start.plusSeconds(601), track)

        assertTrue(result is PhotoMatch.Unmatched)
        result as PhotoMatch.Unmatched
        assertEquals(UnmatchedReason.ExceedsMaxTimeDifference, result.reason)
    }

    @Test
    fun interpolationRespectsMaxDifference() {
        val sparseTrack = Track(
            id = "sparse",
            name = "Sparse",
            points = listOf(
                TrackPoint(GeoPoint(30.0, 120.0), start),
                TrackPoint(GeoPoint(31.0, 121.0), start.plusSeconds(1_200)),
            ),
        )

        val result = matcher.match(start.plusSeconds(600), sparseTrack)

        assertTrue(result is PhotoMatch.Unmatched)
        result as PhotoMatch.Unmatched
        assertEquals(UnmatchedReason.ExceedsMaxTimeDifference, result.reason)
    }
}
