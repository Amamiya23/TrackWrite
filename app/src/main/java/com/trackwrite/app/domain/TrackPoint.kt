package com.trackwrite.app.domain

import java.time.Instant

data class TrackPoint(
    val position: GeoPoint,
    val recordedAt: Instant,
)
