package com.trailguardian.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import com.trailguardian.core.model.Workspace
import com.trailguardian.core.ui.Badge
import com.trailguardian.core.ui.SectionLabel
import com.trailguardian.core.ui.TrailColors
import com.trailguardian.core.ui.TrailConfirmDialog

@Composable
fun HistoryScreen(
    workspaces: List<Workspace>,
    activeWorkspaceId: String?,
    isRecording: Boolean,
    onCreate: () -> Unit,
    onLoad: (Workspace) -> Unit,
    onViewDetails: (Workspace) -> Unit,
    onExport: (Workspace) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<Workspace?>(null) }

    if (workspaces.isEmpty()) {
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
                Icon(Icons.Filled.History, contentDescription = null, tint = TrailColors.TextMuted)
            }
            Spacer(Modifier.height(16.dp))
            Text("空空如也", color = TrailColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "每次外出计划都可以建立一个轨迹工作空间，目标路径、已走轨迹和标注点位会一起保存。",
                color = TrailColors.TextMuted,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("新建轨迹")
            }
        }
        return
    }

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
            Column {
                SectionLabel("工作空间管理")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, contentDescription = null, tint = TrailColors.EmeraldBright, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("历史轨迹", color = TrailColors.EmeraldBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(5.dp))
                Text("新建轨迹", fontSize = 12.sp)
            }
        }

        workspaces.forEach { workspace ->
            val isActive = workspace.id == activeWorkspaceId
            val target = workspace.targetTrack
            val recorded = workspace.recordedTrack
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isActive) Color(0xFF022C22).copy(alpha = 0.35f) else TrailColors.Panel.copy(alpha = 0.85f),
                        RoundedCornerShape(16.dp),
                    )
                    .border(
                        1.dp,
                        if (isActive) Color(0xFF047857) else TrailColors.Border.copy(alpha = 0.8f),
                        RoundedCornerShape(16.dp),
                    )
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                workspace.name,
                                color = TrailColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (isActive) {
                                Spacer(Modifier.size(6.dp))
                                Badge("已加载", TrailColors.EmeraldBright)
                            }
                        }
                        Text(
                            GeoUtils.formatTrackDate(workspace.createdAt) +
                                if (workspace.updatedAt != workspace.createdAt) {
                                    " · 更新 ${GeoUtils.formatTrackDate(workspace.updatedAt)}"
                                } else {
                                    ""
                                },
                            color = TrailColors.TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Badge("目标 ${target?.points?.size ?: 0}", if (target != null) TrailColors.Sky else TrailColors.TextMuted)
                        Badge("已走 ${recorded?.points?.size ?: 0}", if (recorded != null) TrailColors.EmeraldBright else TrailColors.TextMuted)
                        Badge("点位 ${workspace.pois.size}", TrailColors.Amber)
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (target != null) {
                    Text(
                        target.name,
                        color = TrailColors.Sky,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                if (recorded != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${recorded.distance} km", color = TrailColors.EmeraldBright, fontSize = 11.sp)
                        Text("+${recorded.eleGain}m", color = TrailColors.Amber, fontSize = 11.sp)
                        Text(recorded.time, color = TrailColors.TextMuted, fontSize = 11.sp)
                    }
                } else {
                    Text("尚未记录已走轨迹", color = TrailColors.TextMuted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(TrailColors.Border.copy(alpha = 0.55f)))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    HistoryAction(Icons.Filled.FolderOpen, "加载", TrailColors.EmeraldBright, Modifier.weight(1f)) { onLoad(workspace) }
                    HistoryAction(Icons.Filled.Visibility, "详情", TrailColors.TextSecondary, Modifier.weight(1f)) { onViewDetails(workspace) }
                    HistoryAction(
                        Icons.Filled.Download,
                        "导出",
                        TrailColors.Sky,
                        Modifier.weight(1f),
                        enabled = recorded?.points?.isNotEmpty() == true,
                    ) { onExport(workspace) }
                    HistoryAction(
                        Icons.Filled.Delete,
                        "删除",
                        TrailColors.Rose,
                        Modifier.weight(1f),
                        enabled = !(isActive && isRecording),
                    ) { pendingDelete = workspace }
                }
            }
        }

        Spacer(Modifier.height(56.dp))
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        TrailConfirmDialog(
            title = "删除工作空间",
            text = "确认删除“${toDelete.name}”？目标路径、已走轨迹和标注点位将一起删除，且不可撤销。",
            confirmLabel = "删除",
            dangerous = true,
            onConfirm = {
                onDelete(toDelete.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun HistoryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(4.dp))
        Text(label, color = tint, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
