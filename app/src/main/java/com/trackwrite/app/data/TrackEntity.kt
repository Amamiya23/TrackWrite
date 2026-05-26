package com.trackwrite.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val name: String,
)

@Entity(
    tableName = "track_points",
    indices = [
        Index("trackId"),
        Index(value = ["trackId", "recordedAt"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val recordedAt: String,
)
