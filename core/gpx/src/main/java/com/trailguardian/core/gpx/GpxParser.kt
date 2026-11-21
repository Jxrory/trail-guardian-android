package com.trailguardian.core.gpx

import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.Poi
import com.trailguardian.core.model.PoiType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.OffsetDateTime

class GpxParseException(message: String) : Exception(message)

data class ParsedTrackFile(
    val points: List<GeoPoint>,
    val waypoints: List<Poi>,
)

private val poiTypeKeywords: Map<PoiType, List<String>> = mapOf(
    PoiType.CAMP to listOf(
        "camp", "campground", "campsite", "camping", "tent", "shelter", "hut",
        "bivouac", "lodge", "营地", "露营", "帐篷", "庇护",
    ),
    PoiType.WATER to listOf(
        "water", "drinking", "spring", "stream", "river", "well", "fountain",
        "tap", "水源", "取水", "溪", "泉", "河",
    ),
    PoiType.DANGER to listOf(
        "danger", "hazard", "warning", "risk", "rockfall", "cliff", "avalanche",
        "danger area", "危险", "警告", "悬崖", "落石", "雪崩",
    ),
    PoiType.VIEW to listOf(
        "view", "viewpoint", "scenic", "scenery", "overlook", "peak", "summit",
        "mountain", "观景", "风景", "山顶", "山峰",
    ),
)

private fun inferPoiType(text: String): PoiType {
    val haystack = text.lowercase()
    poiTypeKeywords.forEach { (type, keywords) ->
        if (keywords.any { haystack.contains(it) }) return type
    }
    return PoiType.MARKER
}

