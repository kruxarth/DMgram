package app.dmgram.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** A 2 dp line in the text color, like a quiet browser progress bar. Warm tabs never show it. */
@Composable
fun LoadingOverlay(progress: Int, ready: Boolean) {
    val loading = !ready && progress < 100
    AnimatedVisibility(visible = loading, enter = fadeIn(), exit = fadeOut()) {
        val color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
        val modifier = Modifier.fillMaxWidth().height(2.dp)
        if (progress <= 0) {
            LinearProgressIndicator(
                modifier = modifier,
                color = color,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
            )
        } else {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0, 100) / 100f },
                modifier = modifier,
                color = color,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Butt,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}
