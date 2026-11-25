package com.trailguardian.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailguardian.core.geo.DistanceUnit
import com.trailguardian.core.geo.PaceCalculator
import com.trailguardian.core.geo.PaceResult
import com.trailguardian.core.ui.Badge
import com.trailguardian.core.ui.SectionLabel
import com.trailguardian.core.ui.TrailColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun PaceCalculatorScreen(
    onBack: () -> Unit,
    onNotify: (String) -> Unit,
) {
    var unitName by rememberSaveable { mutableStateOf(DistanceUnit.KILOMETER.name) }
    var distanceText by rememberSaveable { mutableStateOf("") }
    var hoursText by rememberSaveable { mutableStateOf("") }
    var minutesText by rememberSaveable { mutableStateOf("") }
    var secondsText by rememberSaveable { mutableStateOf("") }
    var paceMinutesText by rememberSaveable { mutableStateOf("") }
    var paceSecondsText by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<PaceResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val unit = DistanceUnit.entries.firstOrNull { it.name == unitName } ?: DistanceUnit.KILOMETER

    fun clearAll() {
        distanceText = ""
        hoursText = ""
        minutesText = ""
        secondsText = ""
        paceMinutesText = ""
        paceSecondsText = ""
        result = null
        error = null
    }

    fun populate(resultValue: PaceResult, targetUnit: DistanceUnit) {
        val displayDistance = resultValue.distanceKilometers / targetUnit.kilometers
        distanceText = String.format(Locale.US, "%.2f", displayDistance)
        hoursText = (resultValue.durationSeconds / 3600).toString()
        minutesText = ((resultValue.durationSeconds % 3600) / 60).toString()
        secondsText = (resultValue.durationSeconds % 60).toString()
        val paceSeconds = (resultValue.paceSecondsPerKilometer * targetUnit.kilometers).roundToInt()
        paceMinutesText = (paceSeconds / 60).toString()
        paceSecondsText = (paceSeconds % 60).toString()
    }

    fun switchUnit(target: DistanceUnit) {
        if (target == unit) return
        val currentResult = result
        unitName = target.name
        if (currentResult != null) {
            populate(currentResult, target)
        } else {
            error = null
        }
    }

    fun calculate() {
        val distance = distanceText.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
        val hours = hoursText.trim().toLongOrNull() ?: 0L
        val minutes = minutesText.trim().toLongOrNull() ?: 0L
        val seconds = secondsText.trim().toLongOrNull() ?: 0L
        val hasTime = hoursText.isNotBlank() || minutesText.isNotBlank() || secondsText.isNotBlank()
        val duration = hours * 3600 + minutes * 60 + seconds
        val paceMinutes = paceMinutesText.trim().toLongOrNull() ?: 0L
        val paceSeconds = paceSecondsText.trim().toLongOrNull() ?: 0L
        val hasPace = paceMinutesText.isNotBlank() || paceSecondsText.isNotBlank()
        val pacePerUnit = paceMinutes * 60 + paceSeconds

        if (hasTime && (hours !in 0..999 || minutes !in 0..59 || seconds !in 0..59)) {
            error = "运动时间格式不正确，分钟和秒应在 0 - 59 之间"
            result = null
            return
        }
        if (hasPace && (paceMinutes !in 0..999 || paceSeconds !in 0..59)) {
            error = "配速格式不正确，秒应在 0 - 59 之间"
            result = null
            return
        }

        val distanceKilometers = distance?.times(unit.kilometers)
        val calculated = when {
            distanceKilometers != null && hasTime && duration > 0 -> {
                PaceCalculator.paceFromDistanceAndTime(distanceKilometers, duration)
            }
            distanceKilometers != null && hasPace && pacePerUnit > 0 -> {
                PaceCalculator.timeFromDistanceAndPace(distanceKilometers, pacePerUnit.toDouble() * unit.kilometers)
            }
            hasTime && duration > 0 && hasPace && pacePerUnit > 0 -> {
                PaceCalculator.distanceFromTimeAndPace(duration, pacePerUnit.toDouble() * unit.kilometers)
            }
            else -> null
        }
        if (calculated == null) {
            error = "请填写两项有效数据：里程、运动时间或每单位配速"
            result = null
            return
        }
        result = calculated
        error = null
        populate(calculated, unit)
        onNotify("配速换算完成")
    }

    ToolScaffold(
        title = "配速换算",
        subtitle = "户外工具",
        onBack = onBack,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.DirectionsRun,
                contentDescription = null,
                tint = TrailColors.EmeraldBright,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.size(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("里程、时间与配速", color = TrailColors.TextPrimary, fontWeight = FontWeight.Bold)
                Text("填写其中两项，即可计算第三项", color = TrailColors.TextMuted, fontSize = 10.sp)
            }
            Badge(if (unit == DistanceUnit.KILOMETER) "跑步 · 公里" else "跑步 · 英里", TrailColors.EmeraldBright)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(TrailColors.Panel, RoundedCornerShape(16.dp))
                .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DistanceUnit.entries.forEach { option ->
                    UnitChip(
                        label = option.label,
                        selected = option == unit,
                        modifier = Modifier.weight(1f),
                    ) { switchUnit(option) }
                }
            }

            ToolSectionTitle("运动里程")
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(
                    value = distanceText,
                    onValueChange = { distanceText = it },
                    placeholder = "10",
                    modifier = Modifier.weight(1f),
                    label = unit.label,
                    isInteger = false,
                )
                Spacer(Modifier.size(8.dp))
                Text(unit.shortLabel, color = TrailColors.TextMuted, fontSize = 11.sp)
            }

            ToolSectionTitle("运动时间")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimeField(
                    label = "时",
                    value = hoursText,
                    onValueChange = { hoursText = it },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = "分",
                    value = minutesText,
                    onValueChange = { minutesText = it },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = "秒",
                    value = secondsText,
                    onValueChange = { secondsText = it },
                    modifier = Modifier.weight(1f),
                )
            }

            ToolSectionTitle("每${unit.label}配速")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TimeField(
                    label = "分",
                    value = paceMinutesText,
                    onValueChange = { paceMinutesText = it },
                    modifier = Modifier.weight(1f),
                )
                TimeField(
                    label = "秒",
                    value = paceSecondsText,
                    onValueChange = { paceSecondsText = it },
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("/${unit.shortLabel}", color = TrailColors.TextMuted, fontSize = 11.sp)
                }
            }

            error?.let {
                Text(
                    it,
                    color = TrailColors.Rose,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF4C0519).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = ::calculate,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = TrailColors.Emerald),
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("计算")
                }
                OutlinedButton(
                    onClick = ::clearAll,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(5.dp))
                    Text("重置")
                }
            }
        }

        val current = result
        if (current != null) {
            ResultSummary(current, unit)
            SplitTable(current, unit)
        } else {
            SectionLabel("计算结果")
            Text(
                "完成计算后会显示预估时间、配速和分段时间表。",
                color = TrailColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TrailColors.Panel.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
            )
        }
    }
}

