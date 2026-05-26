package com.trackwrite.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GpxCodecTest {
    private val codec = GpxCodec()

    @Test
    fun encodesAndDecodesTrackPoints() {
        val track = Track(
            id = "track-1",
            name = "Photo day",
            points = listOf(
                TrackPoint(
                    position = GeoPoint(latitude = 30.1, longitude = 120.2, altitudeMeters = 15.0),
                    recordedAt = Instant.parse("2026-05-26T10:00:00Z"),
                ),
                TrackPoint(
                    position = GeoPoint(latitude = 30.2, longitude = 120.3),
                    recordedAt = Instant.parse("2026-05-26T10:01:00Z"),
                ),
            ),
        )

        val xml = codec.encode(track)
        val decoded = codec.decode("imported", xml)

        assertTrue(xml.contains("TrackWrite"))
        assertEquals("imported", decoded.id)
        assertEquals("Photo day", decoded.name)
        assertEquals(2, decoded.points.size)
        assertEquals(30.1, decoded.points[0].position.latitude, 0.0)
        assertEquals(15.0, decoded.points[0].position.altitudeMeters ?: -1.0, 0.0)
    }
}
