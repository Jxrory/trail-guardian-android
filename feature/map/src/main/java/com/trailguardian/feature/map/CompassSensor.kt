package com.trailguardian.feature.map

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import com.trailguardian.core.geo.GeoUtils

class CompassSensor(
    context: Context,
    private val onHeading: (Float?) -> Unit,
) : SensorEventListener {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val rotationVector: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private var lastUpdate = 0L
    private var smoothed: Float? = null
    private var displayRotation = 0
    private var registered = false

    fun setDisplayRotation(rotation: Int) {
        displayRotation = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    fun start() {
        val sensor = rotationVector
        if (sensor == null) {
            onHeading(null)
            return
        }
        if (registered) return
        registered = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        if (!registered) onHeading(null)
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = System.currentTimeMillis()
        if (now - lastUpdate < 100) return
        lastUpdate = now
        val heading = readHeading(event.values) ?: return
        val previous = smoothed
        val next = if (previous == null) {
            heading
        } else {
            GeoUtils.normalizeHeading(
                previous + GeoUtils.shortestHeadingDelta(previous, heading) * 0.45f,
            )
        }
        smoothed = next
        onHeading(next)
    }

    private fun readHeading(values: FloatArray): Float? {
        val rotation = FloatArray(9)
        val remapped = FloatArray(9)
        try {
            SensorManager.getRotationMatrixFromVector(rotation, values)
            when (displayRotation) {
                90 -> SensorManager.remapCoordinateSystem(
                    rotation,
                    SensorManager.AXIS_Y,
                    SensorManager.AXIS_MINUS_X,
                    remapped,
                )
                180 -> SensorManager.remapCoordinateSystem(
                    rotation,
                    SensorManager.AXIS_MINUS_X,
                    SensorManager.AXIS_MINUS_Y,
                    remapped,
                )
                270 -> SensorManager.remapCoordinateSystem(
                    rotation,
                    SensorManager.AXIS_MINUS_Y,
                    SensorManager.AXIS_X,
                    remapped,
                )
                else -> rotation.copyInto(remapped)
            }
            val orientation = FloatArray(3)
            SensorManager.getOrientation(remapped, orientation)
            return GeoUtils.normalizeHeading(Math.toDegrees(orientation[0].toDouble()).toFloat())
        } catch (_: Exception) {
            return null
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
