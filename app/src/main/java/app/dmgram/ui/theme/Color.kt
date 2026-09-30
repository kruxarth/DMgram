package app.dmgram.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

// Values follow Instagram's own web tokens (--ig-primary-background etc.) so the
// native chrome and the page read as one surface. Keep values/colors.xml in sync.

private val LightColors = lightColorScheme(
    primary = Color(0xFF0095F6),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    onSurfaceVariant = Color(0xFF737373),
    surfaceTint = Color.Transparent,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEFEFEF),
    outline = Color(0xFFC7C7C7),
    outlineVariant = Color(0xFFDBDBDB),
    inverseSurface = Color(0xFF262626),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFFED4956),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4A5DF9),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFF0C1014),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF0C1014),
    onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFFA8A8A8),
    surfaceTint = Color.Transparent,
    surfaceContainerLow = Color(0xFF212328),
    surfaceContainer = Color(0xFF212328),
    surfaceContainerHigh = Color(0xFF212328),
    surfaceContainerHighest = Color(0xFF25292E),
    outline = Color(0xFF555555),
    outlineVariant = Color(0xFF262626),
    inverseSurface = Color(0xFF262626),
    inverseOnSurface = Color(0xFFF5F5F5),
    error = Color(0xFFFF3040),
)

/** Unread badge. Same red in both themes, like Instagram's. */
val BadgeRed = Color(0xFFFF3040)

internal fun colorScheme(dark: Boolean) = if (dark) DarkColors else LightColors

fun windowBackground(dark: Boolean): Int =
    (if (dark) DarkColors.background else LightColors.background).toArgb()
