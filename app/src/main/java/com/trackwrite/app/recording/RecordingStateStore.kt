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
            lastPointRecordedAtMillis = preferences.getLong(KEY_LAST_POINT_RECORDED_AT, 0L).takeIf { it > 0L },
            provider = preferences.getString(KEY_PROVIDER, null),
            issue = preferences.getString(KEY_ISSUE, null)?.let { stored ->
                runCatching { RecordingIssue.valueOf(stored) }.getOrNull()
            } ?: RecordingIssue.None,
        )

    fun set(
        trackId: String?,
        status: RecordingStatus,
        lastPointRecordedAtMillis: Long? = current().lastPointRecordedAtMillis,
        provider: String? = current().provider,
        issue: RecordingIssue = current().issue,
    ) {
        preferences.edit()
            .putString(KEY_TRACK_ID, trackId)
            .putString(KEY_STATUS, status.name)
            .putLong(KEY_LAST_POINT_RECORDED_AT, lastPointRecordedAtMillis ?: 0L)
            .putString(KEY_PROVIDER, provider)
            .putString(KEY_ISSUE, issue.name)
            .apply()
    }

    companion object {
        private const val KEY_TRACK_ID = "track_id"
        private const val KEY_STATUS = "status"
        private const val KEY_LAST_POINT_RECORDED_AT = "last_point_recorded_at"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_ISSUE = "issue"
    }
}

data class RecordingSnapshot(
    val trackId: String?,
    val status: RecordingStatus,
    val lastPointRecordedAtMillis: Long? = null,
    val provider: String? = null,
    val issue: RecordingIssue = RecordingIssue.None,
)

enum class RecordingStatus {
    Recording,
    Paused,
    Stopped,
}

enum class RecordingIssue {
    None,
    WaitingForFix,
    PermissionMissing,
    LocationDisabled,
}
