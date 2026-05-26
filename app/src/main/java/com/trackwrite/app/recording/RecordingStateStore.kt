package com.trackwrite.app.recording

import android.content.Context

class RecordingStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("recording", Context.MODE_PRIVATE)

    fun current(): RecordingSnapshot =
        RecordingSnapshot(
            trackId = preferences.getString(KEY_TRACK_ID, null),
            status = RecordingStatus.valueOf(
                preferences.getString(KEY_STATUS, RecordingStatus.Stopped.name) ?: RecordingStatus.Stopped.name,
            ),
        )

    fun set(trackId: String?, status: RecordingStatus) {
        preferences.edit()
            .putString(KEY_TRACK_ID, trackId)
            .putString(KEY_STATUS, status.name)
            .apply()
    }

    companion object {
        private const val KEY_TRACK_ID = "track_id"
        private const val KEY_STATUS = "status"
    }
}

data class RecordingSnapshot(
    val trackId: String?,
    val status: RecordingStatus,
)

enum class RecordingStatus {
    Recording,
    Paused,
    Stopped,
}
