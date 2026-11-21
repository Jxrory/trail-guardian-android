package com.trailguardian.service.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.trailguardian.core.model.GpsState
import com.trailguardian.core.model.GpsStatus

class LocationSource(
    private val context: Context,
    private val onLocation: (Location) -> Unit,
    private val onGpsState: (GpsState) -> Unit,
) {
    private var fusedCallback: LocationCallback? = null
    private var fusedClient: com.google.android.gms.location.FusedLocationProviderClient? = null
    private var managerListener: LocationListener? = null
    private var started = false

    fun start() {
        if (started) return
        started = true
        if (!hasForegroundPermission()) {
            started = false
            onGpsState(
                GpsState(
                    status = GpsStatus.DENIED,
                    error = "定位权限被拒绝，请在系统设置中允许定位",
                ),
            )
            return
        }
        if (hasPlayServices()) {
            startFused()
        } else {
            startLocationManager()
        }
    }

    fun stop() {
        started = false
        fusedCallback?.let { callback ->
            runCatching { fusedClient?.removeLocationUpdates(callback) }
        }
        fusedCallback = null
        fusedClient = null
        managerListener?.let { listener ->
            runCatching {
                context.getSystemService(LocationManager::class.java)
                    .removeUpdates(listener)
            }
        }
        managerListener = null
    }

    private fun startFused() {
        val client = LocationServices.getFusedLocationProviderClient(context)
        fusedClient = client
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let(onLocation)
            }
        }
        fusedCallback = callback
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            INTERVAL_MS,
        )
            .setMinUpdateIntervalMillis(MIN_INTERVAL_MS)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (error: SecurityException) {
            onGpsState(
                GpsState(
                    status = GpsStatus.DENIED,
                    error = error.message ?: "定位权限被拒绝",
                ),
            )
        } catch (error: Exception) {
            onGpsState(
                GpsState(
                    status = GpsStatus.ERROR,
                    error = error.message ?: "暂时无法获取 GPS 定位",
                ),
            )
        }
    }

    private fun startLocationManager() {
        val manager = context.getSystemService(LocationManager::class.java)
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                onLocation(location)
            }

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) {
                onGpsState(
                    GpsState(
                        status = GpsStatus.ERROR,
                        error = "GPS 定位已关闭",
                    ),
                )
            }
        }
        managerListener = listener
        try {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    INTERVAL_MS,
                    1f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                manager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    INTERVAL_MS,
                    1f,
                    listener,
                    Looper.getMainLooper(),
                )
            }
        } catch (error: SecurityException) {
            onGpsState(
                GpsState(
                    status = GpsStatus.DENIED,
                    error = error.message ?: "定位权限被拒绝",
                ),
            )
        } catch (error: Exception) {
            onGpsState(
                GpsState(
                    status = GpsStatus.ERROR,
                    error = error.message ?: "暂时无法获取 GPS 定位",
                ),
            )
        }
    }

    private fun hasForegroundPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasPlayServices(): Boolean {
        val availability = GoogleApiAvailability.getInstance()
        return availability.isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }

    companion object {
        const val INTERVAL_MS = 1500L
        const val MIN_INTERVAL_MS = 1000L
    }
}
