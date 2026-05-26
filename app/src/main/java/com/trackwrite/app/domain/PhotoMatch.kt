package com.trackwrite.app.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.abs

data class MatchOptions(
    val cameraOffset: Duration = Duration.ZERO,
    val maxTimeDifference: Duration = Duration.ofMinutes(5),
    val allowStartFallback: Boolean = true,
    val allowEndFallback: Boolean = true,
)

sealed interface PhotoMatch {
    val adjustedPhotoTime: Instant

    data class Matched(
        override val adjustedPhotoTime: Instant,
        val position: GeoPoint,
        val source: MatchSource,
        val timeDifference: Duration,
    ) : PhotoMatch

    data class Unmatched(
        override val adjustedPhotoTime: Instant,
        val reason: UnmatchedReason,
    ) : PhotoMatch
}

enum class MatchSource {
    ExactTrackPoint,
    InterpolatedTrackSegment,
    StartFallback,
    EndFallback,
}

enum class UnmatchedReason {
    EmptyTrack,
    BeforeTrackStart,
    AfterTrackEnd,
    ExceedsMaxTimeDifference,
}

class PhotoTrackMatcher {
    fun match(
        capturedAt: Instant,
        track: Track,
        options: MatchOptions = MatchOptions(),
    ): PhotoMatch {
        val adjustedTime = capturedAt.plus(options.cameraOffset)
        val points = track.points

        if (points.isEmpty()) {
            return PhotoMatch.Unmatched(adjustedTime, UnmatchedReason.EmptyTrack)
        }

        val first = points.first()
        val last = points.last()

        if (adjustedTime.isBefore(first.recordedAt)) {
            return fallbackOrUnmatched(
                adjustedTime = adjustedTime,
                point = first,
                allowed = options.allowStartFallback,
                maxTimeDifference = options.maxTimeDifference,
                source = MatchSource.StartFallback,
                unmatchedReason = UnmatchedReason.BeforeTrackStart,
            )
        }

        if (adjustedTime.isAfter(last.recordedAt)) {
            return fallbackOrUnmatched(
                adjustedTime = adjustedTime,
                point = last,
                allowed = options.allowEndFallback,
                maxTimeDifference = options.maxTimeDifference,
                source = MatchSource.EndFallback,
                unmatchedReason = UnmatchedReason.AfterTrackEnd,
            )
        }

        points.firstOrNull { it.recordedAt == adjustedTime }?.let { point ->
            return PhotoMatch.Matched(
                adjustedPhotoTime = adjustedTime,
                position = point.position,
                source = MatchSource.ExactTrackPoint,
                timeDifference = Duration.ZERO,
            )
        }

        val segment = points.zipWithNext().first { (before, after) ->
            adjustedTime.isAfter(before.recordedAt) && adjustedTime.isBefore(after.recordedAt)
        }
        val (before, after) = segment
        val nearestDifference = minOf(
            absoluteDuration(Duration.between(before.recordedAt, adjustedTime)),
            absoluteDuration(Duration.between(adjustedTime, after.recordedAt)),
        )

        if (nearestDifference > options.maxTimeDifference) {
            return PhotoMatch.Unmatched(adjustedTime, UnmatchedReason.ExceedsMaxTimeDifference)
        }

        return PhotoMatch.Matched(
            adjustedPhotoTime = adjustedTime,
            position = interpolate(before, after, adjustedTime),
            source = MatchSource.InterpolatedTrackSegment,
            timeDifference = nearestDifference,
        )
    }

    private fun fallbackOrUnmatched(
        adjustedTime: Instant,
        point: TrackPoint,
        allowed: Boolean,
        maxTimeDifference: Duration,
        source: MatchSource,
        unmatchedReason: UnmatchedReason,
    ): PhotoMatch {
        if (!allowed) {
            return PhotoMatch.Unmatched(adjustedTime, unmatchedReason)
        }

        val difference = absoluteDuration(Duration.between(point.recordedAt, adjustedTime))
        if (difference > maxTimeDifference) {
            return PhotoMatch.Unmatched(adjustedTime, UnmatchedReason.ExceedsMaxTimeDifference)
        }

        return PhotoMatch.Matched(
            adjustedPhotoTime = adjustedTime,
            position = point.position,
            source = source,
            timeDifference = difference,
        )
    }

    private fun interpolate(before: TrackPoint, after: TrackPoint, at: Instant): GeoPoint {
        val totalMillis = Duration.between(before.recordedAt, after.recordedAt).toMillis().toDouble()
        val elapsedMillis = Duration.between(before.recordedAt, at).toMillis().toDouble()
        val ratio = elapsedMillis / totalMillis

        return GeoPoint(
            latitude = before.position.latitude +
                (after.position.latitude - before.position.latitude) * ratio,
            longitude = before.position.longitude +
                (after.position.longitude - before.position.longitude) * ratio,
            altitudeMeters = interpolateAltitude(before.position, after.position, ratio),
        )
    }

    private fun interpolateAltitude(before: GeoPoint, after: GeoPoint, ratio: Double): Double? {
        val startAltitude = before.altitudeMeters
        val endAltitude = after.altitudeMeters
        if (startAltitude == null || endAltitude == null) return null
        return startAltitude + (endAltitude - startAltitude) * ratio
    }

    private fun absoluteDuration(duration: Duration): Duration =
        if (duration.isNegative) duration.negated() else duration
}
