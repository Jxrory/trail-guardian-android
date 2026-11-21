package com.trailguardian.core.gpx

import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.Poi
import com.trailguardian.core.model.PoiType
import java.time.Instant
import java.util.Locale

private val gpxPoiTypes: Map<PoiType, Pair<String, String>> = mapOf(
    PoiType.CAMP to ("Campground" to "camp"),
    PoiType.WATER to ("Drinking Water" to "water"),
    PoiType.DANGER to ("Danger Area" to "danger"),
    PoiType.VIEW to ("Scenic Area" to "view"),
    PoiType.MARKER to ("Waypoint" to "marker"),
)

fun escapeXml(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun isoTime(millis: Long): String = Instant.ofEpochMilli(millis).toString()

private fun formatCoordinate(value: Double): String =
    String.format(Locale.US, "%.7f", value)

private fun createWaypoint(waypoint: Poi): String? {
    if (!waypoint.lat.isFinite() || !waypoint.lng.isFinite()) return null
    val meta = gpxPoiTypes[waypoint.type] ?: gpxPoiTypes.getValue(PoiType.MARKER)
    val elevation = waypoint.ele?.takeIf { it.isFinite() }?.let { "<ele>${it}</ele>" } ?: ""
    val time = waypoint.time?.let { "<time>${isoTime(it)}</time>" } ?: ""
    val description = waypoint.desc.takeIf { it.isNotBlank() }
        ?.let { "<desc>${escapeXml(it)}</desc>" } ?: ""
    val sym = escapeXml(waypoint.sym?.takeIf { it.isNotBlank() } ?: meta.first)
    val gpxType = escapeXml(waypoint.gpxType?.takeIf { it.isNotBlank() } ?: meta.second)
    val name = escapeXml(waypoint.name.takeIf { it.isNotBlank() } ?: "未命名标注点")
    return "  <wpt lat=\"${formatCoordinate(waypoint.lat)}\" lon=\"${formatCoordinate(waypoint.lng)}\">" +
        "$elevation$time<name>$name</name>$description<sym>$sym</sym><type>$gpxType</type></wpt>"
}

fun createGpx(
    trackName: String,
    points: List<GeoPoint>,
    waypoints: List<Poi> = emptyList(),
): String {
    val waypointBlock = waypoints.mapNotNull(::createWaypoint)
    val waypointsXml = if (waypointBlock.isNotEmpty()) "\n" + waypointBlock.joinToString("\n") else ""
    val trackPoints = points.joinToString("\n") { point ->
        val elevation = point.ele?.let { "<ele>$it</ele>" } ?: ""
        val time = point.time?.let { "<time>${isoTime(it)}</time>" } ?: ""
        "      <trkpt lat=\"${formatCoordinate(point.lat)}\" lon=\"${formatCoordinate(point.lng)}\">" +
            "$elevation$time</trkpt>"
    }
    return """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="TrailGuardian" xmlns="http://www.topografix.com/GPX/1/1">
  <metadata><name>${escapeXml(trackName)}</name></metadata>$waypointsXml
  <trk>
    <name>${escapeXml(trackName)}</name>
    <trkseg>
$trackPoints
    </trkseg>
  </trk>
</gpx>"""
}

fun gpxFileName(baseName: String): String {
    val name = baseName.trim().ifBlank { "trail" }
    return if (name.endsWith(".gpx", ignoreCase = true)) name else "$name.gpx"
}
