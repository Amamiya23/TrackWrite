package com.trackwrite.app.update

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

object UpdateMetadataParser {
    const val METADATA_ASSET_NAME = "trackwrite-update.json"

    private val sha256Pattern = Regex("^[a-fA-F0-9]{64}$")
    private val positiveIntegerPattern = Regex("^[1-9][0-9]*$")

    fun parseMetadata(json: String): UpdateMetadata =
        try {
            val root = JSONObject(json)
            val versionName = root.requiredString("versionName")
            val versionCode = root.requiredPositiveInt("versionCode")
            val apkAssetName = root.requiredString("apkAssetName")
            val sha256 = root.requiredString("sha256").lowercase(Locale.US)

            if (!sha256Pattern.matches(sha256)) {
                throw MalformedUpdateReleaseException("sha256 must be a 64-character hexadecimal digest.")
            }

            UpdateMetadata(
                versionName = versionName,
                versionCode = versionCode,
                apkAssetName = apkAssetName,
                sha256 = sha256,
            )
        } catch (error: MalformedUpdateReleaseException) {
            throw error
        } catch (error: JSONException) {
            throw MalformedUpdateReleaseException("Update metadata JSON is invalid.", error)
        }

    fun parseRelease(json: String): GitHubRelease =
        try {
            val root = JSONObject(json)
            val assets = root.optJSONArray("assets")
                ?: throw MalformedUpdateReleaseException("GitHub release is missing assets.")

            GitHubRelease(
                tagName = root.optionalString("tag_name"),
                name = root.optionalString("name"),
                body = root.optionalString("body"),
                htmlUrl = root.optionalString("html_url"),
                assets = assets.toReleaseAssets(),
            )
        } catch (error: MalformedUpdateReleaseException) {
            throw error
        } catch (error: JSONException) {
            throw MalformedUpdateReleaseException("GitHub release JSON is invalid.", error)
        }

    fun requireMetadataAsset(release: GitHubRelease): ReleaseAsset =
        release.assets.firstOrNull { it.name == METADATA_ASSET_NAME }
            ?: throw MalformedUpdateReleaseException("GitHub release is missing $METADATA_ASSET_NAME.")

    fun buildCandidate(release: GitHubRelease, metadata: UpdateMetadata): UpdateCandidate {
        val apkAsset = release.assets.firstOrNull { it.name == metadata.apkAssetName }
            ?: throw MalformedUpdateReleaseException("GitHub release is missing APK asset ${metadata.apkAssetName}.")
        return UpdateCandidate(
            metadata = metadata,
            apkAsset = apkAsset,
            releasePageUrl = release.htmlUrl,
        )
    }

    fun decide(installed: InstalledAppVersion, candidate: UpdateCandidate): UpdateDecision =
        if (candidate.metadata.versionCode > installed.versionCode) {
            UpdateDecision.Available(candidate)
        } else {
            UpdateDecision.UpToDate(
                installedVersion = installed,
                latestVersion = candidate.metadata,
            )
        }

    private fun JSONArray.toReleaseAssets(): List<ReleaseAsset> =
        List(length()) { index ->
            val asset = getJSONObject(index)
            ReleaseAsset(
                name = asset.requiredString("name"),
                downloadUrl = asset.requiredString("browser_download_url"),
                sizeBytes = asset.optionalLong("size"),
            )
        }

    private fun JSONObject.requiredString(name: String): String {
        if (!has(name) || isNull(name)) {
            throw MalformedUpdateReleaseException("$name is required.")
        }
        val value = get(name)
        if (value !is String) {
            throw MalformedUpdateReleaseException("$name must be a string.")
        }
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            throw MalformedUpdateReleaseException("$name is required.")
        }
        return trimmed
    }

    private fun JSONObject.requiredPositiveInt(name: String): Int {
        if (!has(name) || isNull(name)) {
            throw MalformedUpdateReleaseException("$name is required.")
        }
        val value = get(name)
        if (value !is Number) {
            throw MalformedUpdateReleaseException("$name must be a positive integer.")
        }
        val numberText = value.toString()
        if (!positiveIntegerPattern.matches(numberText)) {
            throw MalformedUpdateReleaseException("$name must be a positive integer.")
        }
        val parsed = numberText.toLongOrNull()
            ?: throw MalformedUpdateReleaseException("$name is too large.")
        if (parsed > Int.MAX_VALUE) {
            throw MalformedUpdateReleaseException("$name is too large.")
        }
        return parsed.toInt()
    }

    private fun JSONObject.optionalString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        val value = get(name)
        if (value !is String) {
            throw MalformedUpdateReleaseException("$name must be a string.")
        }
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            return null
        }
        return trimmed
    }

    private fun JSONObject.optionalLong(name: String): Long? =
        if (has(name) && !isNull(name)) {
            optLong(name, -1L).takeIf { it >= 0L }
        } else {
            null
        }
}
