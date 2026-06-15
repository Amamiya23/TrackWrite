package com.trackwrite.app.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

class UpdateDownloader(
    private val cacheDir: File,
) {
    fun download(candidate: UpdateCandidate): File {
        val updatesDir = File(cacheDir, "updates")
        if (!updatesDir.exists() && !updatesDir.mkdirs()) {
            throw UpdateDownloadException("Could not create update cache directory.")
        }

        val target = File(updatesDir, safeApkName(candidate.metadata))
        val temp = try {
            File.createTempFile("trackwrite-update-", ".apk", updatesDir)
        } catch (error: IOException) {
            throw UpdateDownloadException("Could not create update cache file.", error)
        }
        var targetTouched = false
        var connection: HttpURLConnection? = null

        try {
            connection = openConnection(candidate.apkAsset.downloadUrl)
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw UpdateDownloadException("HTTP $responseCode while downloading update APK.")
            }

            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }

            val actualSha256 = digest.digest().toHex()
            if (!actualSha256.equals(candidate.metadata.sha256, ignoreCase = true)) {
                throw UpdateChecksumException(candidate.metadata.sha256, actualSha256)
            }

            if (target.exists() && !target.delete()) {
                throw UpdateDownloadException("Could not replace cached update APK.")
            }
            targetTouched = true
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                if (!temp.delete()) {
                    temp.deleteOnExit()
                }
            }
            return target
        } catch (error: UpdateException) {
            temp.delete()
            if (targetTouched) target.delete()
            throw error
        } catch (error: IOException) {
            temp.delete()
            if (targetTouched) target.delete()
            throw UpdateDownloadException("Could not download update APK.", error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream")
            setRequestProperty("User-Agent", "TrackWrite-Android")
        }

    private fun safeApkName(metadata: UpdateMetadata): String {
        val version = metadata.versionName.safeFileSegment().ifBlank { "update" }
        val asset = metadata.apkAssetName.safeFileSegment().removeSuffix(".apk")
        return "${version}-${asset}.apk"
    }

    private fun String.safeFileSegment(): String =
        replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte.toInt() and 0xff)
        }
}
