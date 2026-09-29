package com.schiwy.galeriesync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PrimaryLight = Color(0xFF1268FF)
private val PrimaryDark = Color(0xFF5B9CFF)

private val LightColors = lightColorScheme(
    primary = PrimaryLight,
    secondary = Color(0xFF00A971)
)
private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    secondary = Color(0xFF2ED991)
)

@Composable
fun GalerieSyncTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
