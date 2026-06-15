package com.trackwrite.app.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GitHubReleaseUpdateSource(
    private val latestReleaseUrl: String = DEFAULT_LATEST_RELEASE_URL,
) {
    fun fetchLatestUpdate(): UpdateCandidate {
        val release = UpdateMetadataParser.parseRelease(readText(latestReleaseUrl))
        val metadataAsset = UpdateMetadataParser.requireMetadataAsset(release)
        val metadata = UpdateMetadataParser.parseMetadata(readText(metadataAsset.downloadUrl))
        return UpdateMetadataParser.buildCandidate(release, metadata)
    }

    private fun readText(url: String): String {
        val connection = try {
            openConnection(url)
        } catch (error: IOException) {
            throw UpdateNetworkException("Could not fetch update information.", error)
        }
        return try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND && url == latestReleaseUrl) {
                throw NoUpdateReleaseException()
            }
            if (responseCode !in 200..299) {
                throw UpdateNetworkException("HTTP $responseCode while fetching update information.")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (error: UpdateException) {
            throw error
        } catch (error: IOException) {
            throw UpdateNetworkException("Could not fetch update information.", error)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
            setRequestProperty("User-Agent", "TrackWrite-Android")
        }

    private companion object {
        const val DEFAULT_LATEST_RELEASE_URL = "https://api.github.com/repos/Amamiya23/TrackWrite/releases/latest"
    }
}
