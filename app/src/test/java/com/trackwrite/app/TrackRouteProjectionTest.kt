package com.trackwrite.app

import com.trackwrite.app.domain.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRouteProjectionTest {
    @Test
    fun emptyOrInvalidViewportProducesNoPoints() {
        val point = GeoPoint(latitude = 30.0, longitude = 120.0)

        assertTrue(projectRouteToViewport(emptyList(), 100.0, 100.0).isEmpty())
        assertTrue(projectRouteToViewport(listOf(point), 0.0, 100.0).isEmpty())
        assertTrue(projectRouteToViewport(listOf(point), 100.0, -1.0).isEmpty())
    }

    @Test
    fun singleAndRepeatedPointsAreCentered() {
        val point = GeoPoint(latitude = 30.0, longitude = 120.0)

        val single = projectRouteToViewport(listOf(point), 200.0, 100.0)
        val repeated = projectRouteToViewport(List(3) { point }, 200.0, 100.0)

        assertEquals(RouteViewportPoint(100.0, 50.0), single.single())
        assertTrue(repeated.all { it == RouteViewportPoint(100.0, 50.0) })
    }

    @Test
    fun horizontalRouteUsesWidthAndCentersVertically() {
        val projected = projectRouteToViewport(
            points = listOf(
                GeoPoint(latitude = 30.0, longitude = 120.0),
                GeoPoint(latitude = 30.0, longitude = 121.0),
            ),
            viewportWidth = 100.0,
            viewportHeight = 100.0,
        )

        assertEquals(10.0, projected.first().x, 0.0001)
        assertEquals(90.0, projected.last().x, 0.0001)
        assertTrue(projected.all { kotlin.math.abs(it.y - 50.0) < 0.0001 })
    }

    @Test
    fun verticalRouteUsesHeightAndCentersHorizontally() {
        val projected = projectRouteToViewport(
            points = listOf(
                GeoPoint(latitude = 30.0, longitude = 120.0),
                GeoPoint(latitude = 31.0, longitude = 120.0),
            ),
            viewportWidth = 100.0,
            viewportHeight = 100.0,
        )

        assertTrue(projected.all { kotlin.math.abs(it.x - 50.0) < 0.0001 })
        assertEquals(90.0, projected.first().y, 0.0001)
        assertEquals(10.0, projected.last().y, 0.0001)
    }

    @Test
    fun projectionPreservesAspectRatioAndViewportBounds() {
        val projected = projectRouteToViewport(
            points = listOf(
                GeoPoint(latitude = 0.0, longitude = 0.0),
                GeoPoint(latitude = 1.0, longitude = 1.0),
            ),
            viewportWidth = 200.0,
            viewportHeight = 100.0,
        )

        val routeWidth = projected.last().x - projected.first().x
        val routeHeight = projected.first().y - projected.last().y
        assertEquals(routeHeight, routeWidth, 0.02)
        assertTrue(projected.all { it.x in 0.0..200.0 && it.y in 0.0..100.0 })
        assertTrue(projected.all { it.x.isFinite() && it.y.isFinite() })
    }

    @Test
    fun longRouteIsSampledAndPreservesEndpoints() {
        val points = List(1_000) { index ->
            GeoPoint(latitude = 30.0 + index * 0.0001, longitude = 120.0 + index * 0.0002)
        }

        val full = projectRouteToViewport(points, 300.0, 200.0, maxDrawPoints = 1_000)
        val sampled = projectRouteToViewport(points, 300.0, 200.0, maxDrawPoints = 100)

        assertEquals(100, sampled.size)
        assertEquals(full.first().x, sampled.first().x, 0.0001)
        assertEquals(full.first().y, sampled.first().y, 0.0001)
        assertEquals(full.last().x, sampled.last().x, 0.0001)
        assertEquals(full.last().y, sampled.last().y, 0.0001)
    }

    @Test
    fun sampledRoutePreservesInteriorExtremes() {
        val points = List(100) { index ->
            GeoPoint(
                latitude = if (index == 47) 31.0 else 30.0,
                longitude = 120.0 + index * 0.0001,
            )
        }

        val sampled = projectRouteToViewport(points, 300.0, 200.0, maxDrawPoints = 10)

        assertTrue(sampled.any { kotlin.math.abs(it.y - 20.0) < 0.0001 })
    }

    @Test
    fun antimeridianCrossingRemainsContinuous() {
        val projected = projectRouteToViewport(
            points = listOf(
                GeoPoint(latitude = 10.0, longitude = 179.8),
                GeoPoint(latitude = 10.1, longitude = 179.9),
                GeoPoint(latitude = 10.2, longitude = -179.9),
            ),
            viewportWidth = 200.0,
            viewportHeight = 100.0,
        )

        assertTrue(projected.zipWithNext().all { (a, b) -> kotlin.math.abs(b.x - a.x) < 100.0 })
    }
}