@Composable
private fun ResultSummary(result: PaceResult, unit: DistanceUnit) {
    val distance = result.distanceKilometers / unit.kilometers
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF022C22).copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .border(1.dp, TrailColors.Emerald.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = TrailColors.EmeraldBright, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("换算结果", color = TrailColors.EmeraldBright, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("每${unit.label}配速", color = TrailColors.TextMuted, fontSize = 9.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResultCell(
                "距离",
                String.format(Locale.US, "%.2f %s", distance, unit.shortLabel),
                Modifier.weight(1f),
            )
            ResultCell("时间", PaceCalculator.formatDuration(result.durationSeconds), Modifier.weight(1f))
            ResultCell("配速", PaceCalculator.formatPace(result.paceSecondsPerKilometer, unit), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ResultCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(TrailColors.Background.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 9.dp),
    ) {
        Text(label, color = TrailColors.TextMuted, fontSize = 9.sp)
        Spacer(Modifier.size(2.dp))
        Text(value, color = TrailColors.EmeraldBright, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun SplitTable(result: PaceResult, unit: DistanceUnit) {
    val rows = PaceCalculator.splitRows(result, unit)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TrailColors.Panel, RoundedCornerShape(16.dp))
            .border(1.dp, TrailColors.Border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp)) {
            Text("距离 / ${unit.label}", color = TrailColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text("累计时间", color = TrailColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
        rows.forEachIndexed { index, (distance, time) ->
            val label = if (distance == 1.0 || distance % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f %s", distance, unit.shortLabel)
            } else {
                String.format(Locale.US, "%.2f %s", distance, unit.shortLabel)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (index % 2 == 0) TrailColors.Emerald.copy(alpha = 0.09f) else Color.Transparent,
                        RoundedCornerShape(7.dp),
                    )
                    .padding(horizontal = 8.dp, vertical = 7.dp),
            ) {
                Text(label, color = TrailColors.TextSecondary, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(time, color = TrailColors.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (PaceCalculator.splitCount(result, unit) > rows.size) {
            Text(
                "分段表最多显示 50 项，配速与总时间仍按完整里程计算。",
                color = TrailColors.TextMuted,
                fontSize = 9.sp,
            )
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    label: String,
    modifier: Modifier = Modifier,
    isInteger: Boolean,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        label = { Text(label, fontSize = 10.sp) },
        placeholder = { Text(placeholder, color = TrailColors.TextMuted, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isInteger) KeyboardType.Number else KeyboardType.Decimal,
        ),
        colors = paceFieldColors(),
    )
}

@Composable
private fun TimeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NumberField(
        value = value,
        onValueChange = onValueChange,
        placeholder = "0",
        label = label,
        modifier = modifier,
        isInteger = true,
    )
}

@Composable
private fun UnitChip(
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
private fun paceFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TrailColors.Emerald,
    unfocusedBorderColor = TrailColors.Border,
    focusedContainerColor = TrailColors.Background.copy(alpha = 0.7f),
    unfocusedContainerColor = TrailColors.Background.copy(alpha = 0.7f),
    focusedTextColor = TrailColors.TextPrimary,
    unfocusedTextColor = TrailColors.TextPrimary,
    cursorColor = TrailColors.Emerald,
    focusedLabelColor = TrailColors.EmeraldBright,
    unfocusedLabelColor = TrailColors.TextMuted,
)
