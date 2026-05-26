package com.trackwrite.app.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.trackwrite.app.domain.GeoPoint
import com.trackwrite.app.domain.MatchOptions
import com.trackwrite.app.domain.PhotoMatch
import com.trackwrite.app.domain.PhotoTrackMatcher
import com.trackwrite.app.domain.Track
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

data class PhotoCandidate(
    val uri: Uri,
    val displayName: String,
    val capturedAt: Instant?,
    val manualLocation: GeoPoint? = null,
)

data class PhotoMatchResult(
    val photo: PhotoCandidate,
    val match: PhotoMatch?,
) {
    val selectedPosition: GeoPoint?
        get() = photo.manualLocation ?: (match as? PhotoMatch.Matched)?.position
}

class PhotoGeotagging(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    private val matcher = PhotoTrackMatcher()

    fun loadPhotos(uris: List<Uri>): List<PhotoCandidate> =
        uris.map { uri ->
            PhotoCandidate(
                uri = uri,
                displayName = displayName(uri),
                capturedAt = readCapturedAt(uri),
            )
        }

    fun loadPhotosFromFolder(folderUri: Uri): List<PhotoCandidate> {
        val folder = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
        return loadPhotos(
            folder.listFiles()
                .filter { it.isFile && it.type?.startsWith("image/") == true }
                .map { it.uri },
        )
    }

    fun matchPhotos(
        photos: List<PhotoCandidate>,
        track: Track,
        options: MatchOptions = MatchOptions(),
    ): List<PhotoMatchResult> =
        photos.map { photo ->
            PhotoMatchResult(
                photo = photo,
                match = photo.capturedAt?.let { matcher.match(it, track, options) },
            )
        }

    fun exportCopies(results: List<PhotoMatchResult>, targetFolderUri: Uri): List<String> {
        val folder = DocumentFile.fromTreeUri(context, targetFolderUri)
            ?: return listOf("Export folder is not available.")

        return results.map { result ->
            val position = result.selectedPosition
            if (position == null) {
                "${result.photo.displayName}: no location"
            } else {
                runCatching {
                    val output = folder.createFile("image/jpeg", result.photo.displayName)
                        ?: error("Could not create output file.")
                    resolver.openInputStream(result.photo.uri).use { input ->
                        resolver.openOutputStream(output.uri, "w").use { out ->
                            requireNotNull(input) { "Could not open source photo." }
                            requireNotNull(out) { "Could not open output photo." }
                            input.copyTo(out)
                        }
                    }
                    writeGps(output.uri, position)
                    "${result.photo.displayName}: exported"
                }.getOrElse { error ->
                    "${result.photo.displayName}: ${error.message ?: "export failed"}"
                }
            }
        }
    }

    fun writeInPlace(results: List<PhotoMatchResult>): List<String> =
        results.map { result ->
            val position = result.selectedPosition
            if (position == null) {
                "${result.photo.displayName}: no location"
            } else {
                runCatching {
                    writeGps(result.photo.uri, position)
                    "${result.photo.displayName}: written"
                }.getOrElse { error ->
                    "${result.photo.displayName}: ${error.message ?: "write failed"}"
                }
            }
        }

    private fun readCapturedAt(uri: Uri): Instant? =
        runCatching {
            resolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                val exif = ExifInterface(input)
                val value = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    ?: return null
                parseExifDate(value)
            }
        }.getOrNull()

    private fun writeGps(uri: Uri, position: GeoPoint) {
        val temp = File.createTempFile("trackwrite-exif-", ".jpg", context.cacheDir)
        try {
            resolver.openInputStream(uri).use { input ->
                FileOutputStream(temp).use { output ->
                    requireNotNull(input) { "Could not open photo." }
                    input.copyTo(output)
                }
            }
            ExifInterface(temp).apply {
                setLatLong(position.latitude, position.longitude)
                position.altitudeMeters?.let { altitude ->
                    setAttribute(ExifInterface.TAG_GPS_ALTITUDE, rational(abs(altitude)))
                    setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, if (altitude < 0) "1" else "0")
                }
                saveAttributes()
            }
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "Could not write photo." }
                temp.inputStream().use { it.copyTo(output) }
            }
        } finally {
            temp.delete()
        }
    }

    private fun displayName(uri: Uri): String {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return uri.lastPathSegment ?: "photo.jpg"
    }

    private fun parseExifDate(value: String): Instant {
        val formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)
        return java.time.LocalDateTime.parse(value, formatter)
            .atZone(ZoneId.systemDefault())
            .toInstant()
    }

    private fun rational(value: Double): String {
        val scaled = floor(value * 1000).toInt()
        return "$scaled/1000"
    }
}
