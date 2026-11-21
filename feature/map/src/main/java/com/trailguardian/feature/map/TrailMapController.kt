package com.trailguardian.feature.map

import androidx.compose.ui.geometry.Offset
import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.LivePosition
import com.trailguardian.core.model.MapCamera
import com.trailguardian.core.model.Poi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.min

data class MapData(
    val targetPoints: List<GeoPoint> = emptyList(),
    val recordedPoints: List<GeoPoint> = emptyList(),
    val pois: List<Poi> = emptyList(),
    val position: LivePosition? = null,
)

class TrailMapController(
    private val mapView: MapView,
    tileBaseUrl: String,
    private val initialCamera: MapCamera,
    private var contourEnabled: Boolean,
    private var contourOpacity: Float,
    private val onPoiClick: (String) -> Unit,
    private val onCameraChanged: (MapCamera) -> Unit,
    private val onReady: () -> Unit,
) {
    private var map: MapLibreMap? = null
    private var styleReady = false
    private var pendingData: MapData? = null
    private var appliedInitialCamera = false

    private val _userScreen = MutableStateFlow<Offset?>(null)
    val userScreen: StateFlow<Offset?> = _userScreen.asStateFlow()
    private var position: LivePosition? = null

    init {
        mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.addOnCameraMoveListener { updateUserScreen() }
            mapLibreMap.addOnCameraIdleListener {
                updateUserScreen()
                reportCamera()
            }
            mapLibreMap.addOnMapClickListener { latLng ->
                val screenPoint = mapLibreMap.projection.toScreenLocation(latLng)
                val features = mapLibreMap.queryRenderedFeatures(screenPoint, "pois")
                val poiId = features.firstOrNull()?.getStringProperty("id")
                if (poiId != null) {
                    onPoiClick(poiId)
                    true
                } else {
                    false
                }
            }
            mapLibreMap.setStyle(
                Style.Builder().fromJson(
                    MapStyle.build(tileBaseUrl, contourEnabled, contourOpacity),
                ),
            ) { style ->
                styleReady = true
                style.getLayerAs<RasterLayer>("contour")?.setProperties(
                    PropertyFactory.rasterOpacity(contourOpacity),
                    PropertyFactory.visibility(
                        if (contourEnabled) Property.VISIBLE else Property.NONE,
                    ),
                )
                pendingData?.let { syncData(it) }
                if (!appliedInitialCamera) {
                    appliedInitialCamera = true
                    mapLibreMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(initialCamera.lat, initialCamera.lng),
                            initialCamera.zoom,
                        ),
                    )
                }
                reportCamera()
                updateUserScreen()
                onReady()
            }
        }
    }

    val isReady: Boolean
        get() = styleReady

    fun applyData(data: MapData) {
        pendingData = data
        position = data.position
        if (styleReady) syncData(data)
        updateUserScreen()
    }

    fun setContour(enabled: Boolean, opacity: Float) {
        contourEnabled = enabled
        contourOpacity = opacity
        if (!styleReady) return
        map?.style?.getLayerAs<RasterLayer>("contour")?.setProperties(
            PropertyFactory.rasterOpacity(opacity),
            PropertyFactory.visibility(if (enabled) Property.VISIBLE else Property.NONE),
        )
    }

    fun centerToUser(minZoom: Double = 16.0): Boolean {
        val map = map ?: return false
        val currentPosition = position ?: return false
        val targetZoom = maxOf(map.cameraPosition.zoom, minZoom)
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(currentPosition.lat, currentPosition.lng),
                targetZoom,
            ),
            500,
        )
        return true
    }

    fun centerToPoint(lat: Double, lng: Double, zoom: Double = 17.0) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), zoom), 500)
    }

    fun cameraCenter(): GeoPoint? {
        val map = map ?: return null
        val target = map.cameraPosition.target ?: return null
        return GeoPoint(target.latitude, target.longitude)
    }

    fun focusPoints(points: List<GeoPoint>, maxZoom: Double = 16.0, paddingPx: Int = 96) {
        val map = map ?: return
        if (points.isEmpty()) return
        if (points.size == 1) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(points.first().lat, points.first().lng),
                    min(maxZoom, 16.0),
                ),
                500,
            )
            return
        }
        val builder = LatLngBounds.Builder()
        points.forEach { point -> builder.include(LatLng(point.lat, point.lng)) }
        val bounds = builder.build()
        val padding = intArrayOf(paddingPx, paddingPx, paddingPx, paddingPx)
        val camera = map.getCameraForLatLngBounds(bounds, padding)
        val target = camera?.target ?: return
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(target, min(camera.zoom, maxZoom)),
            500,
        )
    }

    fun destroy() {
        map = null
        _userScreen.value = null
    }

    private fun syncData(data: MapData) {
        val map = map ?: return
        setGeoJson("target", if (data.targetPoints.size > 1) MapStyle.lineGeoJson(data.targetPoints) else MapStyle.lineGeoJson(emptyList()))
        setGeoJson("targetEndpoints", MapStyle.endpointsGeoJson(data.targetPoints, "#38BDF8"))
        setGeoJson("recorded", if (data.recordedPoints.size > 1) MapStyle.lineGeoJson(data.recordedPoints) else MapStyle.lineGeoJson(emptyList()))
        setGeoJson("recordedEndpoints", MapStyle.endpointsGeoJson(data.recordedPoints, "#10B981"))
        setGeoJson("pois", MapStyle.poiGeoJson(data.pois))
        updateUserScreen()
    }

    private fun setGeoJson(sourceId: String, json: String) {
        map?.style?.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(json)
    }

    private fun updateUserScreen() {
        val map = map ?: run {
            _userScreen.value = null
            return
        }
        val currentPosition = position
        if (currentPosition == null) {
            _userScreen.value = null
            return
        }
        val screen = map.projection.toScreenLocation(
            LatLng(currentPosition.lat, currentPosition.lng),
        )
        _userScreen.value = Offset(screen.x, screen.y)
    }

    private fun reportCamera() {
        val map = map ?: return
        val target = map.cameraPosition.target ?: return
        onCameraChanged(
            MapCamera(
                lat = target.latitude,
                lng = target.longitude,
                zoom = map.cameraPosition.zoom,
            ),
        )
    }
}
