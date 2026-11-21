package com.trailguardian.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workspaceId")],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val kind: String,
    val name: String,
    val distanceM: Double,
    val elevationGainM: Double,
    val durationS: Long,
    val distanceText: String,
    val eleGain: Int,
    val timeText: String,
    val startedAt: Long?,
    val finishedAt: Long?,
    val importedAt: Long?,
    val createdAt: Long,
    val pointCount: Int,
)

@Entity(
    tableName = "track_points",
    primaryKeys = ["trackId", "seq"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")],
)
data class TrackPointEntity(
    val trackId: String,
    val seq: Int,
    val lat: Double,
    val lng: Double,
    val ele: Int?,
    val time: Long?,
)

@Entity(
    tableName = "pois",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workspaceId")],
)
data class PoiEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val type: String,
    val name: String,
    val description: String,
    val lat: Double,
    val lng: Double,
    val ele: Double?,
    val time: Long?,
    val sym: String?,
    val gpxType: String?,
    val source: String?,
    val createdAt: Long,
)

@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val workspaceId: String?,
    val sourceId: String,
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
    val pointsJson: String,
    val zoomsJson: String,
    val includeContour: Boolean,
    val tileCount: Int,
    val downloadedTiles: Int,
    val failedTiles: Int,
    val estimatedBytes: Long,
    val sizeBytes: Long,
    val progress: Int,
    val status: String,
    val createdAt: Long,
    val completedAt: Long?,
)

@Entity(tableName = "active_session")
data class ActiveSessionEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val sessionStartTime: Long,
    val accumulatedPausedMs: Long,
    val totalDistanceM: Double,
    val totalElevationGainM: Double,
    val isRecording: Boolean,
    val isPaused: Boolean,
    val pauseStartedAt: Long?,
    val lastAcceptedLat: Double?,
    val lastAcceptedLng: Double?,
    val lastAcceptedEle: Int?,
    val lastAcceptedTime: Long?,
    val lastSampleTimestamp: Long,
    val motionState: String,
    val stationaryCount: Int,
    val lastValidElevation: Int?,
    val savedAt: Long,
)

@Entity(
    tableName = "session_points",
    primaryKeys = ["sessionId", "seq"],
    indices = [Index("sessionId")],
)
data class SessionPointEntity(
    val sessionId: String,
    val seq: Int,
    val lat: Double,
    val lng: Double,
    val ele: Int?,
    val time: Long?,
)
