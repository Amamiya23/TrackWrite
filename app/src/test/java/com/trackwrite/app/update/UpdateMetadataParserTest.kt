package com.trackwrite.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UpdateMetadataParserTest {
    @Test
    fun parsesMetadataAndSelectsApkAsset() {
        val release = UpdateMetadataParser.parseRelease(
            """
            {
              "tag_name": "v2.3",
              "name": "TrackWrite v2.3",
              "html_url": "https://github.com/Amamiya23/TrackWrite/releases/tag/v2.3",
              "assets": [
                {
                  "name": "trackwrite-update.json",
                  "browser_download_url": "https://example.test/trackwrite-update.json",
                  "size": 128
                },
                {
                  "name": "trackwrite-v2.3.apk",
                  "browser_download_url": "https://example.test/trackwrite-v2.3.apk",
                  "size": 4096
                }
              ]
            }
            """.trimIndent(),
        )
        val metadata = UpdateMetadataParser.parseMetadata(
            """
            {
              "versionName": "v2.3",
              "versionCode": 23,
              "apkAssetName": "trackwrite-v2.3.apk",
              "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            }
            """.trimIndent(),
        )

        val metadataAsset = UpdateMetadataParser.requireMetadataAsset(release)
        val candidate = UpdateMetadataParser.buildCandidate(release, metadata)

        assertEquals("trackwrite-update.json", metadataAsset.name)
        assertEquals("v2.3", candidate.metadata.versionName)
        assertEquals(23, candidate.metadata.versionCode)
        assertEquals("trackwrite-v2.3.apk", candidate.apkAsset.name)
        assertEquals("https://github.com/Amamiya23/TrackWrite/releases/tag/v2.3", candidate.releasePageUrl)
    }

    @Test
    fun rejectsMissingMetadataAsset() {
        val release = UpdateMetadataParser.parseRelease(
            """
            {
              "assets": [
                {
                  "name": "trackwrite-v2.3.apk",
                  "browser_download_url": "https://example.test/trackwrite-v2.3.apk"
                }
              ]
            }
            """.trimIndent(),
        )

        try {
            UpdateMetadataParser.requireMetadataAsset(release)
            fail("Expected missing metadata asset to be rejected.")
        } catch (error: MalformedUpdateReleaseException) {
            assertTrue(error.message.orEmpty().contains("trackwrite-update.json"))
        }
    }

    @Test
    fun rejectsMissingApkAssetNamedByMetadata() {
        val release = UpdateMetadataParser.parseRelease(
            """
            {
              "assets": [
                {
                  "name": "trackwrite-update.json",
                  "browser_download_url": "https://example.test/trackwrite-update.json"
                }
              ]
            }
            """.trimIndent(),
        )
        val metadata = UpdateMetadata(
            versionName = "v2.3",
            versionCode = 23,
            apkAssetName = "trackwrite-v2.3.apk",
            sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        )

        try {
            UpdateMetadataParser.buildCandidate(release, metadata)
            fail("Expected missing APK asset to be rejected.")
        } catch (error: MalformedUpdateReleaseException) {
            assertTrue(error.message.orEmpty().contains("trackwrite-v2.3.apk"))
        }
    }

    @Test
    fun rejectsMalformedMetadataJson() {
        try {
            UpdateMetadataParser.parseMetadata("{")
            fail("Expected malformed metadata JSON to be rejected.")
        } catch (error: MalformedUpdateReleaseException) {
            assertTrue(error.message.orEmpty().contains("JSON"))
        }
    }

    @Test
    fun rejectsMetadataWithInvalidSha256() {
        try {
            UpdateMetadataParser.parseMetadata(
                """
                {
                  "versionName": "v2.3",
                  "versionCode": 23,
                  "apkAssetName": "trackwrite-v2.3.apk",
                  "sha256": "not-a-sha"
                }
                """.trimIndent(),
            )
            fail("Expected invalid sha256 to be rejected.")
        } catch (error: MalformedUpdateReleaseException) {
            assertTrue(error.message.orEmpty().contains("sha256"))
        }
    }

    @Test
    fun rejectsMetadataWithNonIntegerVersionCode() {
        val invalidVersionCodes = listOf(
            """"23"""",
            "23.5",
            "0",
        )

        invalidVersionCodes.forEach { versionCode ->
            try {
                UpdateMetadataParser.parseMetadata(
                    """
                    {
                      "versionName": "v2.3",
                      "versionCode": $versionCode,
                      "apkAssetName": "trackwrite-v2.3.apk",
                      "sha256": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
                    }
                    """.trimIndent(),
                )
                fail("Expected invalid versionCode $versionCode to be rejected.")
            } catch (error: MalformedUpdateReleaseException) {
                assertTrue(error.message.orEmpty().contains("versionCode"))
            }
        }
    }

    @Test
    fun comparesUpdatesByVersionCode() {
        val metadata = UpdateMetadata(
            versionName = "v2.3",
            versionCode = 23,
            apkAssetName = "trackwrite-v2.3.apk",
            sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        )
        val candidate = UpdateCandidate(
            metadata = metadata,
            apkAsset = ReleaseAsset(
                name = "trackwrite-v2.3.apk",
                downloadUrl = "https://example.test/trackwrite-v2.3.apk",
                sizeBytes = 4096,
            ),
            releasePageUrl = null,
        )

        assertTrue(
            UpdateMetadataParser.decide(
                InstalledAppVersion(versionName = "v2.2", versionCode = 22),
                candidate,
            ) is UpdateDecision.Available,
        )
        assertTrue(
            UpdateMetadataParser.decide(
                InstalledAppVersion(versionName = "v2.3", versionCode = 23),
                candidate,
            ) is UpdateDecision.UpToDate,
        )
        assertTrue(
            UpdateMetadataParser.decide(
                InstalledAppVersion(versionName = "v2.4", versionCode = 24),
                candidate,
            ) is UpdateDecision.UpToDate,
        )
    }

    @Test
    fun upToDateDecisionKeepsInstalledAndLatestVersions() {
        val metadata = UpdateMetadata(
            versionName = "v2.3",
            versionCode = 23,
            apkAssetName = "trackwrite-v2.3.apk",
            sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        )
        val candidate = UpdateCandidate(
            metadata = metadata,
            apkAsset = ReleaseAsset(
                name = "trackwrite-v2.3.apk",
                downloadUrl = "https://example.test/trackwrite-v2.3.apk",
                sizeBytes = 4096,
            ),
            releasePageUrl = null,
        )
        val installed = InstalledAppVersion(versionName = "v2.4", versionCode = 24)

        val decision = UpdateMetadataParser.decide(installed, candidate)

        assertTrue(decision is UpdateDecision.UpToDate)
        val upToDate = decision as UpdateDecision.UpToDate
        assertEquals(installed, upToDate.installedVersion)
        assertEquals(metadata, upToDate.latestVersion)
    }
}
