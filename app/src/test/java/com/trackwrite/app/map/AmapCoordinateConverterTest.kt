package com.trackwrite.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmapCoordinateConverterTest {
    @Test
    fun gcj02CoordinateInChinaConvertsToWgs84() {
        val result = AmapCoordinateConverter.gcj02ToWgs84(
            latitude = 39.91022649807321,
            longitude = 116.4037135824225,
        )

        assertEquals(39.908823, result.latitude, 0.0001)
        assertEquals(116.39747, result.longitude, 0.0001)
    }

    @Test
    fun coordinateOutsideChinaIsUnchanged() {
        val result = AmapCoordinateConverter.gcj02ToWgs84(
            latitude = 48.85837,
            longitude = 2.294481,
        )

        assertEquals(48.85837, result.latitude, 0.0)
        assertEquals(2.294481, result.longitude, 0.0)
    }

    @Test
    fun gcj02ConversionMovesChinaCoordinateByExpectedOffset() {
        val result = AmapCoordinateConverter.gcj02ToWgs84(
            latitude = 39.91022649807321,
            longitude = 116.4037135824225,
        )

        assertTrue(result.latitude < 39.91022649807321)
        assertTrue(result.longitude < 116.4037135824225)
    }

    @Test
    fun amapNavigationAllowsOnlyExpectedHttpsOrigin() {
        assertTrue(isAllowedAmapNavigation("https", "webapi.amap.com"))
        assertTrue(isAllowedAmapNavigation("HTTPS", "WEBAPI.AMAP.COM"))
        assertFalse(isAllowedAmapNavigation("http", "webapi.amap.com"))
        assertFalse(isAllowedAmapNavigation("https", "amap.com"))
        assertFalse(isAllowedAmapNavigation("https", "evil.example"))
    }

    @Test
    fun amapBridgeCoordinateValidationRejectsInvalidValues() {
        assertTrue(isValidAmapBridgeCoordinate(39.9, 116.4))
        assertFalse(isValidAmapBridgeCoordinate(Double.NaN, 116.4))
        assertFalse(isValidAmapBridgeCoordinate(39.9, Double.POSITIVE_INFINITY))
        assertFalse(isValidAmapBridgeCoordinate(91.0, 116.4))
        assertFalse(isValidAmapBridgeCoordinate(39.9, 181.0))
    }
}
