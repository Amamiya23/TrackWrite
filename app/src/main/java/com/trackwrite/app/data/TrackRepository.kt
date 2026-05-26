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
import java.util.concurrent.Executors

class TrackRepository(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "tracks.json")
    private val dao = TrackDatabase.get(appContext).trackDao()
    private val executor = Executors.newSingleThreadExecutor()

    init {
        migrateJsonIfNeeded()
    }

    fun listTracks(): List<Track> =
        db {
            dao.listTracks()
                .map { it.toTrack(dao.listPoints(it.id)) }
                .sortedByDescending { it.startTime ?: Instant.EPOCH }
        }

    fun getTrack(id: String): Track? =
        db {
            dao.getTrack(id)?.toTrack(dao.listPoints(id))
        }

    fun saveTrack(track: Track) {
        db {
            dao.replaceTrack(
                track = track.toEntity(),
                points = track.points.map { it.toEntity(track.id) },
            )
        }
    }

    fun appendPoint(trackId: String, point: TrackPoint) {
        db {
            dao.insertPoint(point.toEntity(trackId))
        }
    }

    fun renameTrack(trackId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        db { dao.renameTrack(trackId, trimmed) }
    }

    fun deleteTrack(trackId: String) {
        db { dao.deleteTrack(trackId) }
    }

    fun createTrack(name: String): Track =
        Track(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Track ${Instant.now()}" },
            points = emptyList(),
        ).also(::saveTrack)

    private fun migrateJsonIfNeeded() {
        if (!file.exists()) return
        if (listTracks().isNotEmpty()) return
        runCatching {
            readJsonTracks().forEach(::saveTrack)
            file.renameTo(File(appContext.filesDir, "tracks.migrated.json"))
        }
    }

    private fun <T> db(block: () -> T): T =
        executor.submit<T> { block() }.get()

    private fun readTracks(): List<Track> {
        return readJsonTracks()
    }

    private fun readJsonTracks(): List<Track> {
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

    private fun TrackEntity.toTrack(points: List<TrackPointEntity>): Track =
        Track(
            id = id,
            name = name,
            points = points.map { it.toDomain() },
        )

    private fun Track.toEntity(): TrackEntity =
        TrackEntity(id = id, name = name)

    private fun TrackPoint.toEntity(trackId: String): TrackPointEntity =
        TrackPointEntity(
            trackId = trackId,
            latitude = position.latitude,
            longitude = position.longitude,
            altitudeMeters = position.altitudeMeters,
            recordedAt = recordedAt.toString(),
        )

    private fun TrackPointEntity.toDomain(): TrackPoint =
        TrackPoint(
            position = GeoPoint(
                latitude = latitude,
                longitude = longitude,
                altitudeMeters = altitudeMeters,
            ),
            recordedAt = Instant.parse(recordedAt),
        )
}
