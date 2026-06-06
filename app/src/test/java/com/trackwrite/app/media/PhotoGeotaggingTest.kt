package com.trackwrite.app.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
}
