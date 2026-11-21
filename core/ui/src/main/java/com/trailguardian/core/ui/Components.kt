package com.trailguardian.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TrailCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(TrailColors.Panel, RoundedCornerShape(16.dp))
            .border(1.dp, TrailColors.Border.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, color: Color = TrailColors.Emerald) {
    Text(
        text = text,
        color = color.copy(alpha = 0.8f),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
    )
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (subtitle != null) SectionLabel(subtitle)
        Text(
            text = title,
            color = TrailColors.EmeraldBright,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun Badge(text: String, color: Color = TrailColors.Emerald) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Dot(color: Color, size: Int = 10) {
    Box(
        Modifier
            .size(size.dp)
            .background(color, RoundedCornerShape(50)),
    )
}

@Composable
fun TrailConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "确认",
    dismissLabel: String = "取消",
    dangerous: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TrailColors.Panel,
        title = {
            Text(title, color = if (dangerous) TrailColors.Rose else TrailColors.EmeraldBright)
        },
        text = {
            Text(text, color = TrailColors.TextSecondary, fontSize = 13.sp)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (dangerous) TrailColors.Rose else TrailColors.Emerald)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel, color = TrailColors.TextSecondary)
            }
        },
    )
}

@Composable
fun KeyValueRow(label: String, value: String, valueColor: Color = TrailColors.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TrailColors.TextMuted, fontSize = 12.sp)
        Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(icon: String, title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(icon, fontSize = 34.sp)
        Text(title, color = TrailColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(
            description,
            color = TrailColors.TextMuted,
            fontSize = 12.sp,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
