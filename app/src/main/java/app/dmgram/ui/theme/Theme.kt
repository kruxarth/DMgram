package app.dmgram.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun DMGramTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme(dark),
        typography = DMGramTypography,
        shapes = DMGramShapes,
        content = content,
    )
}
