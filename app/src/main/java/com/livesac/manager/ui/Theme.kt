package com.livesac.manager.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Fond clair + vert profond élégant.
private val LiveSacColors = lightColorScheme(
    primary = Color(0xFF0F5C4A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EEE6),
    onPrimaryContainer = Color(0xFF073B2F),
    secondary = Color(0xFF3F6B5E),
    onSecondary = Color.White,
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF16201D),
    surface = Color.White,
    onSurface = Color(0xFF16201D),
    surfaceVariant = Color(0xFFE6EDEA),
    onSurfaceVariant = Color(0xFF55625E),
    outline = Color(0xFF8A9893),
    outlineVariant = Color(0xFFD5DEDA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFE6EDEA)
)

private val LiveSacShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp)
)

@Composable
fun LiveSacTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LiveSacColors,
        shapes = LiveSacShapes,
        content = content
    )
}
