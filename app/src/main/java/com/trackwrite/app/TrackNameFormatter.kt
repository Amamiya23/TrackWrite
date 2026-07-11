package com.trackwrite.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val DEFAULT_TRACK_NAME_PATTERN = "yyyy-MM-dd HH:mm:ss"

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
