package com.trackwrite.app

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackNameFormatterTest {
    @Test
    fun formatsDateAndTimeInTheProvidedTimeZone() {
        val name = formatDefaultTrackName(
            instant = Instant.parse("2026-07-11T07:35:57Z"),
            zoneId = ZoneId.of("Asia/Shanghai"),
            locale = Locale.US,
        )

        assertEquals("2026-07-11 15:35:57", name)
    }
}
