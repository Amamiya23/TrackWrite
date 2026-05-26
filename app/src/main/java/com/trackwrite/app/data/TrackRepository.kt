package com.trackwrite.app.data

import android.content.Context
import com.trackwrite.app.domain.GeoPoint
import com.trackwrite.app.domain.Track
import com.trackwrite.app.domain.TrackPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID

class TrackRepository(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "tracks.json")

    @Synchronized
    fun listTracks(): List<Track> =
        readTracks().sortedByDescending { it.startTime ?: Instant.EPOCH }

    @Synchronized
    fun getTrack(id: String): Track? =
        readTracks().firstOrNull { it.id == id }

    @Synchronized
    fun saveTrack(track: Track) {
        val tracks = readTracks().filterNot { it.id == track.id } + track
        writeTracks(tracks)
    }

    @Synchronized
    fun appendPoint(trackId: String, point: TrackPoint) {
        val tracks = readTracks()
        val updated = tracks.map { track ->
            if (track.id == trackId) {
                track.copy(points = (track.points + point).sortedBy { it.recordedAt })
            } else {
                track
            }
        }
        writeTracks(updated)
    }

    @Synchronized
    fun renameTrack(trackId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        writeTracks(readTracks().map { track ->
            if (track.id == trackId) track.copy(name = trimmed) else track
        })
    }

    @Synchronized
    fun deleteTrack(trackId: String) {
        writeTracks(readTracks().filterNot { it.id == trackId })
    }

    fun createTrack(name: String): Track =
        Track(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Track ${Instant.now()}" },
            points = emptyList(),
        ).also(::saveTrack)

    private fun readTracks(): List<Track> {
        if (!file.exists()) return emptyList()
        val root = JSONArray(file.readText())
        return List(root.length()) { index ->
            root.getJSONObject(index).toTrack()
        }
    }

    private fun writeTracks(tracks: List<Track>) {
        val root = JSONArray()
        tracks.forEach { root.put(it.toJson()) }
        file.writeText(root.toString(2))
    }

    private fun JSONObject.toTrack(): Track =
        Track(
            id = getString("id"),
            name = getString("name"),
            points = getJSONArray("points").let { array ->
                List(array.length()) { index -> array.getJSONObject(index).toTrackPoint() }
            },
        )

    private fun JSONObject.toTrackPoint(): TrackPoint =
        TrackPoint(
            position = GeoPoint(
                latitude = getDouble("latitude"),
                longitude = getDouble("longitude"),
                altitudeMeters = if (has("altitudeMeters") && !isNull("altitudeMeters")) {
                    getDouble("altitudeMeters")
                } else {
                    null
                },
            ),
            recordedAt = Instant.parse(getString("recordedAt")),
        )

    private fun Track.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("points", JSONArray().also { array ->
                points.forEach { array.put(it.toJson()) }
            })

    private fun TrackPoint.toJson(): JSONObject =
        JSONObject()
            .put("latitude", position.latitude)
            .put("longitude", position.longitude)
            .put("altitudeMeters", position.altitudeMeters)
            .put("recordedAt", recordedAt.toString())
}
