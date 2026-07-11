package com.trackwrite.app

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

private const val DEFAULT_NAME_START_TIME_TOLERANCE_SECONDS = 60 * 60L

private const val DEFAULT_TRACK_NAME_PATTERN = "yyyy-MM-dd HH:mm:ss"
private val LEGACY_TRACK_NAME_PREFIXES = listOf("Recording ", "记录 ")

internal data class TrackHistoryLabel(
    val title: String,
    val subtitle: String? = null,
)

internal fun formatDefaultTrackName(
    instant: Instant,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    return DateTimeFormatter
        .ofPattern(DEFAULT_TRACK_NAME_PATTERN, locale)
        .withZone(zoneId)
        .format(instant)
}

internal fun formatTrackHistoryLabel(
    name: String,
    startTime: Instant?,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): TrackHistoryLabel {
    val automaticNameTime = parseAutomaticTrackName(name, startTime, zoneId, locale)
    return if (automaticNameTime != null) {
        TrackHistoryLabel(title = formatHistoryDateTime(automaticNameTime, zoneId, locale))
    } else {
        TrackHistoryLabel(
            title = name,
            subtitle = startTime?.let { formatHistoryDateTime(it, zoneId, locale) },
        )
    }
}

internal fun formatCompactDuration(duration: Duration): String {
    val totalSeconds = duration.seconds.coerceAtLeast(0L)
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours == 0L) {
        "%d:%02d".format(Locale.ROOT, minutes, seconds)
    } else {
        "%d:%02d:%02d".format(Locale.ROOT, hours, minutes, seconds)
    }
}

private fun parseAutomaticTrackName(
    name: String,
    startTime: Instant?,
    zoneId: ZoneId,
    locale: Locale,
): Instant? {
    parseInstant(name)?.let { return it }
    LEGACY_TRACK_NAME_PREFIXES.forEach { prefix ->
        if (name.startsWith(prefix)) {
            parseInstant(name.removePrefix(prefix))?.let { return it }
        }
    }
    val defaultNameTime = listOf(locale, Locale.ROOT).distinct().firstNotNullOfOrNull { parserLocale ->
        try {
            LocalDateTime.parse(
                name,
                DateTimeFormatter.ofPattern(DEFAULT_TRACK_NAME_PATTERN, parserLocale),
            ).atZone(zoneId).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
    return defaultNameTime?.takeIf { parsedTime ->
        startTime == null ||
            kotlin.math.abs(Duration.between(parsedTime, startTime).seconds) <= DEFAULT_NAME_START_TIME_TOLERANCE_SECONDS
    }
}

private fun parseInstant(value: String): Instant? =
    try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

private fun formatHistoryDateTime(
    instant: Instant,
    zoneId: ZoneId,
    locale: Locale,
): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zoneId)
        .format(instant)
