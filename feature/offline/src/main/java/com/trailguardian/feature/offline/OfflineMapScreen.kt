package com.trailguardian.feature.offline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.geo.OfflinePlanner
import com.trailguardian.core.model.GeoPoint
import com.trailguardian.core.model.MapSources
import com.trailguardian.core.model.OfflineRegion
import com.trailguardian.core.model.OfflineRegionInput
import com.trailguardian.core.model.OfflineStatus
import com.trailguardian.core.ui.Badge
import com.trailguardian.core.ui.SectionLabel
import com.trailguardian.core.ui.TrailColors
import com.trailguardian.core.ui.TrailConfirmDialog

data class OfflineEntryRequest(
    val key: Long,
    val startPicker: Boolean,
    val points: List<GeoPoint> = emptyList(),
    val initialSelection: List<GeoPoint> = emptyList(),
    val initialName: String = "",
    val initialZooms: List<Int>? = null,
    val initialIncludeContour: Boolean = false,
    val replaceMapId: String? = null,
    val workspaceId: String? = null,
    val suggestedName: String? = null,
    val sourceId: String = MapSources.ESRI_SATELLITE.id,
)

private enum class OfflineMode { LIST, SELECT, SETUP }

@Composable
fun OfflineMapScreen(
    request: OfflineEntryRequest?,
    regions: List<OfflineRegion>,
    tileBaseUrl: String,
    onBack: () -> Unit,
    onAddRegion: (OfflineRegionInput) -> Unit,
    onDeleteRegion: (OfflineRegion) -> Unit,
    onClearAll: () -> Unit,
) {
    val source = MapSources.get(request?.sourceId ?: MapSources.ESRI_SATELLITE.id)
    var mode by remember { mutableStateOf(OfflineMode.LIST) }
    var selection by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var zoneName by remember { mutableStateOf("") }
    var selectedZooms by remember { mutableStateOf(OfflinePlanner.DEFAULT_ZOOMS) }
    var includeContour by remember { mutableStateOf(false) }
    var replaceMapId by remember { mutableStateOf<String?>(null) }
    var workspaceId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<OfflineRegion?>(null) }
    var pendingClear by remember { mutableStateOf(false) }

    LaunchedEffect(request?.key) {
        val current = request ?: return@LaunchedEffect
        selection = current.initialSelection
        zoneName = current.initialName
        selectedZooms = current.initialZooms?.takeIf { it.isNotEmpty() }
            ?: OfflinePlanner.DEFAULT_ZOOMS
        includeContour = current.initialIncludeContour
        replaceMapId = current.replaceMapId
        workspaceId = current.workspaceId
        mode = if (current.startPicker) OfflineMode.SELECT else OfflineMode.LIST
    }

    val currentSelection by rememberUpdatedState(selection)
    val bounds = remember(selection) { GeoUtils.boundsFromPoints(selection) }
    val plan = remember(bounds, selectedZooms, includeContour, source) {
        OfflinePlanner.createOfflinePlan(bounds, selectedZooms, source, includeContour)
    }
    val downloading = regions.any { it.status == OfflineStatus.DOWNLOADING }

    when (mode) {
        OfflineMode.SELECT -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TrailColors.Background),
            ) {
                SelectionHeader(
                    title = "选择下载区域",
                    onBack = { mode = OfflineMode.LIST },
                )
                Text(
                    "点击地图，确定区域（至少选择 3 个点）",
                    color = TrailColors.TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    OfflineSelectionMap(
                        modifier = Modifier.fillMaxSize(),
                        tileBaseUrl = tileBaseUrl,
                        focusPoints = request?.points.orEmpty(),
                        selection = selection,
                        onPick = { point -> selection = currentSelection + point },
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .background(TrailColors.Panel.copy(alpha = 0.88f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "已选 ${selection.size} 个点",
                            color = TrailColors.TextSecondary,
                            fontSize = 10.sp,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(TrailColors.Panel),
                ) {
                    TextButton(
                        onClick = { selection = selection.dropLast(1) },
                        enabled = selection.isNotEmpty(),
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    ) {
                        Text("撤销", color = TrailColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = {
                            if (selection.size < 3) return@TextButton
                            if (zoneName.isBlank()) {
                                zoneName = GeoUtils.defaultOfflineName(request?.suggestedName)
                            }
                            mode = OfflineMode.SETUP
                        },
                        enabled = selection.size >= 3,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    ) {
                        Text("下一步", color = TrailColors.EmeraldBright, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = { selection = emptyList() },
                        enabled = selection.isNotEmpty(),
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    ) {
                        Text("重置", color = TrailColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        OfflineMode.SETUP -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TrailColors.Background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton { mode = OfflineMode.SELECT }
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "选择地图层级",
                        color = TrailColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            if (zoneName.isBlank()) return@Button
                            if (selectedZooms.isEmpty()) return@Button
                            if (plan.tileCount > OfflinePlanner.MAX_OFFLINE_TILES || plan.tileCount == 0) {
                                return@Button
                            }
                            onAddRegion(
                                OfflineRegionInput(
                                    name = zoneName.trim(),
                                    workspaceId = workspaceId,
                                    sourceId = source.id,
                                    bounds = bounds
                                        ?: com.trailguardian.core.model.OfflineBounds(0.0, 0.0, 0.0, 0.0),
                                    points = selection,
                                    zooms = selectedZooms,
                                    includeContour = includeContour,
                                    tileCount = plan.tileCount,
                                    estimatedBytes = plan.estimatedBytes,
                                    replaceMapId = replaceMapId,
                                ),
                            )
                            mode = OfflineMode.LIST
                            selection = emptyList()
                            zoneName = ""
                            replaceMapId = null
                        },
                        enabled = plan.tileCount in 1..OfflinePlanner.MAX_OFFLINE_TILES &&
                            selectedZooms.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TrailColors.Emerald,
                            disabledContainerColor = TrailColors.Border,
                        ),
                    ) {
                        Text("确定", fontSize = 12.sp)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrailColors.Panel, RoundedCornerShape(16.dp))
                        .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text("名称", color = TrailColors.TextMuted, fontSize = 11.sp)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = zoneName,
                        onValueChange = { zoneName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TrailColors.Emerald,
                            unfocusedBorderColor = TrailColors.Border,
                            focusedTextColor = TrailColors.TextPrimary,
                            unfocusedTextColor = TrailColors.TextPrimary,
                            cursorColor = TrailColors.Emerald,
                        ),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${source.label} · 区域共 ${plan.tileCount} 张瓦片 · 约 ${GeoUtils.formatBytes(plan.estimatedBytes)}",
                        color = TrailColors.TextMuted,
                        fontSize = 10.sp,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrailColors.Panel, RoundedCornerShape(16.dp))
                        .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("选择层级", color = TrailColors.TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${selectedZooms.size} 级 · ${plan.tileCount} 瓦片 · ${GeoUtils.formatBytes(plan.estimatedBytes)}",
                            color = TrailColors.EmeraldBright,
                            fontSize = 10.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrailColors.Background.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("1", color = TrailColors.TextMuted, fontSize = 10.sp)
                            Spacer(Modifier.weight(1f))
                            Text("18", color = TrailColors.TextMuted, fontSize = 10.sp)
                        }
                        OfflinePlanner.ZOOM_LEVELS.chunked(5).forEach { rowZooms ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                rowZooms.forEach { zoom ->
                                    val checked = selectedZooms.contains(zoom)
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                selectedZooms = if (checked) {
                                                    selectedZooms - zoom
                                                } else {
                                                    (selectedZooms + zoom).sorted()
                                                }
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .background(
                                                    if (checked) Color(0xFF22C55E) else Color.Transparent,
                                                    RoundedCornerShape(50),
                                                )
                                                .border(
                                                    1.dp,
                                                    if (checked) Color(0xFF22C55E) else TrailColors.TextMuted,
                                                    RoundedCornerShape(50),
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (checked) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(10.dp),
                                                )
                                            }
                                        }
                                        Spacer(Modifier.size(5.dp))
                                        Text(zoom.toString(), color = TrailColors.TextPrimary, fontSize = 12.sp)
                                    }
                                }
                                repeat(5 - rowZooms.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrailColors.Panel, RoundedCornerShape(16.dp))
                        .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text("更多", color = TrailColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TrailColors.Background.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .clickable { includeContour = !includeContour }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("同时下载等高线叠加数据", color = TrailColors.TextPrimary, fontSize = 12.sp)
                            Text("高程瓦片仅支持 1 - 15 级，体积会增加", color = TrailColors.TextMuted, fontSize = 10.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(
                                    if (includeContour) TrailColors.Emerald else Color.Transparent,
                                    RoundedCornerShape(50),
                                )
                                .border(
                                    1.dp,
                                    if (includeContour) TrailColors.Emerald else TrailColors.TextMuted,
                                    RoundedCornerShape(50),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (includeContour) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }

                if (plan.tileCount > OfflinePlanner.MAX_OFFLINE_TILES) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF4C0519).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF881337), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            "当前选区包含 ${plan.tileCount} 张瓦片，超过单次 ${OfflinePlanner.MAX_OFFLINE_TILES} 张上限。请缩小选区或减少下载层级。",
                            color = Color(0xFFFDA4AF),
                            fontSize = 11.sp,
                        )
                    }
                }

                Text(
                    "每张瓦片按约 ${GeoUtils.formatBytes(OfflinePlanner.APPROX_TILE_BYTES)} 估算。离线包下载完成后，断网仍可显示对应区域与层级。",
                    color = TrailColors.TextMuted,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        OfflineMode.LIST -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TrailColors.Background)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BackButton(onClick = onBack)
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        SectionLabel("独立管理")
                        Text("离线地图", color = TrailColors.EmeraldBright, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = {
                            selection = emptyList()
                            zoneName = ""
                            selectedZooms = OfflinePlanner.DEFAULT_ZOOMS
                            includeContour = false
                            replaceMapId = null
                            mode = OfflineMode.SELECT
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
                    ) {
                        Text("添加", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TrailColors.Panel.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = null, tint = TrailColors.EmeraldBright, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        "已保存 ${regions.size} 个离线区域。地图圈选后可选择 1 - 18 级并独立下载。",
                        color = TrailColors.TextSecondary,
                        fontSize = 11.sp,
                    )
                }

                if (regions.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, tint = TrailColors.TextMuted, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("暂无离线地图", color = TrailColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                        Text(
                            "点击“添加”，在地图上圈选需要离线使用的区域。",
                            color = TrailColors.TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    regions.forEach { region ->
                        RegionRow(
                            region = region,
                            onDelete = { pendingDelete = region },
                        )
                    }
                    TextButton(
                        onClick = { pendingClear = true },
                        enabled = !downloading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF4C0519).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF881337), RoundedCornerShape(12.dp))
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            if (downloading) "下载进行中，完成后可清空" else "清空全部离线地图",
                            color = if (downloading) TrailColors.TextMuted else Color(0xFFFDA4AF),
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        TrailConfirmDialog(
            title = "删除离线地图",
            text = "确认删除“${toDelete.name}”及其已缓存瓦片？",
            confirmLabel = "删除",
            dangerous = true,
            onConfirm = {
                onDeleteRegion(toDelete)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
    if (pendingClear) {
        TrailConfirmDialog(
            title = "清空全部离线地图",
            text = "所有离线地图记录与瓦片缓存都会被删除，此操作不可撤销。",
            confirmLabel = "全部清空",
            dangerous = true,
            onConfirm = {
                onClearAll()
                pendingClear = false
            },
            onDismiss = { pendingClear = false },
        )
    }
}

@Composable
private fun SelectionHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(TrailColors.Panel)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        Spacer(Modifier.weight(1f))
        Text(title, color = TrailColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.size(40.dp))
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(TrailColors.PanelElevated, RoundedCornerShape(50)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = TrailColors.TextSecondary)
        }
    }
}

