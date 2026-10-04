package org.onedroid.dataset.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1967D2),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondaryContainer = Color(0xFFE8F0FE),
    onSecondaryContainer = Color(0xFF1967D2),
    background = Color(0xFFE8EAED),
    onBackground = Color(0xFF202124),
    surface = Color.White,
    onSurface = Color(0xFF202124),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceContainerLow = Color.White,
    outline = Color(0xFFBDC1C6),
    outlineVariant = Color(0xFFDADCE0),
    error = Color(0xFFD93025),
    tertiary = Color(0xFFB06000),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondaryContainer = Color(0xFF1F3760),
    onSecondaryContainer = Color(0xFFA8C7FA),
    background = Color(0xFF17181A),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF26272A),
    onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceContainerLow = Color(0xFF26272A),
    outline = Color(0xFF5F6368),
    outlineVariant = Color(0xFF3C4043),
    error = Color(0xFFF28B82),
    tertiary = Color(0xFFFDD663),
)

/** Per-class accent colors, used for class dots and balance bars. */
private val ClassColors = listOf(
    Color(0xFFE8710A),
    Color(0xFFE52592),
    Color(0xFF9334E6),
    Color(0xFF1A73E8),
    Color(0xFF12B5CB),
    Color(0xFF1E8E3E),
    Color(0xFFF9AB00),
    Color(0xFFD93025),
)

fun classColor(index: Int): Color = ClassColors[index % ClassColors.size]

@Composable
fun DataDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
