package com.trackwrite.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualLocationSearchTest {
    @Test
    fun searchResultsParseStructuredPoisAndFilterInvalidEntries() {
        val results = parseAmapSearchResults(
            """[
                {"name":"National Museum","address":"Dongcheng","latitude":39.9039,"longitude":116.3970},
                {"name":"","address":"Missing name","latitude":39.9,"longitude":116.4},
                {"name":"Invalid coordinate","address":"","latitude":91.0,"longitude":116.4},
                {"name":"Missing coordinate","address":""}
            ]""".trimIndent(),
        )

        assertEquals(
            listOf(
                AmapSearchResult(
                    name = "National Museum",
                    address = "Dongcheng",
                    latitude = 39.9039,
                    longitude = 116.3970,
                ),
            ),
            results,
        )
    }

    @Test
    fun searchResultsDistinguishEmptyPayloadFromMalformedPayload() {
        assertEquals(emptyList<AmapSearchResult>(), parseAmapSearchResults("[]"))
        assertNull(parseAmapSearchResults(null))
        assertNull(parseAmapSearchResults("not-json"))
        assertNull(parseAmapSearchResults("{}"))
    }

    @Test
    fun searchResultsAreLimitedToEightItems() {
        val validItems = (0 until 12).joinToString { index ->
            """{"name":"Place $index","address":"Address $index","latitude":39.9,"longitude":116.4}"""
        }
        val payload = """[{"name":"Invalid","latitude":91.0,"longitude":116.4},$validItems]"""

        assertEquals(8, parseAmapSearchResults(payload)?.size)
    }

    @Test
    fun searchResultsMayContainDuplicateProviderEntries() {
        val payload = """[
            {"name":"Same place","address":"Same address","latitude":39.9,"longitude":116.4},
            {"name":"Same place","address":"Same address","latitude":39.9,"longitude":116.4}
        ]""".trimIndent()

        assertEquals(2, parseAmapSearchResults(payload)?.size)
    }

    @Test
    fun mapHtmlKeepsSearchResultsOutOfTheWebDocument() {
        val html = mapHtml(
            amapKey = "test-key",
            securityJsCode = "test-security-code",
            text = ManualLocationMapText(
                failedToLoad = "load failed",
                mapErrorPrefix = "map error: ",
                notInitialized = "not initialized",
                mapSelection = "selection",
                mapTap = "map tap",
                notReady = "not ready",
                searchFailedPrefix = "search failed: ",
            ),
        )

        assertTrue(html.contains("TrackWrite.searchResults(JSON.stringify(payload))"))
        assertTrue(html.contains("if (status === 'no_data')"))
        assertTrue(html.contains("TrackWrite.searchResults('[]')"))
        assertTrue(html.contains("window.trackwriteSelectResult"))
        assertFalse(html.contains("id=\"panel\""))
        assertFalse(html.contains("panel: 'panel'"))
    }
}