@Composable
private fun RegionRow(region: OfflineRegion, onDelete: () -> Unit) {
    val downloading = region.status == OfflineStatus.DOWNLOADING
    val source = MapSources.getById(region.sourceId)
    val size = GeoUtils.formatBytes(region.sizeBytes.takeIf { it > 0 } ?: region.estimatedBytes)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrailColors.Panel, RoundedCornerShape(14.dp))
            .border(1.dp, TrailColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(region.name, color = TrailColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                    if (downloading) {
                        Spacer(Modifier.size(6.dp))
                        Text("(${region.progress}%)", color = TrailColors.Amber, fontSize = 10.sp)
                    }
                }
                Text("下载层级：${region.zooms.joinToString(",")}", color = TrailColors.TextMuted, fontSize = 10.sp)
                Text(
                    "${source.label}${if (region.includeContour) " + 等高线" else ""}，${region.downloadedTiles}/${region.tileCount} 瓦片，$size",
                    color = TrailColors.EmeraldBright,
                    fontSize = 10.sp,
                )
            }
            if (!downloading) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除离线地图", tint = TrailColors.Rose, modifier = Modifier.size(16.dp))
                }
            } else {
                Badge("${region.progress}%", TrailColors.Amber)
            }
        }
        if (downloading) {
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(TrailColors.Border, RoundedCornerShape(50)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(region.progress.coerceIn(0, 100) / 100f)
                        .height(6.dp)
                        .background(TrailColors.EmeraldBright, RoundedCornerShape(50)),
                )
            }
        }
        if (region.status == OfflineStatus.ERROR) {
            Spacer(Modifier.height(6.dp))
            Text(
                "部分瓦片下载失败，可重新下载",
                color = TrailColors.Amber,
                fontSize = 10.sp,
            )
        }
    }
}
