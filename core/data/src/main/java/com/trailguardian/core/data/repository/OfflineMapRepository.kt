package com.trailguardian.core.data.repository

import android.content.Context
import androidx.room.withTransaction
import androidx.work.WorkManager
import com.trailguardian.core.data.JsonCodec
import com.trailguardian.core.data.db.OfflineRegionEntity
import com.trailguardian.core.data.db.TrailGuardianDatabase
import com.trailguardian.core.data.tile.TileCache
import com.trailguardian.core.geo.OfflinePlanner
import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.OfflineBounds
import com.trailguardian.core.model.OfflineRegion
import com.trailguardian.core.model.OfflineRegionInput
import com.trailguardian.core.model.OfflineStatus
import com.trailguardian.core.model.shortId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.regex.Pattern

class OfflineMapRepository(
    private val context: Context,
    private val database: TrailGuardianDatabase,
    private val cache: TileCache,
) {
    private val dao = database.offlineRegionDao()

    val regions: Flow<List<OfflineRegion>> = dao.observeAll().map { rows ->
        rows.map { it.toModel() }
    }

    suspend fun getRegion(id: String): OfflineRegion? = dao.get(id)?.toModel()

    suspend fun createRegion(input: OfflineRegionInput): OfflineRegion = database.withTransaction {
        val now = System.currentTimeMillis()
        val replaceId = input.replaceMapId
        if (replaceId != null) {
            val previous = dao.get(replaceId)
            if (previous != null) {
                deleteCachedTiles(previous)
                dao.delete(previous.id)
            }
        }
        val entity = OfflineRegionEntity(
            id = "offline-$now-${shortId()}",
            name = input.name,
            workspaceId = input.workspaceId,
            sourceId = input.sourceId,
            north = input.bounds.north,
            south = input.bounds.south,
            east = input.bounds.east,
            west = input.bounds.west,
            pointsJson = JsonCodec.pointsToJson(input.points),
            zoomsJson = JsonCodec.intsToJson(input.zooms.sorted()),
            includeContour = input.includeContour,
            tileCount = input.tileCount,
            downloadedTiles = 0,
            failedTiles = 0,
            estimatedBytes = input.estimatedBytes,
            sizeBytes = 0,
            progress = 0,
            status = OfflineStatus.DOWNLOADING.value,
            createdAt = now,
            completedAt = null,
        )
        dao.insert(entity)
        enqueueDownload(entity.id)
        entity.toModel()
    }

    suspend fun updateProgress(id: String, progress: Int, completed: Int, failed: Int, bytes: Long) {
        dao.updateProgress(id, completed, failed, progress, bytes)
    }

    suspend fun complete(id: String, resultTotal: Int, failed: Int, bytes: Long) {
        val status = if (failed > 0) OfflineStatus.ERROR else OfflineStatus.READY
        dao.complete(
            id = id,
            status = status.value,
            completed = resultTotal - failed,
            failed = failed,
            sizeBytes = bytes,
            completedAt = System.currentTimeMillis(),
        )
    }

    suspend fun markError(id: String) {
        dao.updateStatus(id, OfflineStatus.ERROR.value)
    }

    suspend fun deleteRegion(region: OfflineRegion) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(region.id))
        deleteCachedTiles(region.toEntity())
        dao.delete(region.id)
    }

    suspend fun clearAll() {
        WorkManager.getInstance(context).cancelAllWork()
        cache.clear()
        dao.clear()
    }

    fun regionUrls(region: OfflineRegion): List<String> = OfflinePlanner.regionUrls(
        bounds = region.bounds,
        zooms = region.zooms,
        sourceId = region.sourceId,
        includeContour = region.includeContour,
    )

    fun cacheTileCount(): Int = cache.count()

    fun cacheSizeBytes(): Long = cache.totalBytes()

    fun cache() = cache

    private fun enqueueDownload(regionId: String) {
        com.trailguardian.core.data.worker.OfflineDownloadWorker.enqueue(context, regionId)
    }

    private fun deleteCachedTiles(entity: OfflineRegionEntity) {
        val region = entity.toModel()
        val urls = regionUrls(region)
        cache.delete(urls)
        if (entity.includeContour) {
            val contourKeys = urls.mapNotNull { parseTerrainKey(it) }
                .map { (z, x, y) -> "contour://$z/$x/$y" }
            cache.delete(contourKeys)
        }
    }

    private fun parseTerrainKey(url: String): Triple<Int, Int, Int>? {
        val matcher = TERRAIN_PATTERN.matcher(url)
        if (!matcher.find()) return null
        return try {
            Triple(
                matcher.group(1)!!.toInt(),
                matcher.group(2)!!.toInt(),
                matcher.group(3)!!.toInt(),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun OfflineRegionEntity.toModel(): OfflineRegion = OfflineRegion(
        id = id,
        name = name,
        workspaceId = workspaceId,
        sourceId = sourceId,
        bounds = OfflineBounds(north, south, east, west),
        points = JsonCodec.pointsFromJson(pointsJson).ifEmpty {
            listOf(
                GeoPoint(north, west),
                GeoPoint(north, east),
                GeoPoint(south, east),
                GeoPoint(south, west),
            )
        },
        zooms = JsonCodec.intsFromJson(zoomsJson),
        includeContour = includeContour,
        tileCount = tileCount,
        downloadedTiles = downloadedTiles,
        failedTiles = failedTiles,
        estimatedBytes = estimatedBytes,
        sizeBytes = sizeBytes,
        progress = progress,
        status = OfflineStatus.fromValue(status),
        createdAt = createdAt,
        completedAt = completedAt,
    )

    private fun OfflineRegion.toEntity(): OfflineRegionEntity = OfflineRegionEntity(
        id = id,
        name = name,
        workspaceId = workspaceId,
        sourceId = sourceId,
        north = bounds.north,
        south = bounds.south,
        east = bounds.east,
        west = bounds.west,
        pointsJson = JsonCodec.pointsToJson(points),
        zoomsJson = JsonCodec.intsToJson(zooms),
        includeContour = includeContour,
        tileCount = tileCount,
        downloadedTiles = downloadedTiles,
        failedTiles = failedTiles,
        estimatedBytes = estimatedBytes,
        sizeBytes = sizeBytes,
        progress = progress,
        status = status.value,
        createdAt = createdAt,
        completedAt = completedAt,
    )

    companion object {
        private val TERRAIN_PATTERN =
            Pattern.compile("terrarium/(\\d+)/(\\d+)/(\\d+)\\.png")
        fun workName(regionId: String): String = "offline-$regionId"
    }
}
