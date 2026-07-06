package com.virtualworkspace.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E5A8A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD2E4F7),
    secondary = Color(0xFFE8A83E),
    surface = Color(0xFFFAFAFC),
    background = Color(0xFFF4F5F8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FC1EC),
    onPrimary = Color(0xFF0C2C46),
    primaryContainer = Color(0xFF1E4260),
    secondary = Color(0xFFE8A83E),
    surface = Color(0xFF15181D),
    background = Color(0xFF101318)
)

@Composable
fun VirtualWorkspaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
