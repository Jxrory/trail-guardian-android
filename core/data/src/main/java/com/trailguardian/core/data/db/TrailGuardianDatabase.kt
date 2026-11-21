package com.trailguardian.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WorkspaceEntity::class,
        TrackEntity::class,
        TrackPointEntity::class,
        PoiEntity::class,
        OfflineRegionEntity::class,
        ActiveSessionEntity::class,
        SessionPointEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class TrailGuardianDatabase : RoomDatabase() {
    abstract fun workspaceDao(): WorkspaceDao
    abstract fun trackDao(): TrackDao
    abstract fun poiDao(): PoiDao
    abstract fun offlineRegionDao(): OfflineRegionDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var instance: TrailGuardianDatabase? = null

        fun getInstance(context: Context): TrailGuardianDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TrailGuardianDatabase::class.java,
                    "trail_guardian.db",
                ).build().also { instance = it }
            }
    }
}
