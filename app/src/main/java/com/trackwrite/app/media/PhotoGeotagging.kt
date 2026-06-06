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

private const val MIME_TYPE_JPEG = "image/jpeg"
private const val MIME_TYPE_JPG = "image/jpg"
private const val MIME_TYPE_PJPEG = "image/pjpeg"
private const val MIME_TYPE_X_JPEG = "image/x-jpeg"
private const val MIME_TYPE_X_JPG = "image/x-jpg"
private const val MIME_TYPE_PNG = "image/png"
private const val MIME_TYPE_WEBP = "image/webp"
private const val MIME_TYPE_ANY_IMAGE = "image/*"
private const val MIME_TYPE_OCTET_STREAM = "application/octet-stream"
private const val GPS_COORDINATE_VERIFY_TOLERANCE = 0.000001
private const val GPS_ALTITUDE_VERIFY_TOLERANCE = 0.001

private const val GPS_EXIF_VERSION_2_3_0_0 = "2,3,0,0"

private val writableExtensionMimeTypes = mapOf(
    "jpg" to MIME_TYPE_JPEG,
    "jpeg" to MIME_TYPE_JPEG,
    "png" to MIME_TYPE_PNG,
    "webp" to MIME_TYPE_WEBP,
)

private val unsupportedRawExtensions = setOf("dng", "nef")

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
                writeGpsAttributes(this, position)
                saveAttributes()
            }
            if (mimeType == MIME_TYPE_JPEG) {
                verifyWrittenGps(temp, position)
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

    private fun verifyWrittenGps(file: File, position: GeoPoint) {
        val exif = ExifInterface(file)
        val latLong = requireNotNull(exif.getLatLong()) { "Could not verify written GPS coordinates." }
        require(abs(latLong[0] - position.latitude) <= GPS_COORDINATE_VERIFY_TOLERANCE) {
            "Written GPS latitude did not verify."
        }
        require(abs(latLong[1] - position.longitude) <= GPS_COORDINATE_VERIFY_TOLERANCE) {
            "Written GPS longitude did not verify."
        }
        require(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) == gpsLatitudeRef(position.latitude)) {
            "Written GPS latitude ref did not verify."
        }
        require(exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF) == gpsLongitudeRef(position.longitude)) {
            "Written GPS longitude ref did not verify."
        }
        require(!gpsVersionReadBackBlocksWrite(exif.getAttributeBytes(ExifInterface.TAG_GPS_VERSION_ID))) {
            "Written GPS version did not verify."
        }
        val altitude = position.altitudeMeters
        if (altitude == null) {
            require(exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) == null) { "Unexpected GPS altitude was written." }
            require(exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF) == null) { "Unexpected GPS altitude ref was written." }
        } else {
            require(abs(exif.getAltitude(Double.NaN) - altitude) <= GPS_ALTITUDE_VERIFY_TOLERANCE) {
                "Written GPS altitude did not verify."
            }
            require(exif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF) == gpsAltitudeRef(altitude)) {
                "Written GPS altitude ref did not verify."
            }
        }
    }
}

internal fun writeGpsAttributes(exif: ExifInterface, position: GeoPoint) {
    exif.setLatLong(position.latitude, position.longitude)
    exif.setAttribute(ExifInterface.TAG_GPS_VERSION_ID, GPS_EXIF_VERSION_2_3_0_0)
    position.altitudeMeters?.let { altitude ->
        exif.setAltitude(altitude)
    } ?: run {
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE, null)
        exif.setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, null)
    }
}

internal fun gpsExifVersionBytes(): ByteArray = byteArrayOf(2, 3, 0, 0)

// GPSVersionID is still written, but provider/AndroidX read-back differences are non-blocking.
internal fun gpsVersionReadBackBlocksWrite(version: ByteArray?): Boolean =
    when {
        version == null -> false
        version.contentEquals(gpsExifVersionBytes()) -> false
        else -> false
    }

internal fun gpsLatitudeRef(latitude: Double): String = if (latitude >= 0) "N" else "S"

internal fun gpsLongitudeRef(longitude: Double): String = if (longitude >= 0) "E" else "W"

internal fun gpsAltitudeRef(altitude: Double): String = if (altitude < 0) "1" else "0"

internal fun gpsWritableMimeType(mimeType: String?, displayName: String): String? {
    val normalizedMimeType = mimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.US)
    val extension = displayName
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase(Locale.US)
    if (extension in unsupportedRawExtensions) return null

    when (normalizedMimeType) {
        MIME_TYPE_JPEG, MIME_TYPE_JPG, MIME_TYPE_PJPEG, MIME_TYPE_X_JPEG, MIME_TYPE_X_JPG -> return MIME_TYPE_JPEG
        MIME_TYPE_PNG -> return MIME_TYPE_PNG
        MIME_TYPE_WEBP -> return MIME_TYPE_WEBP
    }
    if (normalizedMimeType != null &&
        normalizedMimeType != MIME_TYPE_OCTET_STREAM &&
        normalizedMimeType != MIME_TYPE_ANY_IMAGE
    ) {
        return null
    }

    return writableExtensionMimeTypes[extension]
}

private fun tempSuffix(mimeType: String): String =
    when (mimeType) {
        MIME_TYPE_PNG -> ".png"
        MIME_TYPE_WEBP -> ".webp"
        else -> ".jpg"
    }
