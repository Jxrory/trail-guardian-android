package com.trailguardian.feature.map

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.model.ContourStatus
import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.LivePosition
import com.trailguardian.core.model.MapCamera
import com.trailguardian.core.model.MapFocusRequest
import com.trailguardian.core.model.Poi
import com.trailguardian.core.model.PoiType
import com.trailguardian.core.ui.Dot
import com.trailguardian.core.ui.TrailColors
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import kotlin.math.roundToInt

private val contourStatusLabels = mapOf(
    ContourStatus.IDLE to "等高线已关闭",
    ContourStatus.LOADING to "正在生成等高线…",
    ContourStatus.READY to "等高线已显示",
    ContourStatus.ERROR to "地形数据加载失败，请检查网络",
)

@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    tileBaseUrl: String,
    targetPoints: List<GeoPoint>,
    recordedPoints: List<GeoPoint>,
    pois: List<Poi>,
    position: LivePosition?,
    currentElevation: Int?,
    focusRequest: MapFocusRequest?,
    lastFocusKey: String?,
    initialCamera: MapCamera,
    contourEnabled: Boolean,
    contourOpacity: Float,
    contourStatus: ContourStatus,
    onFocusApplied: (String) -> Unit,
    onToggleContour: (Boolean) -> Unit,
    onContourOpacityChange: (Float) -> Unit,
    onLocateUnavailable: () -> Unit,
    onMapNotReady: () -> Unit,
    onRequestPoi: (GeoPoint) -> Unit,
    onCameraChanged: (MapCamera) -> Unit,
    onPoiSelected: (Poi) -> Unit,
    onControllerReady: (TrailMapController?) -> Unit,
    onMapReady: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    var heading by remember { mutableStateOf<Float?>(null) }
    val compass = remember {
        CompassSensor(context) { value -> heading = value }
    }
    LaunchedEffect(configuration) {
        val rotation = runCatching {
            context.getSystemService(WindowManager::class.java).defaultDisplay.rotation
        }.getOrDefault(0)
        compass.setDisplayRotation(rotation)
    }
    DisposableEffect(compass) {
        compass.start()
        onDispose { compass.stop() }
    }
    val currentPois by rememberUpdatedState(pois)
    val currentPoiSelected by rememberUpdatedState(onPoiSelected)
    val currentCameraChanged by rememberUpdatedState(onCameraChanged)
    val currentControllerReady by rememberUpdatedState(onControllerReady)
    val currentMapReady by rememberUpdatedState(onMapReady)
    val currentOnFocusApplied by rememberUpdatedState(onFocusApplied)
    val currentFocusAppliedKey by rememberUpdatedState(lastFocusKey)
    var mapReady by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var compassPopup by remember { mutableStateOf(false) }
    var selectedPoi by remember { mutableStateOf<Poi?>(null) }
    val mapView = remember {
        MapView(
            context,
            MapLibreMapOptions()
                .minZoomPreference(3.0)
                .maxZoomPreference(19.0)
                .attributionEnabled(true)
                .logoEnabled(false)
                .rotateGesturesEnabled(true)
                .tiltGesturesEnabled(false),
        ).apply { onCreate(null) }
    }
    val controller = remember(mapView) {
        TrailMapController(
            mapView = mapView,
            tileBaseUrl = tileBaseUrl,
            initialCamera = initialCamera,
            contourEnabled = contourEnabled,
            contourOpacity = contourOpacity,
            onPoiClick = { id ->
                currentPois.firstOrNull { it.id == id }?.let {
                    selectedPoi = it
                    currentPoiSelected(it)
                }
            },
            onCameraChanged = { camera -> currentCameraChanged(camera) },
            onReady = {
                mapReady = true
                currentMapReady()
            },
        )
    }
    LaunchedEffect(controller) {
        currentControllerReady(controller)
    }

    LaunchedEffect(mapReady, focusRequest, lastFocusKey) {
        val request = focusRequest ?: return@LaunchedEffect
        if (!mapReady || request.points.isEmpty()) return@LaunchedEffect
        if (request.key == currentFocusAppliedKey) return@LaunchedEffect
        controller.focusPoints(request.points, maxZoom = 16.0, paddingPx = 96)
        currentOnFocusApplied(request.key)
    }

    LaunchedEffect(mapReady, targetPoints, recordedPoints, pois, position) {
        controller.applyData(
            MapData(
                targetPoints = targetPoints,
                recordedPoints = recordedPoints,
                pois = pois,
                position = position,
            ),
        )
    }

    LaunchedEffect(contourEnabled, contourOpacity) {
        controller.setContour(contourEnabled, contourOpacity)
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
            currentControllerReady(null)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val panelMaxHeight = maxHeight * 0.65f
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
        )

        val userScreen = controller.userScreen.collectAsState().value
        if (userScreen != null && position != null) {
            Box(
                modifier = Modifier
                    .offset {
                        val half = 32.dp.roundToPx()
                        IntOffset(
                            (userScreen.x - half).roundToInt(),
                            (userScreen.y - half).roundToInt(),
                        )
                    }
                    .size(64.dp),
            ) {
                UserCompass(
                    heading = heading,
                    hasPosition = true,
                    modifier = Modifier.clickable { compassPopup = !compassPopup },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .width(208.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(TrailColors.Panel.copy(alpha = 0.92f))
                    .border(1.dp, TrailColors.Border, RoundedCornerShape(12.dp))
                    .clickable { infoOpen = !infoOpen }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.SatelliteAlt,
                    contentDescription = "地图信息",
                    tint = TrailColors.EmeraldBright,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Esri 卫星",
                    color = TrailColors.TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (infoOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = TrailColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (infoOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = panelMaxHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TrailColors.Panel.copy(alpha = 0.95f))
                        .border(1.dp, TrailColors.Border, RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "地图信息",
                        color = TrailColors.EmeraldBright,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "等高线叠加",
                            color = TrailColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = contourEnabled,
                            onCheckedChange = onToggleContour,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TrailColors.Emerald,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = TrailColors.Border,
                            ),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("强度", color = TrailColors.TextMuted, fontSize = 10.sp)
                        Slider(
                            value = contourOpacity,
                            onValueChange = onContourOpacityChange,
                            valueRange = 0.1f..1f,
                            steps = 17,
                            enabled = contourEnabled,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = TrailColors.Emerald,
                                activeTrackColor = TrailColors.Emerald.copy(alpha = 0.7f),
                                inactiveTrackColor = TrailColors.Border,
                            ),
                        )
                        Text(
                            "${(contourOpacity * 100).roundToInt()}%",
                            color = TrailColors.TextMuted,
                            fontSize = 9.sp,
                            modifier = Modifier.width(28.dp),
                        )
                    }
                    val effectiveStatus = when {
                        !contourEnabled -> ContourStatus.IDLE
                        contourStatus == ContourStatus.IDLE -> ContourStatus.LOADING
                        else -> contourStatus
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (effectiveStatus) {
                                    ContourStatus.READY -> Color(0xFF064E3B).copy(alpha = 0.6f)
                                    ContourStatus.ERROR -> Color(0xFF4C0519).copy(alpha = 0.6f)
                                    else -> TrailColors.PanelElevated.copy(alpha = 0.8f)
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Dot(
                            when (effectiveStatus) {
                                ContourStatus.READY -> TrailColors.EmeraldBright
                                ContourStatus.ERROR -> TrailColors.Rose
                                else -> TrailColors.TextMuted
                            },
                            size = 6,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            contourStatusLabels[effectiveStatus] ?: "",
                            color = when (effectiveStatus) {
                                ContourStatus.READY -> TrailColors.EmeraldBright
                                ContourStatus.ERROR -> Color(0xFFFDA4AF)
                                else -> TrailColors.TextMuted
                            },
                            fontSize = 10.sp,
                        )
                    }
                    Text(
                        "AWS Terrain 地形数据 · 本地生成 · 50m 间隔",
                        color = TrailColors.TextMuted,
                        fontSize = 9.sp,
                    )
                    if (effectiveStatus == ContourStatus.READY) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LegendLine(TrailColors.Amber)
                            Text("整百米", color = TrailColors.TextMuted, fontSize = 9.sp)
                            Spacer(Modifier.width(8.dp))
                            LegendLine(Color(0xFFCBD5E1))
                            Text("五十米", color = TrailColors.TextMuted, fontSize = 9.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .width(16.dp)
                                    .height(2.dp)
                                    .background(TrailColors.Sky),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("目标运动路径", color = TrailColors.TextMuted, fontSize = 9.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .width(16.dp)
                                    .height(3.dp)
                                    .background(TrailColors.Emerald),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("已走路径", color = TrailColors.TextMuted, fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            MapIconButton(
                onClick = {
                    if (!controller.centerToUser(16.0)) onLocateUnavailable()
                },
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = "定位当前",
                    tint = TrailColors.EmeraldBright,
                )
            }
            MapIconButton(
                onClick = {
                    if (!controller.isReady) {
                        onMapNotReady()
                        return@MapIconButton
                    }
                    val anchor = position?.let { GeoPoint(it.lat, it.lng) }
                        ?: controller.cameraCenter()
                    if (anchor == null) onMapNotReady() else onRequestPoi(anchor)
                },
            ) {
                Icon(
                    Icons.Filled.AddLocationAlt,
                    contentDescription = "打点营地/水源",
                    tint = TrailColors.Amber,
                )
            }
        }

        if (currentElevation != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TrailColors.Panel.copy(alpha = 0.85f))
                    .border(1.dp, TrailColors.Border, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    "海拔 ${currentElevation} m",
                    color = TrailColors.TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }

        val poi = selectedPoi
        if (poi != null) {
            val type = poi.type
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TrailColors.Panel.copy(alpha = 0.96f))
                    .border(1.dp, TrailColors.Border, RoundedCornerShape(14.dp))
                    .clickable { selectedPoi = null }
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(parseHex(type.colorHex), RoundedCornerShape(50)),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(poi.name, color = TrailColors.TextPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${type.label}${if (poi.desc.isNotBlank()) " · ${poi.desc}" else ""}",
                    color = TrailColors.TextMuted,
                    fontSize = 11.sp,
                )
            }
        }

        if (compassPopup && position != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(TrailColors.Panel.copy(alpha = 0.96f))
                    .border(1.dp, TrailColors.Border, RoundedCornerShape(14.dp))
                    .clickable { compassPopup = false }
                    .padding(12.dp),
            ) {
                Text("我的位置", color = TrailColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text(
                    "朝向 ${GeoUtils.describeHeading(heading)}",
                    color = TrailColors.TextSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    "精度约 ${(position.accuracy ?: 0f).roundToInt()} m" +
                        if (currentElevation != null) " · 海拔 $currentElevation m" else "",
                    color = TrailColors.TextSecondary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun LegendLine(color: Color) {
    Canvas(modifier = Modifier.width(16.dp).height(2.dp)) {
        drawLine(
            color = color,
            start = Offset(0f, size.height / 2),
            end = Offset(size.width, size.height / 2),
            strokeWidth = size.height,
        )
    }
}

@Composable
private fun MapIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(TrailColors.Panel.copy(alpha = 0.92f))
            .border(1.dp, TrailColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun UserCompass(
    heading: Float?,
    hasPosition: Boolean,
    modifier: Modifier = Modifier,
) {
    val hasHeading = heading != null && !heading.isNaN()
    val transition = rememberInfiniteTransition(label = "compassPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse",
    )
    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ringRadius = 22.dp.toPx()
            drawCircle(
                color = TrailColors.Rose.copy(alpha = 0.55f * (1f - pulse)),
                radius = ringRadius * (0.6f + 0.85f * pulse),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = Color(0xFF0F172A).copy(alpha = 0.35f),
                radius = ringRadius,
            )
            drawCircle(
                color = TrailColors.TextSecondary.copy(alpha = 0.58f),
                radius = ringRadius,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Text(
            "N",
            color = TrailColors.TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-9).dp),
        )
        Canvas(
            modifier = Modifier
                .size(52.dp)
                .rotate(if (hasHeading) heading!! else 0f),
        ) {
            val path = Path()
            val center = Offset(size.width / 2f, size.height / 2f)
            val halfWidth = 4.dp.toPx()
            val tipY = center.y - 20.dp.toPx()
            val notchY = center.y - 4.6.dp.toPx()
            path.moveTo(center.x, tipY)
            path.lineTo(center.x + halfWidth, center.y)
            path.lineTo(center.x, notchY)
            path.lineTo(center.x - halfWidth, center.y)
            path.close()
            drawPath(
                path = path,
                color = TrailColors.Rose.copy(
                    alpha = if (hasHeading && hasPosition) 0.95f else 0.35f,
                ),
            )
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .border(2.dp, Color.White, RoundedCornerShape(50))
                .background(TrailColors.Rose, RoundedCornerShape(50)),
        )
    }
}

private fun parseHex(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(TrailColors.Emerald)
