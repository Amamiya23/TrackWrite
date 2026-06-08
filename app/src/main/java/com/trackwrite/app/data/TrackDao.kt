package com.trackwrite.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks")
    fun listTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    fun getTrack(id: String): TrackEntity?

    @Query("SELECT COUNT(*) FROM tracks WHERE id = :id")
    fun trackExists(id: String): Int

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY recordedAt ASC")
    fun listPoints(trackId: String): List<TrackPointEntity>

    @Query("SELECT COUNT(*) FROM track_points WHERE trackId = :trackId")
    fun countPoints(trackId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTrack(track: TrackEntity)

    @Insert
    fun insertPoints(points: List<TrackPointEntity>)

    @Insert
    fun insertPoint(point: TrackPointEntity)

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    fun deletePoints(trackId: String)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    fun deleteTrack(trackId: String)

    @Query("UPDATE tracks SET name = :name WHERE id = :trackId")
    fun renameTrack(trackId: String, name: String)

    @Transaction
    fun replaceTrack(track: TrackEntity, points: List<TrackPointEntity>) {
        upsertTrack(track)
        deletePoints(track.id)
        insertPoints(points)
    }
}
