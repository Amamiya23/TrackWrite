package com.trackwrite.app.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PhotoGeotaggingTest {
    @Test
    fun gpsWritableMimeTypeAllowsExifSaveFormats() {
        assertEquals("image/jpeg", gpsWritableMimeType("image/jpeg", "photo.jpg"))
        assertEquals("image/jpeg", gpsWritableMimeType("image/jpg", "photo.jpg"))
        assertEquals("image/jpeg", gpsWritableMimeType("image/pjpeg", "PHOTO.JPG"))
        assertEquals("image/jpeg", gpsWritableMimeType("image/x-jpeg", "PHOTO.JPEG"))
        assertEquals("image/jpeg", gpsWritableMimeType("IMAGE/X-JPG; charset=binary", "PHOTO.JPG"))
        assertEquals("image/png", gpsWritableMimeType("image/png", "photo.png"))
        assertEquals("image/webp", gpsWritableMimeType("image/webp", "photo.webp"))
    }

    @Test
    fun gpsWritableMimeTypeFallsBackToExtensionForGenericMimeTypes() {
        assertEquals("image/jpeg", gpsWritableMimeType(null, "PHOTO.JPG"))
        assertEquals("image/jpeg", gpsWritableMimeType("application/octet-stream", "photo.jpeg"))
        assertEquals("image/jpeg", gpsWritableMimeType("application/octet-stream", "PHOTO.JPEG"))
        assertEquals("image/webp", gpsWritableMimeType("image/*", "photo.WEBP"))
    }

    @Test
    fun gpsWritableMimeTypeRejectsRawFormats() {
        assertNull(gpsWritableMimeType("image/x-nikon-nef", "DSC_1446.NEF"))
        assertNull(gpsWritableMimeType("image/x-adobe-dng", "photo.dng"))
        assertNull(gpsWritableMimeType("image/jpeg", "DSC_1446.NEF"))
        assertNull(gpsWritableMimeType("image/pjpeg", "PHOTO.DNG"))
        assertNull(gpsWritableMimeType("image/x-nikon-nef", "misnamed.jpg"))
    }

    @Test
    fun gpsTagHelpersMatchExifApiSemantics() {
        assertEquals("N", gpsLatitudeRef(40.0))
        assertEquals("S", gpsLatitudeRef(-31.25))
        assertEquals("E", gpsLongitudeRef(121.5))
        assertEquals("W", gpsLongitudeRef(-73.0))
        assertEquals("0", gpsAltitudeRef(12.75))
        assertEquals("1", gpsAltitudeRef(-12.75))
        assertArrayEquals(byteArrayOf(2, 3, 0, 0), gpsExifVersionBytes())
    }

    @Test
    fun writtenGpsVerificationDoesNotRequireGpsVersionReadBack() {
        assertFalse(gpsVersionReadBackBlocksWrite(null))
        assertFalse(gpsVersionReadBackBlocksWrite(gpsExifVersionBytes()))
        assertFalse(gpsVersionReadBackBlocksWrite(byteArrayOf(0, 0, 0, 0)))
    }

    @Test
    fun imageSignatureMatchesSupportedFormats() {
        assertTrue(imageSignatureMatches("image/jpeg", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertTrue(
            imageSignatureMatches(
                "image/png",
                byteArrayOf(
                    0x89.toByte(),
                    0x50,
                    0x4E,
                    0x47,
                    0x0D,
                    0x0A,
                    0x1A,
                    0x0A,
                ),
            ),
        )
        assertTrue(
            imageSignatureMatches(
                "image/webp",
                byteArrayOf(
                    0x52,
                    0x49,
                    0x46,
                    0x46,
                    0x00,
                    0x00,
                    0x00,
                    0x00,
                    0x57,
                    0x45,
                    0x42,
                    0x50,
                ),
            ),
        )
    }

    @Test
    fun imageSignatureRejectsMismatchedFormats() {
        assertFalse(imageSignatureMatches("image/jpeg", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertFalse(imageSignatureMatches("image/png", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertFalse(imageSignatureMatches("image/webp", byteArrayOf(0x52, 0x49, 0x46, 0x46)))
        assertFalse(imageSignatureMatches("image/heic", byteArrayOf(0x00, 0x00, 0x00, 0x18)))
    }

    @Test
    fun backupDisplayNamePrefixesTimestampAndSanitizesInvalidCharacters() {
        val name = backupDisplayName(
            """trip:day/one\photo?.jpg""",
            Instant.parse("2026-06-06T08:09:10.123Z"),
        )

        assertTrue(name.endsWith("-trip_day_one_photo_.jpg"))
        assertFalse(name.contains(":"))
        assertFalse(name.contains("/"))
        assertFalse(name.contains("\\"))
        assertFalse(name.contains("?"))
    }
}
