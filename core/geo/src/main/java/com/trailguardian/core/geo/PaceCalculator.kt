package com.trailguardian.core.geo

import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

enum class DistanceUnit(val label: String, val shortLabel: String, val kilometers: Double) {
    KILOMETER("公里", "km", 1.0),
    MILE("英里", "mi", 1.609344),
}

data class PaceResult(
    val distanceKilometers: Double,
    val durationSeconds: Long,
    val paceSecondsPerKilometer: Double,
)

object PaceCalculator {
    fun paceFromDistanceAndTime(distanceKilometers: Double, durationSeconds: Long): PaceResult? {
        if (distanceKilometers <= 0.0 || durationSeconds <= 0) return null
        return PaceResult(
            distanceKilometers = distanceKilometers,
            durationSeconds = durationSeconds,
            paceSecondsPerKilometer = durationSeconds.toDouble() / distanceKilometers,
        )
    }

    fun timeFromDistanceAndPace(distanceKilometers: Double, paceSecondsPerKilometer: Double): PaceResult? {
        if (distanceKilometers <= 0.0 || paceSecondsPerKilometer <= 0.0) return null
        return PaceResult(
            distanceKilometers = distanceKilometers,
            durationSeconds = (distanceKilometers * paceSecondsPerKilometer).roundToLong(),
            paceSecondsPerKilometer = paceSecondsPerKilometer,
        )
    }

    fun distanceFromTimeAndPace(durationSeconds: Long, paceSecondsPerKilometer: Double): PaceResult? {
        if (durationSeconds <= 0 || paceSecondsPerKilometer <= 0.0) return null
        return PaceResult(
            distanceKilometers = durationSeconds.toDouble() / paceSecondsPerKilometer,
            durationSeconds = durationSeconds,
            paceSecondsPerKilometer = paceSecondsPerKilometer,
        )
    }

    fun formatDuration(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            seconds / 3600,
            (seconds % 3600) / 60,
            seconds % 60,
        )
    }

    fun formatPace(secondsPerKilometer: Double, unit: DistanceUnit): String {
        if (secondsPerKilometer <= 0.0) return "--:--"
        val secondsPerUnit = (secondsPerKilometer * unit.kilometers).roundToLong()
        return String.format(
            Locale.US,
            "%02d:%02d",
            secondsPerUnit / 60,
            secondsPerUnit % 60,
        )
    }

    fun splitRows(result: PaceResult, unit: DistanceUnit, limit: Int = 50): List<Pair<Double, String>> {
        val totalDistance = result.distanceKilometers / unit.kilometers
        val wholeUnits = floor(totalDistance).toInt().coerceAtMost(limit)
        val rows = (1..wholeUnits).map { distance ->
            distance.toDouble() to formatDuration(
                (distance * result.paceSecondsPerKilometer * unit.kilometers).roundToLong(),
            )
        }
        return if (wholeUnits == 0) {
            listOf(totalDistance to formatDuration(result.durationSeconds))
        } else {
            rows
        }
    }

    fun splitCount(result: PaceResult, unit: DistanceUnit, limit: Int = 50): Int {
        val totalDistance = result.distanceKilometers / unit.kilometers
        return ceil(totalDistance).toInt().coerceIn(1, limit)
    }
}