private fun parseTime(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(value.trim()).toInstant().toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

private fun parseNumber(value: String?): Double? {
    if (value.isNullOrBlank()) return null
    return value.trim().toDoubleOrNull()
}

private fun createParser(text: String): XmlPullParser {
    val factory = XmlPullParserFactory.newInstance()
    factory.isNamespaceAware = true
    val parser = factory.newPullParser()
    parser.setInput(StringReader(text))
    return parser
}

fun parseTrackFile(text: String, filename: String = ""): ParsedTrackFile {
    val isKml = filename.lowercase().endsWith(".kml")
    val parsed = if (isKml) parseKml(text) else parseGpx(text)
    if (parsed.points.isEmpty() && parsed.waypoints.isEmpty()) {
        throw GpxParseException("未解析到有效的目标路径或标注点")
    }
    return parsed
}

private fun parseGpx(text: String): ParsedTrackFile {
    val points = ArrayList<GeoPoint>()
    val waypoints = ArrayList<Poi>()
    try {
        val parser = createParser(text)
        var event = parser.eventType
        var trackPoint: MutableMap<String, String?>? = null
        var waypoint: MutableMap<String, String?>? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name.lowercase()) {
                    "trkpt" -> trackPoint = mutableMapOf(
                        "lat" to parser.getAttributeValue(null, "lat"),
                        "lon" to parser.getAttributeValue(null, "lon"),
                        "ele" to null,
                        "time" to null,
                    )
                    "wpt" -> waypoint = mutableMapOf(
                        "lat" to parser.getAttributeValue(null, "lat"),
                        "lon" to parser.getAttributeValue(null, "lon"),
                        "ele" to null,
                        "time" to null,
                        "name" to null,
                        "cmt" to null,
                        "desc" to null,
                        "sym" to null,
                        "type" to null,
                    )
                    "ele" -> {
                        val value = parser.nextText()
                        trackPoint?.let { if (it["ele"] == null) it["ele"] = value }
                        waypoint?.let { if (it["ele"] == null) it["ele"] = value }
                    }
                    "time" -> {
                        val value = parser.nextText()
                        trackPoint?.let { if (it["time"] == null) it["time"] = value }
                        waypoint?.let { if (it["time"] == null) it["time"] = value }
                    }
                    "name" -> waypoint?.let {
                        if (it["name"] == null) it["name"] = parser.nextText()
                    }
                    "cmt" -> waypoint?.let {
                        if (it["cmt"] == null) it["cmt"] = parser.nextText()
                    }
                    "desc" -> waypoint?.let {
                        if (it["desc"] == null) it["desc"] = parser.nextText()
                    }
                    "sym" -> waypoint?.let {
                        if (it["sym"] == null) it["sym"] = parser.nextText()
                    }
                    "type" -> waypoint?.let {
                        if (it["type"] == null) it["type"] = parser.nextText()
                    }
                }
            } else if (event == XmlPullParser.END_TAG) {
                when (parser.name.lowercase()) {
                    "trkpt" -> {
                        trackPoint?.let { data ->
                            val lat = data["lat"]?.toDoubleOrNull()
                            val lng = data["lon"]?.toDoubleOrNull()
                            if (lat != null && lng != null) {
                                points.add(
                                    GeoPoint(
                                        lat = lat,
                                        lng = lng,
                                        ele = parseNumber(data["ele"])?.toInt(),
                                        time = parseTime(data["time"]),
                                    ),
                                )
                            }
                        }
                        trackPoint = null
                    }
                    "wpt" -> {
                        waypoint?.let { data ->
                            val lat = data["lat"]?.toDoubleOrNull()
                            val lng = data["lon"]?.toDoubleOrNull()
                            if (lat != null && lng != null) {
                                val name = data["name"]?.trim().orEmpty()
                                val comment = data["cmt"]?.trim().orEmpty()
                                val description = data["desc"]?.trim().orEmpty()
                                val sym = data["sym"]?.trim().orEmpty()
                                val gpxType = data["type"]?.trim().orEmpty()
                                val type = inferPoiType(
                                    listOf(name, comment, description, sym, gpxType).joinToString(" "),
                                )
                                val displayName = name.ifBlank {
                                    comment.ifBlank {
                                        description.ifBlank {
                                            "GPX 标注点 #${waypoints.size + 1}"
                                        }
                                    }
                                }
                                waypoints.add(
                                    Poi(
                                        id = "gpx-${System.currentTimeMillis()}-${waypoints.size}",
                                        type = type,
                                        name = displayName,
                                        desc = description.ifBlank { comment },
                                        lat = lat,
                                        lng = lng,
                                        ele = parseNumber(data["ele"]),
                                        time = parseTime(data["time"]),
                                        sym = sym.ifBlank { null },
                                        gpxType = gpxType.ifBlank { null },
                                        source = "gpx",
                                    ),
                                )
                            }
                        }
                        waypoint = null
                    }
                }
            }
            event = parser.next()
        }
    } catch (error: GpxParseException) {
        throw error
    } catch (_: Exception) {
        throw GpxParseException("GPX 文件格式不正确")
    }
    return ParsedTrackFile(points, waypoints)
}

private fun parseKml(text: String): ParsedTrackFile {
    val points = ArrayList<GeoPoint>()
    try {
        val parser = createParser(text)
        var event = parser.eventType
        var inCoordinates = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.lowercase() == "coordinates") {
                        inCoordinates = true
                        val value = parser.nextText()
                        value.trim().split(Regex("\\s+"))
                            .filter { it.isNotBlank() }
                            .forEach { tuple ->
                                val parts = tuple.split(",")
                                val lng = parts.getOrNull(0)?.toDoubleOrNull()
                                val lat = parts.getOrNull(1)?.toDoubleOrNull()
                                val ele = parts.getOrNull(2)?.toDoubleOrNull()
                                if (lat != null && lng != null) {
                                    points.add(
                                        GeoPoint(
                                            lat = lat,
                                            lng = lng,
                                            ele = ele?.toInt(),
                                            time = null,
                                        ),
                                    )
                                }
                            }
                        inCoordinates = false
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.lowercase() == "coordinates") inCoordinates = false
                }
            }
            event = parser.next()
        }
    } catch (_: Exception) {
        throw GpxParseException("KML 文件格式不正确")
    }
    return ParsedTrackFile(points, emptyList())
}
