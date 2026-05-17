package com.geofield.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ColorScheme = darkColorScheme(
    primary          = Color(0xFF00D084),
    onPrimary        = Color(0xFF000000),
    secondary        = Color(0xFF0090FF),
    onSecondary      = Color(0xFFFFFFFF),
    background       = Color(0xFF0F1117),
    onBackground     = Color(0xFFE8EAF2),
    surface          = Color(0xFF181C27),
    onSurface        = Color(0xFFE8EAF2),
    surfaceVariant   = Color(0xFF1F2436),
    onSurfaceVariant = Color(0xFF9AA3BF),
    outline          = Color(0xFF2A3045),
    error            = Color(0xFFFF4757),
    onError          = Color(0xFFFFFFFF),
)

@Composable
fun GeoFieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        content = content
    )
}
