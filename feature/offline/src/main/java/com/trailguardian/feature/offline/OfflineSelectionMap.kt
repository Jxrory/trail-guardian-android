package com.trailguardian.feature.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.model.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import kotlin.math.min
import kotlin.math.roundToInt

private const val DEFAULT_LAT = 30.245
private const val DEFAULT_LNG = 120.125

@Composable
fun OfflineSelectionMap(
    modifier: Modifier = Modifier,
    tileBaseUrl: String,
    focusPoints: List<GeoPoint>,
    selection: List<GeoPoint>,
    onPick: (GeoPoint) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapReady = remember { mutableStateOf(false) }
    val markerState = remember { mutableStateOf<List<Offset>>(emptyList()) }
    val mapView = remember {
        MapView(
            context,
            MapLibreMapOptions()
                .minZoomPreference(3.0)
                .maxZoomPreference(19.0)
                .attributionEnabled(true)
                .logoEnabled(false)
                .rotateGesturesEnabled(false)
                .tiltGesturesEnabled(false),
        ).apply { onCreate(null) }
    }
    val controller = remember(mapView) {
        OfflineSelectionController(
            mapView = mapView,
            tileBaseUrl = tileBaseUrl,
            focusPoints = focusPoints,
            onPick = onPick,
            onMarkerOffsets = { offsets -> markerState.value = offsets },
            onReady = { mapReady.value = true },
        )
    }

    LaunchedEffect(selection) { controller.applySelection(selection) }
    LaunchedEffect(mapReady.value, selection) {
        if (mapReady.value) controller.applySelection(selection)
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapView.onDestroy() }
            controller.destroy()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        val offsets = markerState.value
        selection.forEachIndexed { index, _ ->
            val offset = offsets.getOrNull(index) ?: return@forEachIndexed
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (offset.x - 14).roundToInt(),
                            (offset.y - 14).roundToInt(),
                        )
                    }
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEF4444))
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${index + 1}",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

