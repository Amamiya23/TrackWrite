package com.trackwrite.app.update

data class InstalledAppVersion(
    val versionName: String,
    val versionCode: Int,
)

data class UpdateMetadata(
    val versionName: String,
    val versionCode: Int,
    val apkAssetName: String,
    val sha256: String,
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long?,
)

data class GitHubRelease(
    val tagName: String?,
    val name: String?,
    val body: String?,
    val htmlUrl: String?,
    val assets: List<ReleaseAsset>,
)

data class UpdateCandidate(
    val metadata: UpdateMetadata,
    val apkAsset: ReleaseAsset,
    val releasePageUrl: String?,
)

sealed interface UpdateDecision {
    data class Available(val candidate: UpdateCandidate) : UpdateDecision
    data class UpToDate(
        val installedVersion: InstalledAppVersion,
        val latestVersion: UpdateMetadata,
    ) : UpdateDecision
}

open class UpdateException(message: String, cause: Throwable? = null) : Exception(message, cause)

class NoUpdateReleaseException : UpdateException("No published GitHub release was found.")

class MalformedUpdateReleaseException(message: String, cause: Throwable? = null) : UpdateException(message, cause)

class UpdateNetworkException(message: String, cause: Throwable? = null) : UpdateException(message, cause)
