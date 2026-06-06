package com.trackwrite.app.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhotoGeotaggingTest {
    @Test
    fun gpsWritableMimeTypeAllowsExifSaveFormats() {
        assertEquals("image/jpeg", gpsWritableMimeType("image/jpeg", "photo.jpg"))
        assertEquals("image/jpeg", gpsWritableMimeType("image/jpg", "photo.jpg"))
        assertEquals("image/png", gpsWritableMimeType("image/png", "photo.png"))
        assertEquals("image/webp", gpsWritableMimeType("image/webp", "photo.webp"))
    }

    @Test
    fun gpsWritableMimeTypeFallsBackToExtensionForGenericMimeTypes() {
        assertEquals("image/jpeg", gpsWritableMimeType(null, "PHOTO.JPG"))
        assertEquals("image/jpeg", gpsWritableMimeType("application/octet-stream", "photo.jpeg"))
        assertEquals("image/webp", gpsWritableMimeType("image/*", "photo.WEBP"))
    }

    @Test
    fun gpsWritableMimeTypeRejectsRawFormats() {
        assertNull(gpsWritableMimeType("image/x-nikon-nef", "DSC_1446.NEF"))
        assertNull(gpsWritableMimeType("image/x-adobe-dng", "photo.dng"))
        assertNull(gpsWritableMimeType("image/x-nikon-nef", "misnamed.jpg"))
    }
}
