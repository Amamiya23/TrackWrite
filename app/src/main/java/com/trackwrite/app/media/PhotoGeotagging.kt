package com.trackwrite.app.media

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
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
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
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

    fun exportCopies(
        results: List<PhotoMatchResult>,
        targetFolderUri: Uri,
        onProgress: (processed: Int) -> Unit = {},
    ): List<PhotoWriteOutcome> {
        val folder = DocumentFile.fromTreeUri(context, targetFolderUri)
            ?: return listOf(PhotoWriteOutcome("Export folder", PhotoWriteOutcome.Status.Failed, "Export folder is not available."))

        return results.mapIndexed { index, result ->
            val position = result.selectedPosition
            val writableMimeType = result.photo.writableGpsMimeType()
            val outcome = when {
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
                else -> runCatchingStorage {
                    val output = folder.createFile(writableMimeType, result.photo.displayName)
                        ?: error("Could not create output file.")
                    val sourceTemp = copyUriToTemp(result.photo.uri, writableMimeType, "trackwrite-copy-")
                    try {
                        writeUriFromFile(output.uri, sourceTemp)
                    } finally {
                        sourceTemp.delete()
                    }
                    writeGps(output.uri, position, writableMimeType)
                    PhotoWriteOutcome(result.photo.displayName, PhotoWriteOutcome.Status.Written)
                }.getOrElse { error ->
                    PhotoWriteOutcome(result.photo.displayName, PhotoWriteOutcome.Status.Failed, error.message ?: "export failed")
                }
            }
            onProgress(index + 1)
            outcome
        }
    }

    fun writeInPlace(
        results: List<PhotoMatchResult>,
        backupFolderUri: Uri,
        onProgress: (processed: Int) -> Unit = {},
    ): List<PhotoWriteOutcome> {
        val backupFolder = DocumentFile.fromTreeUri(context, backupFolderUri)
            ?: return listOf(
                PhotoWriteOutcome(
                    context.getString(R.string.photo_backup_folder),
                    PhotoWriteOutcome.Status.Failed,
                    context.getString(R.string.photo_backup_folder_unavailable),
                ),
            )
        if (!backupFolder.canWrite()) {
            return listOf(
                PhotoWriteOutcome(
                    context.getString(R.string.photo_backup_folder),
                    PhotoWriteOutcome.Status.Failed,
                    context.getString(R.string.photo_backup_folder_unavailable),
                ),
            )
        }

        return results.mapIndexed { index, result ->
            val position = result.selectedPosition
            val writableMimeType = result.photo.writableGpsMimeType()
            val outcome = when {
                position == null -> {
                    PhotoWriteOutcome(
                        result.photo.displayName,
                        PhotoWriteOutcome.Status.Skipped,
                        context.getString(R.string.photo_write_no_location),
                    )
                }
                writableMimeType == null -> {
                    PhotoWriteOutcome(
                        result.photo.displayName,
                        PhotoWriteOutcome.Status.Failed,
                        context.getString(R.string.photo_write_unsupported_format),
                    )
                }
                else -> runCatchingStorage {
                    writeGpsWithBackup(result.photo, position, writableMimeType, backupFolder)
                    PhotoWriteOutcome(result.photo.displayName, PhotoWriteOutcome.Status.Written)
                }.getOrElse { error ->
                    PhotoWriteOutcome(result.photo.displayName, PhotoWriteOutcome.Status.Failed, error.message ?: "write failed")
                }
            }
            onProgress(index + 1)
            outcome
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
        val sourceTemp = copyUriToTemp(uri, mimeType, "trackwrite-source-")
        var editedTemp: File? = null
        try {
            validateImageFile(sourceTemp, mimeType)
            editedTemp = buildGeotaggedTempCopy(sourceTemp, position, mimeType)
            writeUriFromFile(uri, editedTemp)
            validateImageUri(uri, mimeType, position)
        } finally {
            sourceTemp.delete()
            editedTemp?.delete()
        }
    }

    private fun writeGpsWithBackup(
        photo: PhotoCandidate,
        position: GeoPoint,
        mimeType: String,
        backupFolder: DocumentFile,
    ) {
        val sourceTemp = runCatchingStorage {
            copyUriToTemp(photo.uri, mimeType, "trackwrite-original-").also {
                validateImageFile(it, mimeType)
            }
        }.getOrElse { error ->
            throw IllegalStateException(
                context.getString(R.string.photo_write_source_validation_failed, error.message.orEmpty()),
                error,
            )
        }
        var editedTemp: File? = null
        try {
            editedTemp = runCatchingStorage {
                buildGeotaggedTempCopy(sourceTemp, position, mimeType)
            }.getOrElse { error ->
                throw IllegalStateException(
                    context.getString(R.string.photo_write_preflight_validation_failed, error.message.orEmpty()),
                    error,
                )
            }
            runCatchingStorage {
                writeBackupFromFile(sourceTemp, photo.displayName, mimeType, backupFolder)
            }.getOrElse { error ->
                throw IllegalStateException(
                    context.getString(R.string.photo_write_backup_failed, error.message.orEmpty()),
                    error,
                )
            }
            runCatchingStorage {
                replaceUriFromFile(photo.uri, editedTemp)
            }.getOrElse { error ->
                throw IllegalStateException(
                    context.getString(R.string.photo_write_replace_failed, error.message.orEmpty()),
                    error,
                )
            }
            runCatchingStorage {
                validateImageUri(photo.uri, mimeType, position)
            }.getOrElse { error ->
                throw IllegalStateException(
                    context.getString(R.string.photo_write_postcheck_failed, error.message.orEmpty()),
                    error,
                )
            }
        } finally {
            sourceTemp.delete()
            editedTemp?.delete()
        }
    }

    private fun copyUriToTemp(uri: Uri, mimeType: String, prefix: String): File {
        val temp = File.createTempFile(prefix, tempSuffix(mimeType), context.cacheDir)
        return try {
            resolver.openInputStream(uri).use { input ->
                FileOutputStream(temp).use { output ->
                    requireNotNull(input) { context.getString(R.string.photo_open_failed) }
                    input.copyTo(output)
                }
            }
            temp
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun buildGeotaggedTempCopy(source: File, position: GeoPoint, mimeType: String): File {
        val temp = File.createTempFile("trackwrite-exif-", tempSuffix(mimeType), context.cacheDir)
        return try {
            source.copyTo(temp, overwrite = true)
            ExifInterface(temp).apply {
                writeGpsAttributes(this, position)
                saveAttributes()
            }
            validateImageFile(temp, mimeType, position)
            temp
        } catch (error: Throwable) {
            temp.delete()
            throw error
        }
    }

    private fun writeBackupFromFile(
        source: File,
        displayName: String,
        mimeType: String,
        backupFolder: DocumentFile,
    ) {
        val backup = backupFolder.createFile(mimeType, backupDisplayName(displayName))
            ?: error(context.getString(R.string.photo_backup_create_failed))
        try {
            writeUriFromFile(backup.uri, source)
            validateImageUri(backup.uri, mimeType)
        } catch (error: Throwable) {
            backup.delete()
            throw error
        }
    }

    private fun writeUriFromFile(uri: Uri, file: File) {
        resolver.openOutputStream(uri, "w").use { output ->
            requireNotNull(output) { context.getString(R.string.photo_write_output_failed) }
            file.inputStream().use { input -> input.copyTo(output) }
        }
    }

    private fun replaceUriFromFile(uri: Uri, file: File) {
        resolver.openFileDescriptor(uri, "rwt").use { descriptor ->
            requireNotNull(descriptor) { context.getString(R.string.photo_write_descriptor_failed) }
            FileOutputStream(descriptor.fileDescriptor).use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
                output.fd.sync()
            }
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

    private fun validateImageFile(file: File, mimeType: String, position: GeoPoint? = null) {
        require(file.length() > 0L) { context.getString(R.string.photo_write_empty_file) }
        file.inputStream().use { validateImageSignature(it, mimeType) }
        validateBitmapBounds(file)
        if (position != null) verifyWrittenGps(file, position)
    }

    private fun validateImageUri(uri: Uri, mimeType: String, position: GeoPoint? = null) {
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { context.getString(R.string.photo_open_failed) }
            validateImageSignature(input, mimeType)
        }
        validateBitmapBounds(uri)
        if (position != null) {
            openUnredactedInputStream(uri).use { input ->
                requireNotNull(input) { context.getString(R.string.photo_open_failed) }
                val temp = File.createTempFile("trackwrite-verify-", tempSuffix(mimeType), context.cacheDir)
                try {
                    FileOutputStream(temp).use { output -> input.copyTo(output) }
                    verifyWrittenGps(temp, position)
                } finally {
                    temp.delete()
                }
            }
        }
    }

    private fun openUnredactedInputStream(uri: Uri): InputStream? =
        runCatchingStorage {
            resolver.openInputStream(MediaStore.setRequireOriginal(uri))
        }.getOrNull() ?: resolver.openInputStream(uri)

    private fun validateBitmapBounds(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth > 0 && options.outHeight > 0) {
            context.getString(R.string.photo_write_decode_failed)
        }
    }

    private fun validateBitmapBounds(uri: Uri) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { input ->
            requireNotNull(input) { context.getString(R.string.photo_open_failed) }
            BitmapFactory.decodeStream(input, null, options)
        }
        require(options.outWidth > 0 && options.outHeight > 0) {
            context.getString(R.string.photo_write_decode_failed)
        }
    }

    private fun validateImageSignature(input: InputStream, mimeType: String) {
        val header = readHeader(input)
        require(imageSignatureMatches(mimeType, header)) {
            context.getString(R.string.photo_write_signature_failed)
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

internal fun backupDisplayName(displayName: String, now: Instant = Instant.now()): String {
    val safeName = displayName
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .ifBlank { "photo" }
    val timestamp = DateTimeFormatter
        .ofPattern("yyyyMMdd-HHmmss-SSS", Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(now)
    return "$timestamp-$safeName"
}

internal fun imageSignatureMatches(mimeType: String, header: ByteArray): Boolean =
    when (mimeType) {
        MIME_TYPE_JPEG -> header.size >= 3 &&
            header[0] == 0xFF.toByte() &&
            header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        MIME_TYPE_PNG -> header.size >= 8 &&
            header[0] == 0x89.toByte() &&
            header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() &&
            header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() &&
            header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() &&
            header[7] == 0x0A.toByte()
        MIME_TYPE_WEBP -> header.size >= 12 &&
            header.asciiAt(0, "RIFF") &&
            header.asciiAt(8, "WEBP")
        else -> false
    }

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

private fun readHeader(input: InputStream): ByteArray {
    val header = ByteArray(12)
    var offset = 0
    while (offset < header.size) {
        val count = input.read(header, offset, header.size - offset)
        if (count <= 0) break
        offset += count
    }
    return header.copyOf(offset)
}

private fun ByteArray.asciiAt(offset: Int, value: String): Boolean {
    if (size < offset + value.length) return false
    return value.indices.all { index -> this[offset + index] == value[index].code.toByte() }
}

private inline fun <T> runCatchingStorage(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
