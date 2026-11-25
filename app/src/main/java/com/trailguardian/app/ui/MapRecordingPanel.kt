package com.trailguardian.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.model.GpsState
import com.trailguardian.core.model.GpsStatus
import com.trailguardian.core.model.Workspace
import com.trailguardian.core.ui.Badge
import com.trailguardian.core.ui.TrailColors
import com.trailguardian.service.recording.RecordingState
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class MapRecordPanelLevel {
    OVERVIEW,
    CONTROLS,
    DETAILS,
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MapRecordingPanel(
    workspace: Workspace?,
    recording: RecordingState,
    currentElevation: Int?,
    gpsState: GpsState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit,
    onOpenHistory: () -> Unit,
    maxAvailableHeight: Dp,
    modifier: Modifier = Modifier,
) {
    var level by rememberSaveable(workspace?.id) {
        mutableStateOf(MapRecordPanelLevel.OVERVIEW)
    }
    val availableHeight = maxAvailableHeight.coerceAtLeast(140.dp)
    val overviewHeight = minOf(118.dp, availableHeight * 0.32f)
    val controlsHeight = minOf(216.dp, availableHeight * 0.55f)
    val detailsHeight = minOf(310.dp, availableHeight * 0.72f).coerceAtLeast(controlsHeight)
    val heights = listOf(overviewHeight, controlsHeight, detailsHeight)
    val density = LocalDensity.current
    val anchors = remember(overviewHeight, controlsHeight, detailsHeight, density) {
        DraggableAnchors {
            MapRecordPanelLevel.OVERVIEW at -with(density) { overviewHeight.toPx() }
            MapRecordPanelLevel.CONTROLS at -with(density) { controlsHeight.toPx() }
            MapRecordPanelLevel.DETAILS at -with(density) { detailsHeight.toPx() }
        }
    }
    val dragState = remember(workspace?.id) {
        AnchoredDraggableState(
            initialValue = level,
            positionalThreshold = { distance -> distance * 0.32f },
            velocityThreshold = { with(density) { 180.dp.toPx() } },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = 0.5f,
            ),
            confirmValueChange = { true },
        )
    }
    LaunchedEffect(anchors, workspace?.id) {
        dragState.updateAnchors(anchors, dragState.currentValue)
    }
    LaunchedEffect(dragState.currentValue) {
        level = dragState.currentValue
    }

    val dragOffset = dragState.offset
    val displayedHeight = if (dragOffset.isNaN()) {
        heights[level.ordinal]
    } else {
        with(density) { (-dragOffset).toDp() }.coerceIn(overviewHeight, detailsHeight)
    }
    val revealedLevel = when {
        displayedHeight >= detailsHeight - 4.dp -> MapRecordPanelLevel.DETAILS
        displayedHeight >= controlsHeight - 4.dp -> MapRecordPanelLevel.CONTROLS
        else -> MapRecordPanelLevel.OVERVIEW
    }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (revealedLevel.ordinal >= MapRecordPanelLevel.CONTROLS.ordinal) 1f else 0f,
        animationSpec = tween(durationMillis = revealedLevel.ordinal * 120),
        label = "controlsAlpha",
    )

    val coroutineScope = rememberCoroutineScope()
    BackHandler(enabled = workspace != null && revealedLevel != MapRecordPanelLevel.OVERVIEW) {
        coroutineScope.launch { dragState.animateTo(MapRecordPanelLevel.OVERVIEW) }
    }

    val effectiveHeight = if (workspace == null) minOf(88.dp, availableHeight) else displayedHeight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(effectiveHeight)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(TrailColors.Panel.copy(alpha = 0.98f))
            .border(
                width = 1.dp,
                color = TrailColors.Border.copy(alpha = 0.9f),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            ),
    ) {
        if (workspace == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                NoWorkspaceAction(onOpenHistory)
            }
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .anchoredDraggable(dragState, Orientation.Vertical)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            DragHandle()
            Spacer(Modifier.height(2.dp))
            CoreMetrics(workspace, recording)

            if (revealedLevel != MapRecordPanelLevel.OVERVIEW) {
                Column(Modifier.alpha(controlsAlpha)) {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(TrailColors.Border.copy(alpha = 0.7f)),
                    )
                    Spacer(Modifier.height(8.dp))
                    RecordingStatus(workspace, recording)
                }

                if (revealedLevel == MapRecordPanelLevel.DETAILS) {
                    Column(Modifier.alpha(controlsAlpha)) {
                        Spacer(Modifier.height(8.dp))
                        DetailMetrics(workspace, recording, currentElevation, gpsState)
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            if (revealedLevel != MapRecordPanelLevel.OVERVIEW) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.alpha(controlsAlpha)) {
                    RecordingActions(recording, onStart, onPause, onFinish)
                }
            }
        }
    }
}

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier.fillMaxWidth().height(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(TrailColors.TextMuted.copy(alpha = 0.8f)),
        )
    }
}

