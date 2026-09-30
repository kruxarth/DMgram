package app.dmgram.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val LightColors = lightColorScheme(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
)

private val DarkColors = darkColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF000000),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
)

internal fun colorScheme(dark: Boolean) = if (dark) DarkColors else LightColors

fun windowBackground(dark: Boolean): Int =
    (if (dark) DarkColors.background else LightColors.background).toArgb()
