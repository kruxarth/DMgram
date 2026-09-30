package app.dmgram.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LoadingOverlay(progress: Int, ready: Boolean) {
    if (ready || progress >= 100) return
    if (progress <= 0) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
