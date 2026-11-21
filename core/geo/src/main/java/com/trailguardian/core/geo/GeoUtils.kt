package com.trailguardian.core.geo

import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.OfflineBounds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoUtils {
    const val EARTH_RADIUS_METERS = 6371008.8

    private val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    private val cardinalLabels = arrayOf("北", "东北", "东", "东南", "南", "西南", "西", "西北")

    fun distanceInMeters(from: GeoPoint, to: GeoPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val deltaLat = lat2 - lat1
        val deltaLng = Math.toRadians(to.lng - from.lng)
        val a = sin(deltaLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(deltaLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    fun formatDuration(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val hours = (seconds / 3600).toString().padStart(2, '0')
        val minutes = ((seconds % 3600) / 60).toString().padStart(2, '0')
        val remainder = (seconds % 60).toString().padStart(2, '0')
        return "$hours:$minutes:$remainder"
    }

    fun formatTrackDate(timestamp: Long = System.currentTimeMillis()): String =
        synchronized(dateFormat) { dateFormat.format(Date(timestamp)) }

    fun defaultWorkspaceName(now: Long = System.currentTimeMillis()): String =
        "外出计划 ${formatTrackDate(now)}"

    fun defaultOfflineName(workspaceName: String?): String =
        "${workspaceName?.takeIf { it.isNotBlank() } ?: "户外计划"} 离线地图"

    fun normalizeHeading(value: Float): Float = ((value % 360f) + 360f) % 360f

    fun describeHeading(heading: Float?): String {
        if (heading == null || heading.isNaN()) return "方向待获取"
        val normalized = normalizeHeading(heading)
        val direction = cardinalLabels[Math.round(normalized / 45f) % 8]
        return "$direction ${Math.round(normalized)}°"
    }

    fun shortestHeadingDelta(from: Float, to: Float): Float =
        ((to - from + 540f) % 360f) - 180f

    data class Summary(
        val distanceMeters: Double,
        val elevationGain: Double,
    )

    fun summarizePoints(points: List<GeoPoint>): Summary {
        var distance = 0.0
        var elevationGain = 0.0
        var lastElevation: Int? = null
        points.forEachIndexed { index, point ->
            if (index > 0) distance += distanceInMeters(points[index - 1], point)
            val elevation = point.ele
            if (elevation != null) {
                val previous = lastElevation
                if (previous == null) {
                    lastElevation = elevation
                } else {
                    val delta = elevation - previous
                    if (kotlin.math.abs(delta) >= 3) {
                        if (delta > 0) elevationGain += delta
                        lastElevation = elevation
                    }
                }
            }
        }
        return Summary(distance, elevationGain)
    }

    fun boundsFromPoints(points: List<GeoPoint>): OfflineBounds? {
        if (points.isEmpty()) return null
        var north = Double.NEGATIVE_INFINITY
        var south = Double.POSITIVE_INFINITY
        var east = Double.NEGATIVE_INFINITY
        var west = Double.POSITIVE_INFINITY
        points.forEach { point ->
            if (point.lat > north) north = point.lat
            if (point.lat < south) south = point.lat
            if (point.lng > east) east = point.lng
            if (point.lng < west) west = point.lng
        }
        return OfflineBounds(north, south, east, west)
    }

    fun circlePolygon(lat: Double, lng: Double, radiusMeters: Double, segments: Int = 64): List<GeoPoint> {
        val points = ArrayList<GeoPoint>(segments + 1)
        val latDelta = Math.toDegrees(radiusMeters / EARTH_RADIUS_METERS)
        val cosLat = cos(Math.toRadians(lat)).coerceAtLeast(1e-6)
        val lngDelta = Math.toDegrees(radiusMeters / (EARTH_RADIUS_METERS * cosLat))
        for (index in 0..segments) {
            val angle = 2.0 * Math.PI * index / segments
            points.add(
                GeoPoint(
                    lat = lat + latDelta * sin(angle),
                    lng = lng + lngDelta * cos(angle),
                ),
            )
        }
        return points
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val megabytes = bytes / (1024.0 * 1024.0)
        if (megabytes < 1.0) return "${(bytes / 1024.0).toInt()} KB"
        val digits = if (megabytes >= 10) 1 else 2
        return String.format(Locale.US, "%.${digits}f MB", megabytes)
    }
}
