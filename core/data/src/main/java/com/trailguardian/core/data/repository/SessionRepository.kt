package com.trailguardian.core.data.repository

import androidx.room.withTransaction
import com.trailguardian.core.data.db.ActiveSessionEntity
import com.trailguardian.core.data.db.SessionIds
import com.trailguardian.core.data.db.SessionPointEntity
import com.trailguardian.core.data.db.TrailGuardianDatabase
import com.trailguardian.core.model.ActiveSession
import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.MotionState

class SessionRepository(
    private val database: TrailGuardianDatabase,
) {
    private val dao = database.sessionDao()

    suspend fun load(): ActiveSession? {
        val entity = dao.get() ?: return null
        if (!entity.isRecording) return null
        val points = dao.getPoints().map { it.toGeoPoint() }
        if (points.isEmpty()) return null
        return buildSession(entity, dao.getPoints())
    }

    suspend fun save(session: ActiveSession) {
        dao.upsert(session.toEntity())
    }

    suspend fun appendPoint(seq: Int, point: GeoPoint, session: ActiveSession) {
        database.withTransaction {
            dao.insertPoint(
                SessionPointEntity(
                    sessionId = SessionIds.CURRENT,
                    seq = seq,
                    lat = point.lat,
                    lng = point.lng,
                    ele = point.ele,
                    time = point.time,
                ),
            )
            dao.upsert(session.toEntity())
        }
    }

    suspend fun clear() {
        database.withTransaction {
            dao.deletePoints()
            dao.delete()
        }
    }

    private fun buildSession(entity: ActiveSessionEntity, points: List<SessionPointEntity>): ActiveSession =
        ActiveSession(
            workspaceId = entity.workspaceId,
            sessionStartTime = entity.sessionStartTime,
            accumulatedPausedMs = entity.accumulatedPausedMs,
            totalDistance = entity.totalDistanceM,
            totalElevationGain = entity.totalElevationGainM,
            points = points.map { it.toGeoPoint() },
            isPaused = entity.isPaused,
            pauseStartedAt = entity.pauseStartedAt,
            lastAcceptedPoint = entity.lastAcceptedLat?.let { lat ->
                GeoPoint(
                    lat = lat,
                    lng = entity.lastAcceptedLng ?: 0.0,
                    ele = entity.lastAcceptedEle,
                    time = entity.lastAcceptedTime,
                )
            },
            lastSampleTimestamp = entity.lastSampleTimestamp,
            motionState = MotionState.fromValue(entity.motionState),
            stationaryCount = entity.stationaryCount,
            lastValidElevation = entity.lastValidElevation,
            savedAt = entity.savedAt,
        )

    private fun ActiveSession.toEntity(): ActiveSessionEntity = ActiveSessionEntity(
        id = SessionIds.CURRENT,
        workspaceId = workspaceId,
        sessionStartTime = sessionStartTime,
        accumulatedPausedMs = accumulatedPausedMs,
        totalDistanceM = totalDistance,
        totalElevationGainM = totalElevationGain,
        isRecording = true,
        isPaused = isPaused,
        pauseStartedAt = pauseStartedAt,
        lastAcceptedLat = lastAcceptedPoint?.lat,
        lastAcceptedLng = lastAcceptedPoint?.lng,
        lastAcceptedEle = lastAcceptedPoint?.ele,
        lastAcceptedTime = lastAcceptedPoint?.time,
        lastSampleTimestamp = lastSampleTimestamp,
        motionState = motionState.name,
        stationaryCount = stationaryCount,
        lastValidElevation = lastValidElevation,
        savedAt = savedAt,
    )

    private fun SessionPointEntity.toGeoPoint(): GeoPoint = GeoPoint(lat, lng, ele, time)
}
