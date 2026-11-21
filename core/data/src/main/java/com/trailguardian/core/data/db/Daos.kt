package com.trailguardian.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

object SessionIds {
    const val CURRENT = "current_active_track"
}

@Dao
interface WorkspaceDao {
    @Query("SELECT * FROM workspaces ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WorkspaceEntity>>

    @Query("SELECT * FROM workspaces ORDER BY createdAt DESC")
    suspend fun getAll(): List<WorkspaceEntity>

    @Query("SELECT * FROM workspaces WHERE id = :id")
    suspend fun get(id: String): WorkspaceEntity?

    @Query("SELECT * FROM workspaces WHERE id = :id")
    fun observe(id: String): Flow<WorkspaceEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(workspace: WorkspaceEntity)

    @Update
    suspend fun update(workspace: WorkspaceEntity)

    @Query("UPDATE workspaces SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: String, updatedAt: Long)

    @Query("DELETE FROM workspaces WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM track_points")
    fun observeAllPoints(): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM tracks WHERE workspaceId = :workspaceId")
    suspend fun getByWorkspace(workspaceId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun get(id: String): TrackEntity?

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY seq ASC")
    suspend fun getPoints(trackId: String): List<TrackPointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<TrackPointEntity>)

    @Query("DELETE FROM tracks WHERE workspaceId = :workspaceId AND kind = :kind")
    suspend fun deleteByKind(workspaceId: String, kind: String)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tracks WHERE workspaceId = :workspaceId")
    suspend fun deleteByWorkspace(workspaceId: String)
}

@Dao
interface PoiDao {
    @Query("SELECT * FROM pois ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PoiEntity>>

    @Query("SELECT * FROM pois WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    suspend fun getByWorkspace(workspaceId: String): List<PoiEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pois: List<PoiEntity>)

    @Query("DELETE FROM pois WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pois WHERE workspaceId = :workspaceId")
    suspend fun deleteByWorkspace(workspaceId: String)
}

@Dao
interface OfflineRegionDao {
    @Query("SELECT * FROM offline_regions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<OfflineRegionEntity>>

    @Query("SELECT * FROM offline_regions ORDER BY createdAt DESC")
    suspend fun getAll(): List<OfflineRegionEntity>

    @Query("SELECT * FROM offline_regions WHERE id = :id")
    suspend fun get(id: String): OfflineRegionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(region: OfflineRegionEntity)

    @Query("DELETE FROM offline_regions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM offline_regions")
    suspend fun clear()

    @Query(
        "UPDATE offline_regions SET downloadedTiles = :completed, failedTiles = :failed, " +
            "progress = :progress, sizeBytes = :sizeBytes WHERE id = :id",
    )
    suspend fun updateProgress(
        id: String,
        completed: Int,
        failed: Int,
        progress: Int,
        sizeBytes: Long,
    )

    @Query(
        "UPDATE offline_regions SET status = :status, completedAt = :completedAt, " +
            "downloadedTiles = :completed, failedTiles = :failed, progress = 100, " +
            "sizeBytes = :sizeBytes WHERE id = :id",
    )
    suspend fun complete(
        id: String,
        status: String,
        completed: Int,
        failed: Int,
        sizeBytes: Long,
        completedAt: Long,
    )

    @Query("UPDATE offline_regions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM active_session WHERE id = :id")
    fun observe(id: String = SessionIds.CURRENT): Flow<ActiveSessionEntity?>

    @Query("SELECT * FROM active_session WHERE id = :id")
    suspend fun get(id: String = SessionIds.CURRENT): ActiveSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ActiveSessionEntity)

    @Query("DELETE FROM active_session WHERE id = :id")
    suspend fun delete(id: String = SessionIds.CURRENT)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: SessionPointEntity)

    @Query("SELECT * FROM session_points WHERE sessionId = :id ORDER BY seq ASC")
    suspend fun getPoints(id: String = SessionIds.CURRENT): List<SessionPointEntity>

    @Query("SELECT COUNT(*) FROM session_points WHERE sessionId = :id")
    suspend fun countPoints(id: String = SessionIds.CURRENT): Int

    @Query("DELETE FROM session_points WHERE sessionId = :id")
    suspend fun deletePoints(id: String = SessionIds.CURRENT)

    @Query("DELETE FROM session_points")
    suspend fun clearAllPoints()
}
