package com.trackwrite.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrackEntity::class, TrackPointEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class TrackDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile
        private var instance: TrackDatabase? = null

        fun get(context: Context): TrackDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrackDatabase::class.java,
                    "trackwrite.db",
                ).build().also { instance = it }
            }
    }
}
