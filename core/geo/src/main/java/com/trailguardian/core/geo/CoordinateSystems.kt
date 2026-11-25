package com.trailguardian.core.geo

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

enum class CoordinateSystem(val label: String, val detail: String) {
    WGS84("WGS84", "国际通用"),
    GCJ02("GCJ-02", "国测局"),
    BD09("BD09", "百度"),
}

enum class CoordinateFormat(val label: String) {
    DECIMAL("十进制"),
    DMS("度分秒"),
    DM("度分"),
}

data class Coordinate(val lat: Double, val lng: Double)

object CoordinateTransforms {
    private const val PI = 3.1415926535897932384626
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val X_PI = PI * 3000.0 / 180.0
    private const val EARTH_RADIUS_METERS = 6378137.0

    fun isOutsideChina(lat: Double, lng: Double): Boolean =
        lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271

    fun wgs84ToGcj02(lat: Double, lng: Double): Coordinate {
        if (isOutsideChina(lat, lng)) return Coordinate(lat, lng)
        val offset = gcj02Offset(lat, lng)
        return Coordinate(lat + offset.lat, lng + offset.lng)
    }

    fun gcj02ToWgs84(lat: Double, lng: Double): Coordinate {
        if (isOutsideChina(lat, lng)) return Coordinate(lat, lng)
        var approximate = Coordinate(lat, lng)
        repeat(12) {
            val gcj = wgs84ToGcj02(approximate.lat, approximate.lng)
            approximate = Coordinate(
                lat = approximate.lat - (gcj.lat - lat),
                lng = approximate.lng - (gcj.lng - lng),
            )
        }
        return approximate
    }

    fun gcj02ToBd09(lat: Double, lng: Double): Coordinate {
        val z = sqrt(lng * lng + lat * lat) + 0.00002 * sin(lat * X_PI)
        val theta = atan2(lat, lng) + 0.000003 * cos(lng * X_PI)
        return Coordinate(
            lat = z * sin(theta) + 0.006,
            lng = z * cos(theta) + 0.0065,
        )
    }

    fun bd09ToGcj02(lat: Double, lng: Double): Coordinate {
        val x = lng - 0.0065
        val y = lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        return Coordinate(
            lat = z * sin(theta),
            lng = z * cos(theta),
        )
    }

    fun convert(source: CoordinateSystem, target: CoordinateSystem, lat: Double, lng: Double): Coordinate {
        val wgs84 = when (source) {
            CoordinateSystem.WGS84 -> Coordinate(lat, lng)
            CoordinateSystem.GCJ02 -> gcj02ToWgs84(lat, lng)
            CoordinateSystem.BD09 -> {
                val gcj02 = bd09ToGcj02(lat, lng)
                gcj02ToWgs84(gcj02.lat, gcj02.lng)
            }
        }
        val gcj02 = wgs84ToGcj02(wgs84.lat, wgs84.lng)
        return when (target) {
            CoordinateSystem.WGS84 -> wgs84
            CoordinateSystem.GCJ02 -> gcj02
            CoordinateSystem.BD09 -> gcj02ToBd09(gcj02.lat, gcj02.lng)
        }
    }

    fun allSystems(source: CoordinateSystem, lat: Double, lng: Double): Map<CoordinateSystem, Coordinate> =
        CoordinateSystem.entries.associateWith { target -> convert(source, target, lat, lng) }

    fun distanceMeters(first: Coordinate, second: Coordinate): Double {
        val lat1 = Math.toRadians(first.lat)
        val lat2 = Math.toRadians(second.lat)
        val deltaLat = lat2 - lat1
        val deltaLng = Math.toRadians(second.lng - first.lng)
        val a = sin(deltaLat / 2).let { it * it } +
            cos(lat1) * cos(lat2) * sin(deltaLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * atan2(sqrt(a), sqrt(1 - a))
    }

    private data class Offset(val lat: Double, val lng: Double)

    private fun gcj02Offset(lat: Double, lng: Double): Offset {
        var deltaLat = transformLat(lng - 105.0, lat - 35.0)
        var deltaLng = transformLng(lng - 105.0, lat - 35.0)
        val radLat = Math.toRadians(lat)
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        deltaLat = deltaLat * 180.0 / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        deltaLng = deltaLng * 180.0 / (A / sqrtMagic * cos(radLat) * PI)
        return Offset(deltaLat, deltaLng)
    }

    private fun transformLat(x: Double, y: Double): Double {
        var value = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        value += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        value += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        value += (160.0 * sin(y / 12.0 * PI) + 320.0 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return value
    }

    private fun transformLng(x: Double, y: Double): Double {
        var value = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        value += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        value += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        value += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return value
    }
}

object CoordinateFormatter {
    private val numberPattern = Regex("[-+]?\\d+(?:[.,]\\d+)?")

    fun format(value: Double, format: CoordinateFormat): String {
        val absolute = abs(value)
        val degrees = floor(absolute).toInt()
        val decimalMinutes = (absolute - degrees) * 60.0
        val minutes = floor(decimalMinutes).toInt()
        val seconds = (decimalMinutes - minutes) * 60.0
        return when (format) {
            CoordinateFormat.DECIMAL -> String.format(Locale.US, "%.8f", absolute)
            CoordinateFormat.DM -> String.format(Locale.US, "%d %.6f", degrees, decimalMinutes)
            CoordinateFormat.DMS -> String.format(Locale.US, "%d %d %.4f", degrees, minutes, seconds)
        }
    }

    fun parse(text: String, format: CoordinateFormat, maxDegrees: Double): Double? {
        val values = numberPattern.findAll(text)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .toList()
        if (values.isEmpty()) return null
        val degrees = abs(values[0])
        val decimal = when (format) {
            CoordinateFormat.DECIMAL -> if (degrees <= maxDegrees) degrees else return null
            CoordinateFormat.DM -> {
                if (values.size < 2) return null
                val minutes = values[1]
                if (minutes !in 0.0..<60.0) return null
                degrees + minutes / 60.0
            }
            CoordinateFormat.DMS -> {
                if (values.size < 3) return null
                val minutes = values[1]
                val seconds = values[2]
                if (minutes !in 0.0..<60.0 || seconds !in 0.0..<60.0) return null
                degrees + minutes / 60.0 + seconds / 3600.0
            }
        }
        return decimal.takeIf { it <= maxDegrees }
    }

    fun describe(coordinate: Coordinate): String {
        val latLabel = if (coordinate.lat >= 0) "北纬" else "南纬"
        val lngLabel = if (coordinate.lng >= 0) "东经" else "西经"
        return String.format(
            Locale.US,
            "%s %.8f, %s %.8f",
            latLabel,
            abs(coordinate.lat),
            lngLabel,
            abs(coordinate.lng),
        )
    }
}
