package com.trailguardian.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object TrailColors {
    val Background = Color(0xFF020617)
    val Panel = Color(0xFF0F172A)
    val PanelElevated = Color(0xFF1E293B)
    val Border = Color(0xFF334155)
    val Emerald = Color(0xFF10B981)
    val EmeraldBright = Color(0xFF34D399)
    val Sky = Color(0xFF38BDF8)
    val Amber = Color(0xFFF59E0B)
    val Rose = Color(0xFFEF4444)
    val Violet = Color(0xFF8B5CF6)
    val Blue = Color(0xFF3B82F6)
    val TextPrimary = Color(0xFFE2E8F0)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)
}

private val darkScheme = darkColorScheme(
    primary = TrailColors.Emerald,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = TrailColors.EmeraldBright,
    secondary = TrailColors.Sky,
    onSecondary = Color(0xFF082F49),
    tertiary = TrailColors.Amber,
    background = TrailColors.Background,
    onBackground = TrailColors.TextPrimary,
    surface = TrailColors.Panel,
    onSurface = TrailColors.TextPrimary,
    surfaceVariant = TrailColors.PanelElevated,
    onSurfaceVariant = TrailColors.TextSecondary,
    outline = TrailColors.Border,
    error = TrailColors.Rose,
    onError = Color.White,
)

private val trailShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

@Composable
fun TrailGuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkScheme,
        shapes = trailShapes,
        content = content,
    )
}
