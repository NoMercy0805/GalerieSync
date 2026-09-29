package de.example.nasphotosync.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val NapsBackground = Color(0xFF0B0B0F)
val NapsSurface = Color(0xFF17171D)
val NapsSurfaceVariant = Color(0xFF222229)
val NapsPrimary = Color(0xFF3D8BFF)
val NapsOnSurfaceVariant = Color(0xFFA0A0AC)
val NapsSuccess = Color(0xFF33C778)
val NapsError = Color(0xFFFF5A5F)

private val DarkColors = darkColorScheme(
    background = NapsBackground,
    surface = NapsSurface,
    surfaceVariant = NapsSurfaceVariant,
    primary = NapsPrimary,
    onBackground = Color(0xFFF2F2F5),
    onSurface = Color(0xFFF2F2F5),
    onSurfaceVariant = NapsOnSurfaceVariant,
    error = NapsError
)

@Composable
fun NasPhotoSyncTheme(content: @Composable () -> Unit) {
    // Mockup ist durchgehend dunkel gehalten - bewusst kein Hell-Modus in
    // dieser Phase, um konsistent zum Design zu bleiben.
    MaterialTheme(colorScheme = DarkColors, content = content)
}
