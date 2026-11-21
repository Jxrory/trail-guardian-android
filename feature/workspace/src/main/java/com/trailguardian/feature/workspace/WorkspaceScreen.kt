package com.trailguardian.feature.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.model.GpsState
import com.trailguardian.core.model.GpsStatus
import com.trailguardian.core.model.OfflineRegion
import com.trailguardian.core.model.OfflineStatus
import com.trailguardian.core.model.Poi
import com.trailguardian.core.model.PoiType
import com.trailguardian.core.model.Workspace
import com.trailguardian.core.ui.Badge
import com.trailguardian.core.ui.EmptyState
import com.trailguardian.core.ui.SectionLabel
import com.trailguardian.core.ui.TrailCard
import com.trailguardian.core.ui.TrailColors
import com.trailguardian.core.ui.TrailConfirmDialog

@Composable
fun WorkspaceScreen(
    workspace: Workspace?,
    isRecording: Boolean,
    isPaused: Boolean,
    distanceMeters: Double,
    elevationGain: Double,
    currentElevation: Int?,
    elapsedSeconds: Long,
    pointCount: Int,
    gpsState: GpsState,
    offlineMap: OfflineRegion?,
    onCreateWorkspace: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenMap: () -> Unit,
    onAddPoi: () -> Unit,
    onDeletePoi: (String) -> Unit,
    onFocusPoi: (Poi) -> Unit,
    onExport: () -> Unit,
    onImportTarget: () -> Unit,
    onStart: () -> Unit,
    onPauseOrResume: () -> Unit,
    onFinish: () -> Unit,
    onOpenOffline: () -> Unit,
) {
    if (workspace == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TrailColors.Background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(TrailColors.PanelElevated, RoundedCornerShape(16.dp))
                    .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Route, contentDescription = null, tint = TrailColors.TextMuted)
            }
            Spacer(Modifier.height(16.dp))
            Text("尚未加载轨迹工作空间", color = TrailColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "目标路径、轨迹记录和标注点位都只能在轨迹工作空间中进行。",
                color = TrailColors.TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onCreateWorkspace,
                    colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("新建")
                }
                Button(
                    onClick = onOpenHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = TrailColors.PanelElevated),
                ) {
                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("历史", color = TrailColors.TextPrimary)
                }
            }
        }
        return
    }

    val recordedTrack = workspace.recordedTrack
    val hasRecorded = recordedTrack?.points?.isNotEmpty() == true
    val visibleDistance = if (isRecording) distanceMeters else recordedTrack?.distanceMeters ?: 0.0
    val visibleElevation = if (isRecording) elevationGain else recordedTrack?.elevationGain ?: 0.0
    val visibleDuration = if (isRecording) elapsedSeconds else recordedTrack?.durationSeconds ?: 0L
    val visiblePointCount = if (isRecording) pointCount else recordedTrack?.points?.size ?: 0
    val offlineReady = offlineMap?.status == OfflineStatus.READY
    val offlineDownloading = offlineMap?.status == OfflineStatus.DOWNLOADING
    var pendingDeletePoi by remember { mutableStateOf<Poi?>(null) }

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
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SectionLabel("当前轨迹工作空间")
                Text(
                    workspace.name,
                    color = TrailColors.EmeraldBright,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text(
                    "创建于 ${GeoUtils.formatTrackDate(workspace.createdAt)}",
                    color = TrailColors.TextMuted,
                    fontSize = 10.sp,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderIcon(Icons.Filled.Map, "在地图查看工作空间", TrailColors.EmeraldBright, onOpenMap)
                HeaderIcon(Icons.Filled.History, "历史轨迹空间", TrailColors.TextSecondary, onOpenHistory)
            }
        }

        TrailCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = TrailColors.Emerald)
                Spacer(Modifier.size(8.dp))
                Text("运动记录", color = TrailColors.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (isRecording) {
                    Badge(if (isPaused) "已暂停" else "记录中", if (isPaused) TrailColors.Amber else TrailColors.Emerald)
                    Spacer(Modifier.size(8.dp))
                }
                GpsBadge(gpsState)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("运动距离", String.format(java.util.Locale.US, "%.2f", visibleDistance / 1000.0), "km")
                Stat("运动耗时", GeoUtils.formatDuration(visibleDuration))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat(
                    "累计爬升 / 当前海拔",
                    "+${Math.round(visibleElevation)}m",
                    "/ ${currentElevation?.let { "${it}m" } ?: "--m"}",
                )
                Stat("已记录轨迹点", visiblePointCount.toString(), "点", TrailColors.Sky)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(TrailColors.Border.copy(alpha = 0.6f)))
            Spacer(Modifier.height(12.dp))
            RecordingControls(
                isRecording = isRecording,
                isPaused = isPaused,
                onStart = onStart,
                onPauseOrResume = onPauseOrResume,
                onFinish = onFinish,
            )
        }

        TrailCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Flag, contentDescription = null, tint = TrailColors.Sky)
                Spacer(Modifier.size(8.dp))
                Text("目标运动路径", color = TrailColors.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("可选", color = TrailColors.TextMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(12.dp))
            val target = workspace.targetTrack
            if (target != null && target.points.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF082F49).copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFF0C4A6E), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(TrailColors.Sky, RoundedCornerShape(50)))
                        Spacer(Modifier.size(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(target.name, color = Color(0xFFBAE6FD), fontWeight = FontWeight.SemiBold)
                            Text(
                                "${target.points.size} 个点 · 蓝色虚线显示",
                                color = Color(0xFF7DD3FC).copy(alpha = 0.7f),
                                fontSize = 10.sp,
                            )
                        }
                        TextButton(onClick = onImportTarget) {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("重新导入", fontSize = 11.sp, color = TrailColors.TextSecondary)
                        }
                    }
                }
            } else {
                Text(
                    "尚未导入目标路径。可以直接记录自己走过的路径，也可以先导入 GPX/KML 作为参考路线。",
                    color = TrailColors.TextMuted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onImportTarget,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0369A1)),
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("导入目标路径", fontSize = 11.sp)
                    }
                    Button(
                        onClick = onExport,
                        enabled = hasRecorded,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = TrailColors.PanelElevated),
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("导出已走轨迹", fontSize = 11.sp, color = TrailColors.TextPrimary)
                    }
                }
            }
        }

        if (hasRecorded && !isRecording && recordedTrack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF022C22).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF064E3B), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(TrailColors.Emerald, RoundedCornerShape(50)))
                        Spacer(Modifier.size(8.dp))
                        Text("已保存的行走轨迹", color = TrailColors.EmeraldBright, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "${recordedTrack.points.size} 点 · ${recordedTrack.distance} km · +${recordedTrack.eleGain}m · ${recordedTrack.time}",
                        color = TrailColors.EmeraldBright.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
                Button(
                    onClick = onExport,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("导出 GPX", fontSize = 11.sp)
                }
            }
        }

        TrailCard {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = TrailColors.Amber)
                Spacer(Modifier.size(8.dp))
                Text("标注点位", color = TrailColors.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(8.dp))
                Badge(workspace.pois.size.toString(), TrailColors.TextSecondary)
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onAddPoi,
                    colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("打点", fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            if (workspace.pois.isEmpty()) {
                Text(
                    "暂无标注点位，可在当前位置或地图中心添加。",
                    color = TrailColors.TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    workspace.pois.forEach { poi ->
                        PoiRow(poi, onFocus = { onFocusPoi(poi) }, onDelete = { pendingDeletePoi = poi })
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrailColors.Panel.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("离线地图", color = TrailColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        offlineReady -> "已下载：${offlineMap?.name} · ${offlineMap?.tileCount} 张瓦片"
                        offlineDownloading -> "正在下载：${offlineMap?.progress ?: 0}%"
                        else -> "进入区域选择，并自动定位到目标路径附近"
                    },
                    color = TrailColors.TextMuted,
                    fontSize = 10.sp,
                )
            }
            Button(
                onClick = onOpenOffline,
                enabled = !offlineDownloading,
                colors = ButtonDefaults.buttonColors(containerColor = TrailColors.PanelElevated),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(15.dp), tint = TrailColors.EmeraldBright)
                Spacer(Modifier.size(6.dp))
                Text(
                    when {
                        offlineReady -> "重新下载"
                        offlineDownloading -> "下载中 ${offlineMap?.progress ?: 0}%"
                        else -> "下载离线地图"
                    },
                    fontSize = 11.sp,
                    color = TrailColors.EmeraldBright,
                )
            }
        }

        Spacer(Modifier.height(56.dp))
    }

    val poiToDelete = pendingDeletePoi
    if (poiToDelete != null) {
        TrailConfirmDialog(
            title = "删除标注点位",
            text = "确认删除“${poiToDelete.name}”？",
            confirmLabel = "删除",
            dangerous = true,
            onConfirm = {
                onDeletePoi(poiToDelete.id)
                pendingDeletePoi = null
            },
            onDismiss = { pendingDeletePoi = null },
        )
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(TrailColors.PanelElevated, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RowScope.Stat(
    label: String,
    value: String,
    hint: String? = null,
    accent: Color = TrailColors.EmeraldBright,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, color = TrailColors.TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = accent, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (hint != null) {
                Spacer(Modifier.size(3.dp))
                Text(hint, color = TrailColors.TextMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun GpsBadge(gpsState: GpsState) {
    when (gpsState.status) {
        GpsStatus.ACTIVE -> Text(
            "GPS 已定位 ±${Math.round(gpsState.accuracy ?: 0f)}m",
            color = TrailColors.EmeraldBright,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        GpsStatus.WAITING -> Text(
            "等待 GPS…",
            color = TrailColors.Amber,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
        else -> Text(
            "GPS 不可用",
            color = TrailColors.Rose,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PoiRow(poi: Poi, onFocus: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrailColors.Background.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .border(1.dp, TrailColors.Border.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(parsePoiColor(poi.type), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(poi.type.label.take(1), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(poi.name, color = TrailColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${poi.type.label}${if (poi.desc.isNotBlank()) " · ${poi.desc}" else ""}",
                color = TrailColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        IconButton(onClick = onFocus, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.LocationOn, contentDescription = "在地图查看", tint = TrailColors.EmeraldBright, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Delete, contentDescription = "删除点位", tint = TrailColors.Rose, modifier = Modifier.size(16.dp))
        }
    }
}

private fun parsePoiColor(type: PoiType): Color =
    runCatching { Color(android.graphics.Color.parseColor(type.colorHex)) }.getOrDefault(TrailColors.Emerald)

@Composable
private fun RecordingControls(
    isRecording: Boolean,
    isPaused: Boolean,
    onStart: () -> Unit,
    onPauseOrResume: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onStart,
            enabled = !isRecording,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(4.dp))
            Text("开始", fontSize = 12.sp)
        }
        Button(
            onClick = onPauseOrResume,
            enabled = isRecording,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPaused) TrailColors.Emerald else TrailColors.Amber,
            ),
        ) {
            Icon(
                if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.size(4.dp))
            Text(if (isPaused) "继续" else "暂停", fontSize = 12.sp)
        }
        Button(
            onClick = onFinish,
            enabled = isRecording,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Rose),
        ) {
            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(15.dp))
            Spacer(Modifier.size(4.dp))
            Text("结束", fontSize = 12.sp)
        }
    }
}
