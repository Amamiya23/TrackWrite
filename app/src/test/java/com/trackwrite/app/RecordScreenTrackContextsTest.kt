package com.trackwrite.app

import com.trackwrite.app.domain.Track
import com.trackwrite.app.recording.RecordingSnapshot
import com.trackwrite.app.recording.RecordingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordScreenTrackContextsTest {
    private val activeTrack = Track(id = "active", name = "Active", points = emptyList())
    private val previousTrack = Track(id = "previous", name = "Previous", points = emptyList())
    private val tracks = listOf(activeTrack, previousTrack)

    @Test
    fun recordingTrackIsExcludedFromHistory() {
        val historical = historicalTracksForRecording(
            tracks = tracks,
            recording = RecordingSnapshot(trackId = activeTrack.id, status = RecordingStatus.Recording),
        )

        assertEquals(listOf(previousTrack), historical)
    }

    @Test
    fun pausedTrackIsExcludedFromHistory() {
        val historical = historicalTracksForRecording(
            tracks = tracks,
            recording = RecordingSnapshot(trackId = activeTrack.id, status = RecordingStatus.Paused),
        )

        assertEquals(listOf(previousTrack), historical)
    }

    @Test
    fun stoppedTrackIsIncludedInHistory() {
        val historical = historicalTracksForRecording(
            tracks = tracks,
            recording = RecordingSnapshot(trackId = activeTrack.id, status = RecordingStatus.Stopped),
        )

        assertEquals(tracks, historical)
    }

    @Test
    fun stoppedViewportUsesLatestHistoricalTrack() {
        val viewportTrack = recordViewportTrack(
            historicalTracks = listOf(previousTrack),
            activeTrack = activeTrack,
            recording = RecordingSnapshot(trackId = activeTrack.id, status = RecordingStatus.Stopped),
        )

        assertEquals(previousTrack, viewportTrack)
    }

    @Test
    fun recordingViewportUsesActiveTrack() {
        val viewportTrack = recordViewportTrack(
            historicalTracks = listOf(previousTrack),
            activeTrack = activeTrack,
            recording = RecordingSnapshot(trackId = activeTrack.id, status = RecordingStatus.Recording),
        )

        assertEquals(activeTrack, viewportTrack)
    }

    @Test
    fun pausedViewportUsesActiveTrack() {
        val viewportTrack = recordViewportTrack(
            historicalTracks = listOf(previousTrack),
            activeTrack = activeTrack,
            recording = RecordingSnapshot(trackId = activeTrack.id, status = RecordingStatus.Paused),
        )

        assertEquals(activeTrack, viewportTrack)
    }
}