@Composable
private fun CoreMetrics(workspace: Workspace, recording: RecordingState) {
    val recordedTrack = workspace.recordedTrack
    val distanceMeters = if (recording.isRecording) {
        recording.distanceMeters
    } else {
        recordedTrack?.distanceMeters ?: 0.0
    }
    val durationSeconds = if (recording.isRecording) {
        recording.elapsedSeconds
    } else {
        recordedTrack?.durationSeconds ?: 0L
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PrimaryMetric(
            label = "配速",
            value = GeoUtils.formatAveragePace(distanceMeters, durationSeconds),
            color = TrailColors.EmeraldBright,
            modifier = Modifier.weight(1f),
        )
        PrimaryMetric(
            label = "里程",
            value = String.format(Locale.US, "%.2f", distanceMeters / 1000.0),
            color = TrailColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        PrimaryMetric(
            label = "运动耗时",
            value = GeoUtils.formatDuration(durationSeconds),
            color = TrailColors.Sky,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PrimaryMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Text(text = label, color = TrailColors.TextMuted, fontSize = 10.sp)
    }
}

@Composable
private fun RecordingStatus(workspace: Workspace, recording: RecordingState) {
    val statusText = when {
        !recording.isRecording -> "等待开始"
        recording.isPaused -> "已暂停"
        else -> "运动中"
    }
    val statusColor = when {
        !recording.isRecording -> TrailColors.TextMuted
        recording.isPaused -> TrailColors.Amber
        else -> TrailColors.Emerald
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = workspace.name,
            color = TrailColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Badge(statusText, statusColor)
    }
}

@Composable
private fun DetailMetrics(
    workspace: Workspace,
    recording: RecordingState,
    currentElevation: Int?,
    gpsState: GpsState,
) {
    val recordedTrack = workspace.recordedTrack
    val elevationGain = if (recording.isRecording) {
        recording.elevationGain
    } else {
        recordedTrack?.elevationGain ?: 0.0
    }
    val pointCount = if (recording.isRecording) {
        recording.points.size
    } else {
        recordedTrack?.points?.size ?: 0
    }
    val gpsText = when (gpsState.status) {
        GpsStatus.ACTIVE -> "已定位 ±${Math.round(gpsState.accuracy ?: 0f)}m"
        GpsStatus.WAITING -> "等待定位"
        else -> "不可用"
    }
    val gpsColor = when (gpsState.status) {
        GpsStatus.ACTIVE -> TrailColors.EmeraldBright
        GpsStatus.WAITING -> TrailColors.Amber
        else -> TrailColors.Rose
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailMetric(
                label = "累计爬升",
                value = "+${Math.round(elevationGain)}m",
                color = TrailColors.Amber,
                modifier = Modifier.weight(1f),
            )
            DetailMetric(
                label = "当前海拔",
                value = currentElevation?.let { "${it}m" } ?: "--",
                color = TrailColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DetailMetric(
                label = "轨迹点",
                value = "${pointCount} 点",
                color = TrailColors.Sky,
                modifier = Modifier.weight(1f),
            )
            DetailMetric(
                label = "GPS",
                value = gpsText,
                color = gpsColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailMetric(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TrailColors.TextMuted, fontSize = 10.sp)
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecordingActions(
    recording: RecordingState,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onFinish: () -> Unit,
) {
    when {
        !recording.isRecording -> {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("开始", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        recording.isPaused -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onPause,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("继续", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onFinish,
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Rose),
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("结束", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        else -> {
            Button(
                onClick = onPause,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Amber),
            ) {
                Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("暂停", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun NoWorkspaceAction(onOpenHistory: () -> Unit) {
    Button(
        onClick = onOpenHistory,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF78350F).copy(alpha = 0.7f),
            contentColor = TrailColors.Amber,
        ),
    ) {
        Text("未加载工作空间 · 选择或新建轨迹", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}
