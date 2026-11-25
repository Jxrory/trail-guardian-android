package com.trailguardian.feature.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.location.Geocoder
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailguardian.core.geo.Coordinate
import com.trailguardian.core.geo.CoordinateFormat
import com.trailguardian.core.geo.CoordinateFormatter
import com.trailguardian.core.geo.CoordinateSystem
import com.trailguardian.core.geo.CoordinateTransforms
import com.trailguardian.core.model.LivePosition
import com.trailguardian.core.ui.SectionLabel
import com.trailguardian.core.ui.TrailColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

@Composable
fun CoordinateConverterScreen(
    position: LivePosition?,
    onBack: () -> Unit,
    onOpenMap: (Double, Double) -> Unit,
    onNotify: (String) -> Unit,
) {
    val context = LocalContext.current
    var selectedSystemName by rememberSaveable { mutableStateOf(CoordinateSystem.GCJ02.name) }
    var selectedFormatName by rememberSaveable { mutableStateOf(CoordinateFormat.DECIMAL.name) }
    var latitudeText by rememberSaveable { mutableStateOf("") }
    var longitudeText by rememberSaveable { mutableStateOf("") }
    var north by rememberSaveable { mutableStateOf(true) }
    var east by rememberSaveable { mutableStateOf(true) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var locationLabel by remember { mutableStateOf<String?>(null) }

    val selectedSystem = CoordinateSystem.entries.firstOrNull { it.name == selectedSystemName }
        ?: CoordinateSystem.GCJ02
    val selectedFormat = CoordinateFormat.entries.firstOrNull { it.name == selectedFormatName }
        ?: CoordinateFormat.DECIMAL

    fun setFromWgs84(wgsLat: Double, wgsLng: Double, system: CoordinateSystem = selectedSystem) {
        val coordinate = CoordinateTransforms.convert(CoordinateSystem.WGS84, system, wgsLat, wgsLng)
        latitudeText = CoordinateFormatter.format(coordinate.lat, selectedFormat)
        longitudeText = CoordinateFormatter.format(coordinate.lng, selectedFormat)
        north = coordinate.lat >= 0
        east = coordinate.lng >= 0
    }

    LaunchedEffect(position?.lat, position?.lng) {
        val current = position ?: return@LaunchedEffect
        if (!initialized) {
            setFromWgs84(current.lat, current.lng)
            initialized = true
        }
    }

    LaunchedEffect(
        position?.let { String.format(Locale.US, "%.3f", it.lat) },
        position?.let { String.format(Locale.US, "%.3f", it.lng) },
    ) {
        val current = position ?: return@LaunchedEffect
        if (!Geocoder.isPresent()) return@LaunchedEffect
        locationLabel = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.CHINA)
                    .getFromLocation(current.lat, current.lng, 1)
                    ?.firstOrNull()
                    ?.let { address ->
                        listOfNotNull(
                            address.locality ?: address.adminArea,
                            address.subLocality,
                            address.thoroughfare,
                            address.featureName,
                        ).distinct().joinToString("")
                    }
                    ?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }

    val latitude = CoordinateFormatter.parse(latitudeText, selectedFormat, 90.0)
    val longitude = CoordinateFormatter.parse(longitudeText, selectedFormat, 180.0)
    val signedLatitude = latitude?.let { if (north) it else -it }
    val signedLongitude = longitude?.let { if (east) it else -it }
    val converted = if (signedLatitude != null && signedLongitude != null) {
        CoordinateTransforms.allSystems(selectedSystem, signedLatitude, signedLongitude)
    } else {
        null
    }

    fun copyCoordinate(system: CoordinateSystem, coordinate: Coordinate) {
        val text = CoordinateFormatter.describe(coordinate)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("${system.label} 坐标", text))
        onNotify("已复制 ${system.label} 坐标")
    }

    ToolScaffold(
        title = "经纬度换算",
        subtitle = "户外工具",
        onBack = onBack,
    ) {
        CurrentLocationCard(
            position = position,
            selectedSystem = selectedSystem,
            locationLabel = locationLabel,
            onCopy = {
                val current = position ?: return@CurrentLocationCard
                val coordinate = CoordinateTransforms.convert(
                    CoordinateSystem.WGS84,
                    selectedSystem,
                    current.lat,
                    current.lng,
                )
                copyCoordinate(selectedSystem, coordinate)
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrailColors.Panel, RoundedCornerShape(16.dp))
                .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = TrailColors.EmeraldBright,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text("坐标系转换", color = TrailColors.TextPrimary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                SectionLabel("${selectedSystem.label} 输入")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CoordinateSystem.entries.forEach { system ->
                    ChoiceChip(
                        label = system.label,
                        selected = system == selectedSystem,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (system == selectedSystem) return@ChoiceChip
                        if (latitude != null && longitude != null) {
                            val target = CoordinateTransforms.convert(
                                selectedSystem,
                                system,
                                signedLatitude ?: 0.0,
                                signedLongitude ?: 0.0,
                            )
                            selectedSystemName = system.name
                            latitudeText = CoordinateFormatter.format(target.lat, selectedFormat)
                            longitudeText = CoordinateFormatter.format(target.lng, selectedFormat)
                            north = target.lat >= 0
                            east = target.lng >= 0
                        } else {
                            selectedSystemName = system.name
                            position?.let { setFromWgs84(it.lat, it.lng, system) }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CoordinateFormat.entries.forEach { format ->
                    ChoiceChip(
                        label = format.label,
                        selected = format == selectedFormat,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (format == selectedFormat) return@ChoiceChip
                        if (latitude != null) latitudeText = CoordinateFormatter.format(latitude, format)
                        if (longitude != null) longitudeText = CoordinateFormatter.format(longitude, format)
                        selectedFormatName = format.name
                    }
                }
            }

            CoordinateInput(
                label = "纬度",
                positiveLabel = "北纬",
                negativeLabel = "南纬",
                positive = north,
                value = latitudeText,
                placeholder = when (selectedFormat) {
                    CoordinateFormat.DECIMAL -> "25.22935814"
                    CoordinateFormat.DM -> "25 13.761488"
                    CoordinateFormat.DMS -> "25 13 45.689"
                },
                onValueChange = { latitudeText = it },
                onDirectionChange = { north = it },
            )

            CoordinateInput(
                label = "经度",
                positiveLabel = "东经",
                negativeLabel = "西经",
                positive = east,
                value = longitudeText,
                placeholder = when (selectedFormat) {
                    CoordinateFormat.DECIMAL -> "103.01169630"
                    CoordinateFormat.DM -> "103 0.701778"
                    CoordinateFormat.DMS -> "103 0 42.106"
                },
                onValueChange = { longitudeText = it },
                onDirectionChange = { east = it },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val current = position
                        if (current == null) onNotify("正在等待 GPS 定位") else setFromWgs84(current.lat, current.lng)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("使用当前位置", fontSize = 11.sp)
                }
                OutlinedButton(
                    onClick = {
                        latitudeText = ""
                        longitudeText = ""
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("清空", fontSize = 11.sp)
                }
            }
        }

        ToolSectionTitle("转换结果")
        if (converted == null) {
            Text(
                "输入有效的纬度和经度后，将同时显示三种坐标系结果。",
                color = TrailColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrailColors.Panel.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrailColors.Panel, RoundedCornerShape(16.dp))
                    .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp)),
            ) {
                CoordinateSystem.entries.forEach { system ->
                    val coordinate = converted.getValue(system)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${system.label} 坐标系",
                                    color = TrailColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(system.detail, color = TrailColors.TextMuted, fontSize = 9.sp)
                                if (system == selectedSystem) {
                                    Spacer(Modifier.size(6.dp))
                                    Text(
                                        "输入基准",
                                        color = TrailColors.EmeraldBright,
                                        fontSize = 9.sp,
                                        modifier = Modifier
                                            .background(TrailColors.Emerald.copy(alpha = 0.12f), RoundedCornerShape(50))
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.size(3.dp))
                            Text(
                                CoordinateFormatter.describe(coordinate),
                                color = TrailColors.TextSecondary,
                                fontSize = 11.sp,
                            )
                        }
                        IconButton(onClick = { copyCoordinate(system, coordinate) }) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "复制 ${system.label} 坐标",
                                tint = TrailColors.EmeraldBright,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                    if (system != CoordinateSystem.entries.last()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .height(1.dp)
                                .background(TrailColors.Border),
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val wgs84 = converted.getValue(CoordinateSystem.WGS84)
                    onOpenMap(wgs84.lat, wgs84.lng)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
            ) {
                Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("在地图中查看 WGS84 位置")
            }
        }
    }
}

@Composable
private fun CurrentLocationCard(
    position: LivePosition?,
    selectedSystem: CoordinateSystem,
    locationLabel: String?,
    onCopy: () -> Unit,
) {
    val coordinate = position?.let {
        CoordinateTransforms.convert(CoordinateSystem.WGS84, selectedSystem, it.lat, it.lng)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF047857), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.MyLocation,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("当前位置", color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    "${selectedSystem.label} · ${selectedSystem.detail}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                )
            }
            IconButton(onClick = onCopy, enabled = coordinate != null) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制当前位置",
                    tint = Color.White.copy(alpha = if (coordinate != null) 0.9f else 0.35f),
                )
            }
        }
        if (coordinate == null) {
            Text("等待 GPS 定位，仍可手动输入坐标。", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        } else {
            LocationLine("经纬度", CoordinateFormatter.describe(coordinate), Color.White)
            LocationLine(
                "精度",
                position.accuracy?.let { "±${String.format(Locale.US, "%.0f", it)} 米" } ?: "未知",
                Color.White.copy(alpha = 0.9f),
            )
            LocationLine("位置", locationLabel ?: "正在解析位置名称…", Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
private fun LocationLine(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(label, color = Color.White.copy(alpha = 0.65f), fontSize = 10.sp, modifier = Modifier.size(width = 48.dp, height = 18.dp))
        Text(value, color = valueColor, fontSize = 11.sp)
    }
}

@Composable
private fun CoordinateInput(
    label: String,
    positiveLabel: String,
    negativeLabel: String,
    positive: Boolean,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onDirectionChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = TrailColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DirectionChip(positiveLabel, positive) { onDirectionChange(true) }
            Spacer(Modifier.size(4.dp))
            DirectionChip(negativeLabel, !positive) { onDirectionChange(false) }
            Spacer(Modifier.size(8.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(placeholder, color = TrailColors.TextMuted, fontSize = 11.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = coordinateFieldColors(),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = TrailColors.TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@Composable
private fun DirectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                if (selected) TrailColors.Emerald.copy(alpha = 0.2f) else TrailColors.PanelElevated,
                RoundedCornerShape(50),
            )
            .border(
                1.dp,
                if (selected) TrailColors.Emerald else TrailColors.Border,
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) TrailColors.EmeraldBright else TrailColors.TextMuted,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                if (selected) TrailColors.Emerald else TrailColors.PanelElevated,
                RoundedCornerShape(10.dp),
            )
            .border(
                1.dp,
                if (selected) TrailColors.Emerald else TrailColors.Border,
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else TrailColors.TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun coordinateFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TrailColors.Emerald,
    unfocusedBorderColor = TrailColors.Border,
    focusedContainerColor = TrailColors.Background.copy(alpha = 0.7f),
    unfocusedContainerColor = TrailColors.Background.copy(alpha = 0.7f),
    cursorColor = TrailColors.Emerald,
)
