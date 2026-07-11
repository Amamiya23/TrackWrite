package com.trackwrite.app

import java.time.Duration
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

    @Test
    fun localizesCurrentAutomaticNameForHistory() {
        val label = formatTrackHistoryLabel(
            name = "2026-07-11 15:35:57",
            startTime = null,
            zoneId = ZoneId.of("Asia/Shanghai"),
            locale = Locale.US,
        )

        assertEquals("Jul 11, 2026, 3:35 PM", label.title.normalizedSpacing())
        assertEquals(null, label.subtitle)
    }

    @Test
    fun usesAutomaticNameTimeInsteadOfReplacingItWithFirstPointTime() {
        val label = formatTrackHistoryLabel(
            name = "2026-07-11 15:35:57",
            startTime = Instant.parse("2026-07-11T07:40:57Z"),
            zoneId = ZoneId.of("Asia/Shanghai"),
            locale = Locale.US,
        )

        assertEquals("Jul 11, 2026, 3:35 PM", label.title.normalizedSpacing())
        assertEquals(null, label.subtitle)
    }

    @Test
    fun preservesDateShapedCustomNameWhenItDoesNotMatchTrackTime() {
        val label = formatTrackHistoryLabel(
            name = "2020-01-02 03:04:05",
            startTime = Instant.parse("2026-07-11T07:35:57Z"),
            zoneId = ZoneId.of("Asia/Shanghai"),
            locale = Locale.US,
        )

        assertEquals("2020-01-02 03:04:05", label.title)
        assertEquals("Jul 11, 2026, 3:35 PM", label.subtitle?.normalizedSpacing())
    }

    @Test
    fun localizesLegacyPrefixedAndPlainIsoNames() {
        val prefixed = formatTrackHistoryLabel(
            name = "记录 2026-07-11T07:35:57.873128Z",
            startTime = null,
            zoneId = ZoneId.of("Asia/Shanghai"),
            locale = Locale.US,
        )
        val plain = formatTrackHistoryLabel(
            name = "2026-07-11T07:35:57Z",
            startTime = null,
            zoneId = ZoneId.of("Asia/Shanghai"),
            locale = Locale.US,
        )
        val englishPrefixed = formatTrackHistoryLabel(
            name = "Recording 2026-07-11T07:35:57Z",
            startTime = null,
            zoneId = ZoneId.of("Asia/Shanghai"),
            locale = Locale.US,
        )

        assertEquals("Jul 11, 2026, 3:35 PM", prefixed.title.normalizedSpacing())
        assertEquals("Jul 11, 2026, 3:35 PM", plain.title.normalizedSpacing())
        assertEquals("Jul 11, 2026, 3:35 PM", englishPrefixed.title.normalizedSpacing())
    }

    @Test
    fun preservesCustomNameAndAddsTrackTime() {
        val label = formatTrackHistoryLabel(
            name = "West Lake photo walk",
            startTime = Instant.parse("2026-07-11T07:35:57Z"),
            zoneId = ZoneId.of("Asia/Shanghai"),
            locale = Locale.US,
        )

        assertEquals("West Lake photo walk", label.title)
        assertEquals("Jul 11, 2026, 3:35 PM", label.subtitle?.normalizedSpacing())
    }

    @Test
    fun leavesCustomNameWithoutPointsUnchanged() {
        val label = formatTrackHistoryLabel(
            name = "Imported route",
            startTime = null,
            zoneId = ZoneId.of("Asia/Shanghai"),
            locale = Locale.US,
        )

        assertEquals("Imported route", label.title)
        assertEquals(null, label.subtitle)
    }

    @Test
    fun formatsCompactDurationsWithoutTruncatingCoreValue() {
        assertEquals("0:00", formatCompactDuration(Duration.ZERO))
        assertEquals("0:09", formatCompactDuration(Duration.ofSeconds(9)))
        assertEquals("59:59", formatCompactDuration(Duration.ofSeconds(3_599)))
        assertEquals("1:00:00", formatCompactDuration(Duration.ofHours(1)))
        assertEquals("27:04:05", formatCompactDuration(Duration.ofHours(27).plusMinutes(4).plusSeconds(5)))
    }

    private fun String.normalizedSpacing(): String = replace('\u202f', ' ')
}