class OfflineSelectionController(
    private val mapView: MapView,
    private val tileBaseUrl: String,
    private val focusPoints: List<GeoPoint>,
    private val onPick: (GeoPoint) -> Unit,
    private val onMarkerOffsets: (List<Offset>) -> Unit,
    private val onReady: () -> Unit,
) {
    private var map: MapLibreMap? = null
    private var ready = false
    private var selection: List<GeoPoint> = emptyList()
    private var appliedFocus = false
    private val _markerOffsets = MutableStateFlow<List<Offset>>(emptyList())
    val markerOffsets: StateFlow<List<Offset>> = _markerOffsets.asStateFlow()

    init {
        mapView.getMapAsync { mapLibreMap ->
            map = mapLibreMap
            mapLibreMap.addOnMapClickListener { latLng ->
                onPick(GeoPoint(latLng.latitude, latLng.longitude))
                true
            }
            mapLibreMap.addOnCameraMoveListener { updateMarkerOffsets() }
            mapLibreMap.addOnCameraIdleListener { updateMarkerOffsets() }
            mapLibreMap.setStyle(
                Style.Builder().fromJson(buildStyle(tileBaseUrl)),
            ) { style ->
                ready = true
                if (focusPoints.size > 1) {
                    style.getSourceAs<GeoJsonSource>("reference")
                        ?.setGeoJson(referenceLineGeoJson(focusPoints))
                }
                applySelection(selection)
                focusInitialView()
                updateMarkerOffsets()
                onReady()
            }
        }
    }

    fun applySelection(points: List<GeoPoint>) {
        selection = points
        val map = map ?: return
        val style = map.style ?: return
        if (!ready) return
        val polygonJson = if (points.size >= 2) {
            polygonGeoJson(points)
        } else {
            emptyCollection()
        }
        val lineJson = if (points.size >= 2) lineGeoJson(points) else emptyCollection()
        val pointsJson = pointsGeoJson(points)
        style.getSourceAs<GeoJsonSource>("selection-fill")?.setGeoJson(polygonJson)
        style.getSourceAs<GeoJsonSource>("selection-line")?.setGeoJson(lineJson)
        style.getSourceAs<GeoJsonSource>("selection-points")?.setGeoJson(pointsJson)
        updateMarkerOffsets()
    }

    fun destroy() {
        map = null
    }

    private fun focusInitialView() {
        if (appliedFocus) return
        appliedFocus = true
        val map = map ?: return
        if (focusPoints.isEmpty()) {
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(DEFAULT_LAT, DEFAULT_LNG),
                    14.0,
                ),
            )
            return
        }
        if (focusPoints.size == 1) {
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(focusPoints[0].lat, focusPoints[0].lng),
                    14.0,
                ),
            )
            return
        }
        val builder = LatLngBounds.Builder()
        focusPoints.forEach { builder.include(LatLng(it.lat, it.lng)) }
        val bounds = builder.build()
        val camera = map.getCameraForLatLngBounds(bounds, intArrayOf(84, 84, 84, 84))
        val target = camera?.target ?: return
        map.moveCamera(
            CameraUpdateFactory.newLatLngZoom(target, min(camera.zoom, 14.0)),
        )
    }

    private fun updateMarkerOffsets() {
        val map = map ?: return
        val style = map.style ?: return
        if (!ready) return
        val offsets = selection.map { point ->
            val screen = map.projection.toScreenLocation(LatLng(point.lat, point.lng))
            Offset(screen.x, screen.y)
        }
        _markerOffsets.value = offsets
        onMarkerOffsets(offsets)
    }

    private fun referenceLineGeoJson(points: List<GeoPoint>): String {
        val coordinates = JSONArray()
        points.forEach { coordinates.put(JSONArray().put(it.lng).put(it.lat)) }
        val feature = JSONObject().apply {
            put("type", "Feature")
            put(
                "geometry",
                JSONObject().apply {
                    put("type", "LineString")
                    put("coordinates", coordinates)
                },
            )
        }
        return collection(listOf(feature))
    }

    private fun emptyCollection(): String = JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", JSONArray())
    }.toString()

    private fun lineGeoJson(points: List<GeoPoint>): String {
        val coordinates = JSONArray()
        points.forEach { coordinates.put(JSONArray().put(it.lng).put(it.lat)) }
        val feature = JSONObject().apply {
            put("type", "Feature")
            put(
                "geometry",
                JSONObject().apply {
                    put("type", "LineString")
                    put("coordinates", coordinates)
                },
            )
        }
        return collection(listOf(feature))
    }

    private fun polygonGeoJson(points: List<GeoPoint>): String {
        val ring = JSONArray()
        points.forEach { ring.put(JSONArray().put(it.lng).put(it.lat)) }
        val first = points.first()
        ring.put(JSONArray().put(first.lng).put(first.lat))
        val feature = JSONObject().apply {
            put("type", "Feature")
            put(
                "geometry",
                JSONObject().apply {
                    put("type", "Polygon")
                    put("coordinates", JSONArray().put(ring))
                },
            )
        }
        return collection(listOf(feature))
    }

    private fun pointsGeoJson(points: List<GeoPoint>): String {
        val features = points.map { point ->
            JSONObject().apply {
                put("type", "Feature")
                put(
                    "geometry",
                    JSONObject().apply {
                        put("type", "Point")
                        put("coordinates", JSONArray().put(point.lng).put(point.lat))
                    },
                )
            }
        }
        return collection(features)
    }

    private fun collection(features: List<JSONObject>): String = JSONObject().apply {
        put("type", "FeatureCollection")
        put("features", JSONArray().apply { features.forEach { put(it) } })
    }.toString()

    companion object {
        fun buildStyle(tileBaseUrl: String): String {
            val sources = JSONObject()
            sources.put(
                "base",
                rasterSource("$tileBaseUrl/esri/{z}/{y}/{x}", 19),
            )
            sources.put(
                "labels",
                rasterSource("$tileBaseUrl/labels/{z}/{y}/{x}", 19),
            )
            sources.put("reference", emptyGeoJson())
            sources.put("selection-fill", emptyGeoJson())
            sources.put("selection-line", emptyGeoJson())
            sources.put("selection-points", emptyGeoJson())
            val layers = JSONArray()
            layers.put(
                JSONObject().apply {
                    put("id", "base")
                    put("type", "raster")
                    put("source", "base")
                    put("paint", JSONObject().put("raster-fade-duration", 0))
                },
            )
            layers.put(
                JSONObject().apply {
                    put("id", "labels")
                    put("type", "raster")
                    put("source", "labels")
                    put("paint", JSONObject().put("raster-fade-duration", 0))
                },
            )
            layers.put(
                JSONObject().apply {
                    put("id", "reference-line")
                    put("type", "line")
                    put("source", "reference")
                    put(
                        "layout",
                        JSONObject().put("line-cap", "butt").put("line-join", "round"),
                    )
                    put(
                        "paint",
                        JSONObject()
                            .put("line-color", "#38BDF8")
                            .put("line-width", 3)
                            .put("line-opacity", 0.9)
                            .put("line-dasharray", JSONArray().put(7).put(6)),
                    )
                },
            )
            layers.put(
                JSONObject().apply {
                    put("id", "selection-fill")
                    put("type", "fill")
                    put("source", "selection-fill")
                    put(
                        "paint",
                        JSONObject()
                            .put("fill-color", "#EF4444")
                            .put("fill-opacity", 0.16),
                    )
                },
            )
            layers.put(
                JSONObject().apply {
                    put("id", "selection-line")
                    put("type", "line")
                    put("source", "selection-line")
                    put(
                        "paint",
                        JSONObject()
                            .put("line-color", "#EF4444")
                            .put("line-width", 3)
                            .put("line-dasharray", JSONArray().put(7).put(5)),
                    )
                },
            )
            layers.put(
                JSONObject().apply {
                    put("id", "selection-points")
                    put("type", "circle")
                    put("source", "selection-points")
                    put(
                        "paint",
                        JSONObject()
                            .put("circle-radius", 8)
                            .put("circle-color", "#EF4444")
                            .put("circle-stroke-width", 2)
                            .put("circle-stroke-color", "#FFFFFF"),
                    )
                },
            )
            return JSONObject().apply {
                put("version", 8)
                put("name", "TrailGuardianOffline")
                put("sources", sources)
                put("layers", layers)
            }.toString()
        }

        private fun rasterSource(tileUrl: String, maxZoom: Int): JSONObject = JSONObject().apply {
            put("type", "raster")
            put("tiles", JSONArray().put(tileUrl))
            put("tileSize", 256)
            put("minzoom", 0)
            put("maxzoom", maxZoom)
            put("attribution", "Tiles © Esri")
        }

        private fun emptyGeoJson(): JSONObject = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", JSONArray())
        }
    }
}
