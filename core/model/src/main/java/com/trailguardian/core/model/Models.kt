package com.trailguardian.core.model

import java.util.UUID

data class GeoPoint(
    val lat: Double,
    val lng: Double,
    val ele: Int? = null,
    val time: Long? = null,
)

data class TargetTrack(
    val id: String,
    val name: String = DEFAULT_NAME,
    val points: List<GeoPoint> = emptyList(),
    val pointCount: Int = points.size,
    val importedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val DEFAULT_NAME = "目标运动路径"

        fun create(points: List<GeoPoint>, name: String? = null): TargetTrack {
            val now = System.currentTimeMillis()
            return TargetTrack(
                id = "target-$now-${shortId()}",
                name = name?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME,
                points = points,
                pointCount = points.size,
                importedAt = now,
            )
        }
    }
}

data class RecordedTrack(
    val id: String,
    val name: String,
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val elevationGain: Double,
    val durationSeconds: Long,
    val distance: String,
    val eleGain: Int,
    val time: String,
    val startedAt: Long?,
    val finishedAt: Long,
    val createdAt: Long,
) {
    companion object {
        fun create(
            workspaceName: String,
            recording: CompletedRecording,
        ): RecordedTrack {
            val points = recording.points
            val distanceMeters = recording.distanceMeters
            val elevationGain = recording.elevationGain
            return RecordedTrack(
                id = "recorded-${recording.createdAt}-${shortId()}",
                name = "$workspaceName 已走轨迹",
                points = points,
                distanceMeters = distanceMeters,
                elevationGain = elevationGain,
                durationSeconds = recording.durationSeconds,
                distance = String.format(java.util.Locale.US, "%.2f", distanceMeters / 1000.0),
                eleGain = Math.round(elevationGain).toInt(),
                time = com.trailguardian.core.model.formatDuration(recording.durationSeconds),
                startedAt = recording.startedAt,
                finishedAt = recording.finishedAt,
                createdAt = recording.createdAt,
            )
        }
    }
}

data class Workspace(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val targetTrack: TargetTrack? = null,
    val recordedTrack: RecordedTrack? = null,
    val pois: List<Poi> = emptyList(),
)

enum class PoiType(val key: String, val label: String, val colorHex: String) {
    CAMP("camp", "营地", "#10B981"),
    WATER("water", "水源", "#3B82F6"),
    DANGER("danger", "危险", "#EF4444"),
    VIEW("view", "观景", "#F59E0B"),
    MARKER("marker", "标注", "#8B5CF6");

    companion object {
        fun fromKey(key: String?): PoiType =
            entries.firstOrNull { it.key == key } ?: MARKER
    }
}

data class Poi(
    val id: String,
    val type: PoiType = PoiType.CAMP,
    val name: String = DEFAULT_NAME,
    val desc: String = "",
    val lat: Double,
    val lng: Double,
    val ele: Double? = null,
    val time: Long? = null,
    val sym: String? = null,
    val gpxType: String? = null,
    val source: String? = null,
) {
    companion object {
        const val DEFAULT_NAME = "未命名点"

        fun manual(
            type: PoiType,
            name: String,
            desc: String,
            lat: Double,
            lng: Double,
        ): Poi = Poi(
            id = "poi-${System.currentTimeMillis()}-${shortId()}",
            type = type,
            name = name.takeIf { it.isNotBlank() } ?: DEFAULT_NAME,
            desc = desc,
            lat = lat,
            lng = lng,
            source = "manual",
        )
    }
}

data class OfflineBounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)

enum class OfflineStatus(val value: String) {
    DOWNLOADING("downloading"),
    READY("ready"),
    ERROR("error");

    companion object {
        fun fromValue(value: String?): OfflineStatus =
            entries.firstOrNull { it.value == value } ?: ERROR
    }
}

data class OfflineRegion(
    val id: String,
    val name: String,
    val workspaceId: String?,
    val sourceId: String,
    val bounds: OfflineBounds,
    val points: List<GeoPoint>,
    val zooms: List<Int>,
    val includeContour: Boolean,
    val tileCount: Int,
    val downloadedTiles: Int,
    val failedTiles: Int,
    val estimatedBytes: Long,
    val sizeBytes: Long,
    val progress: Int,
    val status: OfflineStatus,
    val createdAt: Long,
    val completedAt: Long?,
)

data class OfflineRegionInput(
    val name: String,
    val workspaceId: String?,
    val sourceId: String,
    val bounds: OfflineBounds,
    val points: List<GeoPoint>,
    val zooms: List<Int>,
    val includeContour: Boolean,
    val tileCount: Int,
    val estimatedBytes: Long,
    val replaceMapId: String? = null,
)

data class ActiveSession(
    val workspaceId: String,
    val sessionStartTime: Long,
    val accumulatedPausedMs: Long,
    val totalDistance: Double,
    val totalElevationGain: Double,
    val points: List<GeoPoint>,
    val isPaused: Boolean = false,
    val pauseStartedAt: Long? = null,
    val lastAcceptedPoint: GeoPoint? = null,
    val lastSampleTimestamp: Long = 0,
    val motionState: MotionState = MotionState.MOVING,
    val stationaryCount: Int = 0,
    val lastValidElevation: Int? = null,
    val savedAt: Long = System.currentTimeMillis(),
)

data class CompletedRecording(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val elevationGain: Double,
    val durationSeconds: Long,
    val startedAt: Long,
    val finishedAt: Long,
    val createdAt: Long,
)

data class LivePosition(
    val lat: Double,
    val lng: Double,
    val accuracy: Float?,
    val speed: Float?,
    val timestamp: Long,
)

enum class GpsStatus {
    WAITING,
    ACTIVE,
    DENIED,
    ERROR,
    UNSUPPORTED,
}

data class GpsState(
    val status: GpsStatus = GpsStatus.WAITING,
    val accuracy: Float? = null,
    val lastUpdate: Long? = null,
    val error: String = "",
) {
    companion object {
        val waiting = GpsState()
        val unsupported = GpsState(
            status = GpsStatus.UNSUPPORTED,
            error = "当前设备不支持定位",
        )
    }
}

enum class MotionState(val label: String) {
    STANDBY("就绪"),
    MOVING("运动中"),
    STATIONARY("静止"),
    BACKGROUND("后台记录");

    companion object {
        fun fromValue(value: String?): MotionState =
            entries.firstOrNull { it.name == value } ?: STANDBY
    }
}

enum class ContourStatus {
    IDLE,
    LOADING,
    READY,
    ERROR
}

enum class ToastType {
    SUCCESS,
    ERROR,
    INFO,
}

data class MapFocusRequest(
    val key: String,
    val points: List<GeoPoint>,
)

data class MapCamera(
    val lat: Double = 30.245,
    val lng: Double = 120.125,
    val zoom: Double = 14.0,
)

data class OfflinePlan(
    val urls: List<String>,
    val tileCount: Int,
    val estimatedBytes: Long,
)

data class BatteryState(
    val level: Float? = null,
    val charging: Boolean = false,
)

fun shortId(): String = UUID.randomUUID().toString().replace("-", "").take(6)

fun formatDuration(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = (seconds / 3600).toString().padStart(2, '0')
    val minutes = ((seconds % 3600) / 60).toString().padStart(2, '0')
    val remainder = (seconds % 60).toString().padStart(2, '0')
    return "$hours:$minutes:$remainder"
}
