package com.trackwrite.app.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import com.trackwrite.app.R
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

private const val MIME_TYPE_JPEG = "image/jpeg"
private const val MIME_TYPE_JPG = "image/jpg"
private const val MIME_TYPE_PNG = "image/png"
private const val MIME_TYPE_WEBP = "image/webp"
private const val MIME_TYPE_ANY_IMAGE = "image/*"
private const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"

private val writableExtensionMimeTypes = mapOf(
    "jpg" to MIME_TYPE_JPEG,
    "jpeg" to MIME_TYPE_JPEG,
    "png" to MIME_TYPE_PNG,
    "webp" to MIME_TYPE_WEBP,
)

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

data class PhotoWriteOutcome(
    val fileName: String,
    val status: Status,
    val reason: String? = null,
) {
    enum class Status {
        Written,
        Skipped,
        Failed,
    }
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

    fun exportCopies(results: List<PhotoMatchResult>, targetFolderUri: Uri): List<PhotoWriteOutcome> {
        val folder = DocumentFile.fromTreeUri(context, targetFolderUri)
            ?: return listOf(PhotoWriteOutcome("Export folder", PhotoWriteOutcome.Status.Failed, "Export folder is not available."))

        return results.map { result ->
            val position = result.selectedPosition
            val writableMimeType = result.photo.writableGpsMimeType()
            when {
                position == null -> {
                    PhotoWriteOutcome(result.photo.displayName, PhotoWriteOutcome.Status.Skipped, "no location")
                }
                writableMimeType == null -> {
                    PhotoWriteOutcome(
                        result.photo.displayName,
                        PhotoWriteOutcome.Status.Failed,
                        context.getString(R.string.photo_write_unsupported_format),
                    )
                }
                else -> runCatching {
                    val output = folder.createFile(writableMimeType, result.photo.displayName)
                        ?: error("Could not create output file.")
                    resolver.openInputStream(result.photo.uri).use { input ->
                        resolver.openOutputStream(output.uri, "w").use { out ->
                            requireNotNull(input) { "Could not open source photo." }
                            requireNotNull(out) { "Could not open output photo." }
                            input.copyTo(out)
                        }
                    }
                    writeGps(output.uri, position, writableMimeType)
                    PhotoWriteOutcome(result.photo.displayName, PhotoWriteOutcome.Status.Written)
                }.getOrElse { error ->
                    PhotoWriteOutcome(result.photo.displayName, PhotoWriteOutcome.Status.Failed, error.message ?: "export failed")
                }
            }
        }
    }

    fun writeInPlace(results: List<PhotoMatchResult>): List<PhotoWriteOutcome> =
        results.map { result ->
            val position = result.selectedPosition
            val writableMimeType = result.photo.writableGpsMimeType()
            when {
                position == null -> {
                    PhotoWriteOutcome(result.photo.displayName, PhotoWriteOutcome.Status.Skipped, "no location")
                }
                writableMimeType == null -> {
                    PhotoWriteOutcome(
                        result.photo.displayName,
                        PhotoWriteOutcome.Status.Failed,
                        context.getString(R.string.photo_write_unsupported_format),
                    )
                }
                else -> runCatching {
                    writeGps(result.photo.uri, position, writableMimeType)
                    PhotoWriteOutcome(result.photo.displayName, PhotoWriteOutcome.Status.Written)
                }.getOrElse { error ->
                    PhotoWriteOutcome(result.photo.displayName, PhotoWriteOutcome.Status.Failed, error.message ?: "write failed")
                }
            }
        }

    private fun PhotoCandidate.writableGpsMimeType(): String? =
        gpsWritableMimeType(resolver.getType(uri), displayName)

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

    private fun writeGps(uri: Uri, position: GeoPoint, mimeType: String) {
        val temp = File.createTempFile("trackwrite-exif-", tempSuffix(mimeType), context.cacheDir)
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

internal fun gpsWritableMimeType(mimeType: String?, displayName: String): String? {
    val normalizedMimeType = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.US)
    when (normalizedMimeType) {
        MIME_TYPE_JPEG, MIME_TYPE_JPG -> return MIME_TYPE_JPEG
        MIME_TYPE_PNG -> return MIME_TYPE_PNG
        MIME_TYPE_WEBP -> return MIME_TYPE_WEBP
    }
    if (normalizedMimeType != null &&
        normalizedMimeType != MIME_TYPE_OCTET_STREAM &&
        normalizedMimeType != MIME_TYPE_ANY_IMAGE
    ) {
        return null
    }

    val extension = displayName
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.US)
    return writableExtensionMimeTypes[extension]
}

private fun tempSuffix(mimeType: String): String =
    when (mimeType) {
        MIME_TYPE_PNG -> ".png"
        MIME_TYPE_WEBP -> ".webp"
        else -> ".jpg"
    }
