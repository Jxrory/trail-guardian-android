package com.trailguardian.feature.tools

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailguardian.core.geo.Coordinate
import com.trailguardian.core.geo.CoordinateSystem
import com.trailguardian.core.geo.CoordinateTransforms
import com.trailguardian.core.geo.GeoUtils
import com.trailguardian.core.model.LivePosition
import com.trailguardian.core.ui.Badge
import com.trailguardian.core.ui.TrailColors
import com.trailguardian.feature.map.CompassSensor
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CompassToolScreen(
    position: LivePosition?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    var heading by remember { mutableStateOf<Float?>(null) }
    val compass = remember { CompassSensor(context) { value -> heading = value } }

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

    val gcj02 = position?.let {
        CoordinateTransforms.convert(CoordinateSystem.WGS84, CoordinateSystem.GCJ02, it.lat, it.lng)
    } ?: Coordinate(0.0, 0.0)
    val hasHeading = heading != null && heading?.isFinite() == true

    ToolScaffold(
        title = "指南针",
        subtitle = "户外工具",
        onBack = onBack,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrailColors.Panel, RoundedCornerShape(14.dp))
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CompassStat(
                label = "经度 (GCJ-02)",
                value = if (position != null) String.format(Locale.US, "%.8f°E", gcj02.lng) else "等待定位",
            )
            CompassStat(
                label = "误差",
                value = position?.accuracy?.let { String.format(Locale.US, "±%.1f m", it) } ?: "--",
            )
            CompassStat(
                label = "纬度 (GCJ-02)",
                value = if (position != null) String.format(Locale.US, "%.8f°N", gcj02.lat) else "等待定位",
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111827), RoundedCornerShape(20.dp))
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Navigation,
                    contentDescription = null,
                    tint = if (hasHeading) TrailColors.EmeraldBright else TrailColors.TextMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    GeoUtils.describeHeading(if (hasHeading) heading else null),
                    color = if (hasHeading) TrailColors.TextPrimary else TrailColors.TextMuted,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.size(14.dp))
            CompassDial(heading = if (hasHeading) heading else null)
            Spacer(Modifier.size(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(
                    if (hasHeading) "方向传感器正常" else "方向传感器不可用",
                    if (hasHeading) TrailColors.EmeraldBright else TrailColors.Amber,
                )
                Badge(
                    if (position != null) "GPS 已定位" else "GPS 待定位",
                    if (position != null) TrailColors.Sky else TrailColors.TextMuted,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrailColors.Panel.copy(alpha = 0.75f), RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Explore,
                contentDescription = null,
                tint = TrailColors.EmeraldBright,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "转动手机时，顶部橙色箭头始终表示手机正面朝向。使用前请远离磁铁和强电流设备。",
                color = TrailColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CompassStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TrailColors.TextMuted, fontSize = 9.sp)
        Spacer(Modifier.size(3.dp))
        Text(value, color = TrailColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompassDial(heading: Float?) {
    val hasHeading = heading != null && heading.isFinite()
    val angle = if (hasHeading) -heading!! else 0f
    Box(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .rotate(angle),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f - 2.dp.toPx()
                drawCircle(color = Color(0xFF050B16), radius = radius)
                drawCircle(
                    color = if (hasHeading) TrailColors.Emerald.copy(alpha = 0.7f) else TrailColors.Border,
                    radius = radius,
                    style = Stroke(width = 1.5f.dp.toPx()),
                )

                for (tick in 0 until 72) {
                    val tickAngle = Math.toRadians(tick * 5.0 - 90.0)
                    val major = tick % 9 == 0
                    val medium = tick % 3 == 0
                    val outer = radius - 8.dp.toPx()
                    val length = when {
                        major -> 15.dp.toPx()
                        medium -> 9.dp.toPx()
                        else -> 4.dp.toPx()
                    }
                    val start = Offset(
                        center.x + cos(tickAngle).toFloat() * outer,
                        center.y + sin(tickAngle).toFloat() * outer,
                    )
                    val end = Offset(
                        center.x + cos(tickAngle).toFloat() * (outer - length),
                        center.y + sin(tickAngle).toFloat() * (outer - length),
                    )
                    drawLine(
                        color = when {
                            !hasHeading -> TrailColors.TextMuted.copy(alpha = 0.4f)
                            major -> Color.White
                            else -> TrailColors.TextSecondary.copy(alpha = 0.55f)
                        },
                        start = start,
                        end = end,
                        strokeWidth = if (major) 2.5f.dp.toPx() else 1.dp.toPx(),
                    )
                }

                val crossColor = TrailColors.TextSecondary.copy(alpha = 0.7f)
                drawLine(
                    color = crossColor,
                    start = Offset(center.x - radius * 0.48f, center.y),
                    end = Offset(center.x + radius * 0.48f, center.y),
                    strokeWidth = 1.3f.dp.toPx(),
                )
                drawLine(
                    color = crossColor,
                    start = Offset(center.x, center.y - radius * 0.48f),
                    end = Offset(center.x, center.y + radius * 0.48f),
                    strokeWidth = 1.3f.dp.toPx(),
                )
                drawCircle(
                    color = Color(0xFF374151),
                    radius = 21.dp.toPx(),
                    center = center,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = 25.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            CardinalLabel("N", Alignment.TopCenter, Color.White)
            CardinalLabel("S", Alignment.BottomCenter, Color.White)
            CardinalLabel("E", Alignment.CenterEnd, Color.White)
            CardinalLabel("W", Alignment.CenterStart, Color.White)
        }

        Canvas(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 30.dp, height = 22.dp)
                .padding(top = 2.dp),
        ) {
            val path = Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path,
                color = if (hasHeading) Color(0xFFFB923C) else TrailColors.TextMuted.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun BoxScope.CardinalLabel(label: String, alignment: Alignment, color: Color) {
    Box(
        modifier = Modifier
            .align(alignment)
            .padding(32.dp)
            .size(25.dp)
            .background(Color(0xFF050B16).copy(alpha = 0.94f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
