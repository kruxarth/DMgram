package app.dmgram.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun DMGramTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme(isSystemInDarkTheme()),
        typography = DMGramTypography,
        shapes = DMGramShapes,
        content = content,
    )
}
