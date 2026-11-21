package com.trailguardian.core.data.repository

import androidx.room.withTransaction
import com.trailguardian.core.data.db.PoiEntity
import com.trailguardian.core.data.db.TrackEntity
import com.trailguardian.core.data.db.TrackPointEntity
import com.trailguardian.core.data.db.TrailGuardianDatabase
import com.trailguardian.core.data.db.WorkspaceEntity
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.model.CompletedRecording
import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.Poi
import com.trailguardian.core.model.PoiType
import com.trailguardian.core.model.RecordedTrack
import com.trailguardian.core.model.TargetTrack
import com.trailguardian.core.model.Workspace
import com.trailguardian.core.model.shortId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class WorkspaceRepository(
    private val database: TrailGuardianDatabase,
) {
    private val workspaceDao = database.workspaceDao()
    private val trackDao = database.trackDao()
    private val poiDao = database.poiDao()

    val workspaces: Flow<List<Workspace>> = combine(
        workspaceDao.observeAll(),
        trackDao.observeAll(),
        trackDao.observeAllPoints(),
        poiDao.observeAll(),
    ) { workspaceRows, trackRows, pointRows, poiRows ->
        val pointsByTrack = pointRows.groupBy { it.trackId }
        val tracksByWorkspace = trackRows.groupBy { it.workspaceId }
        val poisByWorkspace = poiRows.groupBy { it.workspaceId }.mapValues { (_, rows) ->
            rows.map { it.toModel() }
        }
        workspaceRows.map { row ->
            val tracks = tracksByWorkspace[row.id].orEmpty()
            val targetRow = tracks.firstOrNull { it.kind == KIND_TARGET }
            val recordedRow = tracks.firstOrNull { it.kind == KIND_RECORDED }
            val target = targetRow?.let { row ->
                row.toTargetTrack(pointsByTrack[row.id].orEmpty())
            }
            val recorded = recordedRow?.let { row ->
                row.toRecordedTrack(pointsByTrack[row.id].orEmpty())
            }
            Workspace(
                id = row.id,
                name = row.name,
                createdAt = row.createdAt,
                updatedAt = row.updatedAt,
                targetTrack = target,
                recordedTrack = recorded,
                pois = poisByWorkspace[row.id].orEmpty(),
            )
        }
    }

    suspend fun createWorkspace(name: String): Workspace {
        val now = System.currentTimeMillis()
        val entity = WorkspaceEntity(
            id = "workspace-$now-${shortId()}",
            name = name.trim().ifBlank { GeoUtils.defaultWorkspaceName(now) },
            createdAt = now,
            updatedAt = now,
        )
        workspaceDao.insert(entity)
        return Workspace(entity.id, entity.name, now, now)
    }

    suspend fun deleteWorkspace(id: String) {
        workspaceDao.delete(id)
    }

    suspend fun getWorkspace(id: String): Workspace? {
        val row = workspaceDao.get(id) ?: return null
        val tracks = trackDao.getByWorkspace(id)
        val pois = poiDao.getByWorkspace(id).map { it.toModel() }
        val target = tracks.firstOrNull { it.kind == KIND_TARGET }?.let { row ->
            row.toTargetTrack(trackDao.getPoints(row.id))
        }
        val recorded = tracks.firstOrNull { it.kind == KIND_RECORDED }?.let { row ->
            row.toRecordedTrack(trackDao.getPoints(row.id))
        }
        return Workspace(
            id = row.id,
            name = row.name,
            createdAt = row.createdAt,
            updatedAt = row.updatedAt,
            targetTrack = target,
            recordedTrack = recorded,
            pois = pois,
        )
    }

    suspend fun saveRecordedTrack(
        workspaceId: String,
        workspaceName: String,
        recording: CompletedRecording,
    ): RecordedTrack = database.withTransaction {
        trackDao.deleteByKind(workspaceId, KIND_RECORDED)
        val track = RecordedTrack.create(workspaceName, recording)
        val now = System.currentTimeMillis()
        trackDao.insertAll(
            listOf(
                TrackEntity(
                    id = track.id,
                    workspaceId = workspaceId,
                    kind = KIND_RECORDED,
                    name = track.name,
                    distanceM = track.distanceMeters,
                    elevationGainM = track.elevationGain,
                    durationS = track.durationSeconds,
                    distanceText = track.distance,
                    eleGain = track.eleGain,
                    timeText = track.time,
                    startedAt = track.startedAt,
                    finishedAt = track.finishedAt,
                    importedAt = null,
                    createdAt = track.createdAt,
                    pointCount = track.points.size,
                ),
            ),
        )
        trackDao.insertPoints(
            track.points.mapIndexed { index, point ->
                TrackPointEntity(track.id, index, point.lat, point.lng, point.ele, point.time)
            },
        )
        workspaceDao.touch(workspaceId, now)
        track
    }

    suspend fun importTarget(
        workspaceId: String,
        name: String,
        points: List<GeoPoint>,
        waypoints: List<Poi>,
    ): TargetTrack? = database.withTransaction {
        val now = System.currentTimeMillis()
        var target: TargetTrack? = null
        if (points.isNotEmpty()) {
            trackDao.deleteByKind(workspaceId, KIND_TARGET)
            target = TargetTrack.create(points, name)
            trackDao.insertAll(
                listOf(
                    TrackEntity(
                        id = target.id,
                        workspaceId = workspaceId,
                        kind = KIND_TARGET,
                        name = target.name,
                        distanceM = 0.0,
                        elevationGainM = 0.0,
                        durationS = 0,
                        distanceText = "0.00",
                        eleGain = 0,
                        timeText = "00:00:00",
                        startedAt = null,
                        finishedAt = null,
                        importedAt = target.importedAt,
                        createdAt = target.importedAt,
                        pointCount = target.points.size,
                    ),
                ),
            )
            trackDao.insertPoints(
                points.mapIndexed { index, point ->
                    TrackPointEntity(target.id, index, point.lat, point.lng, point.ele, point.time)
                },
            )
        }
        if (waypoints.isNotEmpty()) {
            val batchTime = now
            poiDao.insertAll(
                waypoints.mapIndexed { index, waypoint ->
                    waypoint.toEntity(
                        workspaceId = workspaceId,
                        createdAt = batchTime + (waypoints.size - index),
                    )
                },
            )
        }
        workspaceDao.touch(workspaceId, now)
        target
    }

    suspend fun addPoi(workspaceId: String, poi: Poi) {
        val now = System.currentTimeMillis()
        poiDao.insertAll(listOf(poi.toEntity(workspaceId, now)))
        workspaceDao.touch(workspaceId, now)
    }

    suspend fun deletePoi(workspaceId: String, poiId: String) {
        poiDao.delete(poiId)
        workspaceDao.touch(workspaceId, System.currentTimeMillis())
    }

    suspend fun recordedPoints(workspaceId: String): List<GeoPoint> {
        val tracks = trackDao.getByWorkspace(workspaceId)
        val recorded = tracks.firstOrNull { it.kind == KIND_RECORDED } ?: return emptyList()
        return trackDao.getPoints(recorded.id).map { it.toGeoPoint() }
    }

    private fun TrackEntity.toTargetTrack(points: List<TrackPointEntity>): TargetTrack =
        TargetTrack(
            id = id,
            name = name,
            points = points.map { it.toGeoPoint() },
            pointCount = if (pointCount > 0) pointCount else points.size,
            importedAt = importedAt ?: createdAt,
        )

    private fun TrackEntity.toRecordedTrack(points: List<TrackPointEntity>): RecordedTrack =
        RecordedTrack(
            id = id,
            name = name,
            points = points.map { it.toGeoPoint() },
            distanceMeters = distanceM,
            elevationGain = elevationGainM,
            durationSeconds = durationS,
            distance = distanceText,
            eleGain = eleGain,
            time = timeText,
            startedAt = startedAt,
            finishedAt = finishedAt ?: createdAt,
            createdAt = createdAt,
        )

    private fun TrackPointEntity.toGeoPoint(): GeoPoint = GeoPoint(lat, lng, ele, time)

    private fun PoiEntity.toModel(): Poi = Poi(
        id = id,
        type = PoiType.fromKey(type),
        name = name,
        desc = description,
        lat = lat,
        lng = lng,
        ele = ele,
        time = time,
        sym = sym,
        gpxType = gpxType,
        source = source,
    )

    private fun Poi.toEntity(workspaceId: String, createdAt: Long): PoiEntity = PoiEntity(
        id = id,
        workspaceId = workspaceId,
        type = type.key,
        name = name,
        description = desc,
        lat = lat,
        lng = lng,
        ele = ele,
        time = time,
        sym = sym,
        gpxType = gpxType,
        source = source,
        createdAt = createdAt,
    )

    companion object {
        const val KIND_TARGET = "target"
        const val KIND_RECORDED = "recorded"
    }
}
