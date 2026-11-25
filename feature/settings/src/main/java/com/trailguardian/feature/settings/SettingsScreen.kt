package com.trailguardian.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.ui.Badge
import com.trailguardian.core.ui.SectionLabel
import com.trailguardian.core.ui.TrailColors

@Composable
fun SettingsScreen(
    lowPowerEnabled: Boolean,
    onLowPowerChange: (Boolean) -> Unit,
    serviceRunning: Boolean,
    backgroundLocationGranted: Boolean,
    batteryOptimizationRestricted: Boolean,
    offlineMapCount: Int,
    cacheCount: Int,
    cacheSizeBytes: Long,
    versionName: String,
    onOpenCoordinateConverter: () -> Unit,
    onOpenPaceCalculator: () -> Unit,
    onOpenCompassTool: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrailColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            SectionLabel("TrailGuardian")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = TrailColors.EmeraldBright, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(6.dp))
                Text("设置", color = TrailColors.EmeraldBright, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrailColors.Panel, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFF065F46), RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, contentDescription = null, tint = TrailColors.EmeraldBright, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("实时功耗与后台记录状态", color = TrailColors.EmeraldBright, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Badge(
                    if (lowPowerEnabled) "智能低功耗" else "高性能",
                    if (lowPowerEnabled) TrailColors.EmeraldBright else TrailColors.Amber,
                )
            }
            Spacer(Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusCell("GPS 策略", if (lowPowerEnabled) "3s / 运动态" else "1.5s / 高性能", Modifier.weight(1f))
                StatusCell(
                    "前台定位服务",
                    if (serviceRunning) "运行中" else "未启动",
                    Modifier.weight(1f),
                    if (serviceRunning) TrailColors.EmeraldBright else TrailColors.TextMuted,
                )
            }
            Spacer(Modifier.padding(top = 10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusCell(
                    "后台定位权限",
                    if (backgroundLocationGranted) "始终允许" else "仅前台",
                    Modifier.weight(1f),
                    if (backgroundLocationGranted) TrailColors.EmeraldBright else TrailColors.Amber,
                )
                StatusCell(
                    "电池优化",
                    if (batteryOptimizationRestricted) "受限" else "未受限",
                    Modifier.weight(1f),
                    if (batteryOptimizationRestricted) TrailColors.Amber else TrailColors.EmeraldBright,
                )
            }
        }

        ToggleCard(
            title = "自适应省电与静止休眠",
            description = "扎营休息时降低采样频率，延长续航",
            checked = lowPowerEnabled,
            onCheckedChange = onLowPowerChange,
        )

        if (!backgroundLocationGranted || batteryOptimizationRestricted) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrailColors.Panel.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("后台记录建议", color = TrailColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    "锁屏和切后台记录依赖前台定位服务。请允许“始终”定位，并将 TrailGuardian 加入电池优化白名单。部分厂商的省电策略仍可能终止服务。",
                    color = TrailColors.TextMuted,
                    fontSize = 11.sp,
                )
                if (!backgroundLocationGranted) {
                    Button(
                        onClick = onRequestBackgroundLocation,
                        colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
                    ) {
                        Icon(Icons.Filled.BatterySaver, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(5.dp))
                        Text("授予后台定位权限", fontSize = 11.sp)
                    }
                }
                if (batteryOptimizationRestricted) {
                    Button(
                        onClick = onOpenBatterySettings,
                        colors = ButtonDefaults.buttonColors(containerColor = TrailColors.PanelElevated),
                    ) {
                        Text("打开电池优化设置", fontSize = 11.sp, color = TrailColors.TextPrimary)
                    }
                }
            }
        }

        SectionLabel("户外工具")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolEntryCard(
                title = "经纬度换算",
                description = "WGS84 / GCJ-02 / BD09",
                icon = Icons.Filled.MyLocation,
                modifier = Modifier.weight(1f),
                onClick = onOpenCoordinateConverter,
            )
            ToolEntryCard(
                title = "配速换算",
                description = "里程、时间与配速",
                icon = Icons.Filled.Speed,
                modifier = Modifier.weight(1f),
                onClick = onOpenPaceCalculator,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolEntryCard(
                title = "指南针",
                description = "方向、方位与当前位置",
                icon = Icons.Filled.Explore,
                modifier = Modifier.weight(1f),
                onClick = onOpenCompassTool,
            )
            ToolEntryCard(
                title = "离线地图",
                description = "$offlineMapCount 个区域 · 缓存 $cacheCount 张",
                status = GeoUtils.formatBytes(cacheSizeBytes),
                icon = Icons.Filled.Layers,
                modifier = Modifier.weight(1f),
                onClick = onOpenOfflineMaps,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrailColors.Panel.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .border(1.dp, TrailColors.Border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "野径户外助手 · TrailGuardian v$versionName",
                color = TrailColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Kotlin + Jetpack Compose，支持工作空间、等高线、GPX、轨迹、户外工具和离线地图。",
                color = TrailColors.TextMuted,
                fontSize = 10.sp,
            )
            Text(
                "等高线由 AWS Terrain 高程数据在本地生成，首次访问需要联网。",
                color = TrailColors.TextMuted,
                fontSize = 10.sp,
            )
        }

        Spacer(Modifier.padding(bottom = 56.dp))
    }
}

@Composable
private fun ToolEntryCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    status: String? = null,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(112.dp)
            .background(TrailColors.Panel.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = TrailColors.EmeraldBright,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = "打开$title",
                tint = TrailColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            title,
            color = TrailColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            description,
            color = TrailColors.TextMuted,
            fontSize = 9.sp,
        )
        status?.let {
            Spacer(Modifier.weight(1f))
            Text(
                it,
                color = TrailColors.EmeraldBright,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StatusCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TrailColors.TextPrimary,
) {
    Column(
        modifier = modifier
            .background(TrailColors.Background.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = TrailColors.TextMuted, fontSize = 10.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ToggleCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrailColors.Panel.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TrailColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text(description, color = TrailColors.TextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TrailColors.Emerald,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = TrailColors.Border,
            ),
        )
    }
}
