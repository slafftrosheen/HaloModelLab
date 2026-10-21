package com.slafftrosheen.halomodellab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Simple dark Material 3 theme with system fonts. No dynamic color, no branded Halo styling:
 * the Halo brand book is explicitly out of scope for this experiment.
 */
private val LabDarkColors = darkColorScheme(
    primary = Color(0xFF8FB8FF),
    onPrimary = Color(0xFF00214A),
    primaryContainer = Color(0xFF1B3A63),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293042),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF222833),
    onSurfaceVariant = Color(0xFFC2C7D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    outline = Color(0xFF8B919C),
)

@Composable
fun LabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LabDarkColors,
        content = content,
    )
}
