package com.trailguardian.core.geo

import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.MapSourceConfig
import com.trailguardian.core.model.MapSources
import com.trailguardian.core.model.OfflineBounds
import com.trailguardian.core.model.OfflinePlan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

object OfflinePlanner {
    val ZOOM_LEVELS: List<Int> = (1..18).toList()
    val DEFAULT_ZOOMS: List<Int> = (1..17).toList()
    const val MAX_OFFLINE_TILES = 6000
    const val APPROX_TILE_BYTES = 24L * 1024L

    data class TileRange(
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
    )

    fun expandTileUrl(template: String, z: Int, x: Int, y: Int): String =
        template
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())

    fun latLngToTile(lat: Double, lng: Double, zoom: Int): Pair<Int, Int> {
        val size = 2.0.pow(zoom)
        val x = floor((lng + 180.0) / 360.0 * size).toInt()
        val radLat = Math.toRadians(lat)
        val y = floor(
            (1.0 - ln(tan(radLat) + 1.0 / Math.cos(radLat)) / Math.PI) / 2.0 * size,
        ).toInt()
        return x to y
    }

    fun tileRangeForBounds(bounds: OfflineBounds, zoom: Int): TileRange {
        val (westX, northY) = latLngToTile(bounds.north, bounds.west, zoom)
        val (eastX, southY) = latLngToTile(bounds.south, bounds.east, zoom)
        return TileRange(
            minX = min(westX, eastX),
            maxX = max(westX, eastX),
            minY = min(northY, southY),
            maxY = max(northY, southY),
        )
    }

    fun buildTileUrlsForBounds(
        source: MapSourceConfig,
        bounds: OfflineBounds,
        minZoom: Int,
        maxZoom: Int,
    ): List<String> {
        val urls = ArrayList<String>()
        val templates = listOf(source.baseUrl) + source.overlayUrls
        val rangeZooms = minZoom..maxZoom
        for (z in rangeZooms) {
            val range = tileRangeForBounds(bounds, z)
            for (x in range.minX..range.maxX) {
                for (y in range.minY..range.maxY) {
                    for (template in templates) {
                        urls.add(expandTileUrl(template, z, x, y))
                    }
                }
            }
        }
        return urls
    }

    fun buildOfflineUrls(
        bounds: OfflineBounds,
        zooms: List<Int>,
        source: MapSourceConfig = MapSources.ESRI_SATELLITE,
        includeContour: Boolean = false,
    ): List<String> {
        if (zooms.isEmpty()) return emptyList()
        val selectedMin = zooms.min()
        val selectedMax = zooms.max()
        val sources = if (includeContour) {
            listOf(source, MapSources.CONTOUR_TERRAIN)
        } else {
            listOf(source)
        }
        val urls = LinkedHashSet<String>()
        sources.forEach { mapSource ->
            val minZoom = max(selectedMin, 1)
            val maxZoom = min(selectedMax, mapSource.maxNativeZoom)
            if (minZoom <= maxZoom) {
                urls.addAll(buildTileUrlsForBounds(mapSource, bounds, minZoom, maxZoom))
            }
        }
        return urls.toList()
    }

    fun createOfflinePlan(
        bounds: OfflineBounds?,
        zooms: List<Int>,
        source: MapSourceConfig = MapSources.ESRI_SATELLITE,
        includeContour: Boolean = false,
    ): OfflinePlan {
        if (bounds == null) return OfflinePlan(emptyList(), 0, 0)
        val urls = buildOfflineUrls(bounds, zooms, source, includeContour)
        return OfflinePlan(
            urls = urls,
            tileCount = urls.size,
            estimatedBytes = urls.size * APPROX_TILE_BYTES,
        )
    }

    fun regionUrls(
        bounds: OfflineBounds,
        zooms: List<Int>,
        sourceId: String,
        includeContour: Boolean,
    ): List<String> = buildOfflineUrls(
        bounds = bounds,
        zooms = zooms,
        source = MapSources.getById(sourceId),
        includeContour = includeContour,
    )

    fun effectiveDownloadUrl(url: String): String = url

    fun isTerrainUrl(url: String): Boolean =
        url.contains("elevation-tiles-prod/terrarium")

    fun pointsBounds(points: List<GeoPoint>): OfflineBounds? =
        GeoUtils.boundsFromPoints(points)
}
