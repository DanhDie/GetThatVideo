package com.gtv.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta inspirada no YouTube combinada com tons modernos de estúdio de mídia
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF4E55),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF5C1116),
    onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = Color(0xFF4FD8EB),
    onSecondary = Color(0xFF00363D),
    secondaryContainer = Color(0xFF004F58),
    onSecondaryContainer = Color(0xFF97F0FF),
    tertiary = Color(0xFF6DD58C),
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFE6E8EE),
    surface = Color(0xFF181B24),
    onSurface = Color(0xFFE6E8EE),
    surfaceVariant = Color(0xFF232734),
    onSurfaceVariant = Color(0xFFC4C7D4),
    error = Color(0xFFFFB4AB)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFD91E2A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD8),
    onPrimaryContainer = Color(0xFF410005),
    secondary = Color(0xFF006874),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF97F0FF),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary = Color(0xFF146C2E),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF191C22),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C22),
    surfaceVariant = Color(0xFFE8EAF2),
    onSurfaceVariant = Color(0xFF444752)
)

@Composable
fun GetThatVideoTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
