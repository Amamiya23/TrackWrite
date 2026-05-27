package com.trackwrite.app.settings

import android.content.Context
import java.time.Duration

class AppSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun current(): AppSettings =
        AppSettings(
            appearance = AppearanceMode.fromStorage(preferences.getString(KEY_APPEARANCE, null)),
            recordingFrequency = RecordingFrequency.fromStorage(preferences.getString(KEY_RECORDING_FREQUENCY, null)),
            cameraOffset = Duration.ofMinutes(preferences.getLong(KEY_CAMERA_OFFSET_MINUTES, 0L)),
            maxPhotoTimeDifference = Duration.ofMinutes(
                preferences.getLong(KEY_MAX_PHOTO_TIME_DIFFERENCE_MINUTES, DEFAULT_MAX_PHOTO_TIME_DIFFERENCE_MINUTES),
            ),
            allowStartFallback = preferences.getBoolean(KEY_ALLOW_START_FALLBACK, true),
            allowEndFallback = preferences.getBoolean(KEY_ALLOW_END_FALLBACK, true),
            preferExportCopies = preferences.getBoolean(KEY_PREFER_EXPORT_COPIES, true),
            defaultExportFolderUri = preferences.getString(KEY_DEFAULT_EXPORT_FOLDER_URI, null),
        )

    fun setAppearance(value: AppearanceMode) {
        preferences.edit().putString(KEY_APPEARANCE, value.storageValue).apply()
    }

    fun setRecordingFrequency(value: RecordingFrequency) {
        preferences.edit().putString(KEY_RECORDING_FREQUENCY, value.storageValue).apply()
    }

    fun setMaxPhotoTimeDifference(value: Duration) {
        val minutes = value.toMinutes().coerceIn(MIN_PHOTO_TIME_DIFFERENCE_MINUTES, MAX_PHOTO_TIME_DIFFERENCE_MINUTES)
        preferences.edit().putLong(KEY_MAX_PHOTO_TIME_DIFFERENCE_MINUTES, minutes).apply()
    }

    fun setCameraOffset(value: Duration) {
        val minutes = value.toMinutes().coerceIn(MIN_CAMERA_OFFSET_MINUTES, MAX_CAMERA_OFFSET_MINUTES)
        preferences.edit().putLong(KEY_CAMERA_OFFSET_MINUTES, minutes).apply()
    }

    fun setAllowStartFallback(value: Boolean) {
        preferences.edit().putBoolean(KEY_ALLOW_START_FALLBACK, value).apply()
    }

    fun setAllowEndFallback(value: Boolean) {
        preferences.edit().putBoolean(KEY_ALLOW_END_FALLBACK, value).apply()
    }

    fun setPreferExportCopies(value: Boolean) {
        preferences.edit().putBoolean(KEY_PREFER_EXPORT_COPIES, value).apply()
    }

    fun setDefaultExportFolderUri(value: String?) {
        preferences.edit().apply {
            if (value.isNullOrBlank()) {
                remove(KEY_DEFAULT_EXPORT_FOLDER_URI)
            } else {
                putString(KEY_DEFAULT_EXPORT_FOLDER_URI, value)
            }
        }.apply()
    }

    companion object {
        const val MIN_CAMERA_OFFSET_MINUTES = -720L
        const val MAX_CAMERA_OFFSET_MINUTES = 720L
        const val MIN_PHOTO_TIME_DIFFERENCE_MINUTES = 1L
        const val MAX_PHOTO_TIME_DIFFERENCE_MINUTES = 60L

        private const val DEFAULT_MAX_PHOTO_TIME_DIFFERENCE_MINUTES = 5L
        private const val KEY_APPEARANCE = "appearance"
        private const val KEY_RECORDING_FREQUENCY = "recording_frequency"
        private const val KEY_CAMERA_OFFSET_MINUTES = "camera_offset_minutes"
        private const val KEY_MAX_PHOTO_TIME_DIFFERENCE_MINUTES = "max_photo_time_difference_minutes"
        private const val KEY_ALLOW_START_FALLBACK = "allow_start_fallback"
        private const val KEY_ALLOW_END_FALLBACK = "allow_end_fallback"
        private const val KEY_PREFER_EXPORT_COPIES = "prefer_export_copies"
        private const val KEY_DEFAULT_EXPORT_FOLDER_URI = "default_export_folder_uri"
    }
}

data class AppSettings(
    val appearance: AppearanceMode = AppearanceMode.System,
    val recordingFrequency: RecordingFrequency = RecordingFrequency.Balanced,
    val cameraOffset: Duration = Duration.ZERO,
    val maxPhotoTimeDifference: Duration = Duration.ofMinutes(5),
    val allowStartFallback: Boolean = true,
    val allowEndFallback: Boolean = true,
    val preferExportCopies: Boolean = true,
    val defaultExportFolderUri: String? = null,
)

enum class AppearanceMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    companion object {
        fun fromStorage(value: String?): AppearanceMode =
            entries.firstOrNull { it.storageValue == value } ?: System
    }
}

enum class RecordingFrequency(
    val storageValue: String,
    val intervalMs: Long,
    val distanceMeters: Float,
) {
    Efficient("efficient", intervalMs = 15_000L, distanceMeters = 25f),
    Balanced("balanced", intervalMs = 5_000L, distanceMeters = 8f),
    Precise("precise", intervalMs = 2_000L, distanceMeters = 3f),
    ;

    companion object {
        fun fromStorage(value: String?): RecordingFrequency =
            entries.firstOrNull { it.storageValue == value } ?: Balanced
    }
}
