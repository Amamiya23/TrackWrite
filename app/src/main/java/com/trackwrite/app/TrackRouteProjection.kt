package com.trackwrite.app

import com.trackwrite.app.domain.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

internal data class RouteViewportPoint(
    val x: Double,
    val y: Double,
)

internal fun projectRouteToViewport(
    points: List<GeoPoint>,
    viewportWidth: Double,
    viewportHeight: Double,
    paddingFraction: Double = 0.10,
    maxDrawPoints: Int = 600,
): List<RouteViewportPoint> {
    if (points.isEmpty() || viewportWidth <= 0.0 || viewportHeight <= 0.0) return emptyList()
    require(maxDrawPoints >= 6) { "maxDrawPoints must be at least 6." }

    val referenceLatitudeRadians = (points.sumOf { it.latitude } / points.size) * PI / 180.0
    val longitudeScale = cos(referenceLatitudeRadians)
    val unwrappedLongitudes = unwrapLongitudes(points)
    val projected = points.mapIndexed { index, point ->
        ProjectedRoutePoint(
            x = unwrappedLongitudes[index] * PI / 180.0 * longitudeScale,
            y = point.latitude * PI / 180.0,
        )
    }

    val minX = projected.minOf { it.x }
    val maxX = projected.maxOf { it.x }
    val minY = projected.minOf { it.y }
    val maxY = projected.maxOf { it.y }
    val spanX = maxX - minX
    val spanY = maxY - minY
    val padding = paddingFraction.coerceIn(0.0, 0.45)
    val availableWidth = viewportWidth * (1.0 - padding * 2.0)
    val availableHeight = viewportHeight * (1.0 - padding * 2.0)
    val scaleX = if (spanX > ROUTE_EPSILON) availableWidth / spanX else Double.POSITIVE_INFINITY
    val scaleY = if (spanY > ROUTE_EPSILON) availableHeight / spanY else Double.POSITIVE_INFINITY
    val scale = min(scaleX, scaleY).takeIf { it.isFinite() } ?: 1.0
    val routeWidth = spanX * scale
    val routeHeight = spanY * scale
    val left = (viewportWidth - routeWidth) / 2.0
    val top = (viewportHeight - routeHeight) / 2.0

    return sampleProjectedPoints(projected, maxDrawPoints).map { point ->
        RouteViewportPoint(
            x = (left + (point.x - minX) * scale).coerceIn(0.0, viewportWidth),
            y = (top + (maxY - point.y) * scale).coerceIn(0.0, viewportHeight),
        )
    }
}

private data class ProjectedRoutePoint(
    val x: Double,
    val y: Double,
)

private fun unwrapLongitudes(points: List<GeoPoint>): List<Double> {
    val result = ArrayList<Double>(points.size)
    var previousRaw = points.first().longitude
    var current = previousRaw
    result += current

    for (index in 1 until points.size) {
        val point = points[index]
        var delta = point.longitude - previousRaw
        while (delta > 180.0) delta -= 360.0
        while (delta < -180.0) delta += 360.0
        current += delta
        result += current
        previousRaw = point.longitude
    }
    return result
}

private fun sampleProjectedPoints(
    points: List<ProjectedRoutePoint>,
    maxDrawPoints: Int,
): List<ProjectedRoutePoint> {
    if (points.size <= maxDrawPoints) return points
    val lastIndex = points.lastIndex
    val selectedIndices = linkedSetOf(
        0,
        lastIndex,
        points.indices.minBy { points[it].x },
        points.indices.maxBy { points[it].x },
        points.indices.minBy { points[it].y },
        points.indices.maxBy { points[it].y },
    )
    repeat(maxDrawPoints) { sampleIndex ->
        if (selectedIndices.size < maxDrawPoints) {
            selectedIndices += (sampleIndex.toDouble() * lastIndex / (maxDrawPoints - 1)).roundToInt()
        }
    }
    var candidateIndex = 0
    while (selectedIndices.size < maxDrawPoints) {
        selectedIndices += candidateIndex
        candidateIndex += 1
    }
    return selectedIndices.sorted().map(points::get)
}

private const val ROUTE_EPSILON = 1e-12
