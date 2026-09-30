package app.dmgram.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.dmgram.R
import app.dmgram.ui.theme.Dimens

enum class ErrorKind {
    WEBVIEW_UPDATE,
    OFFLINE,
    LOAD,
}

@Composable
fun ErrorState(kind: ErrorKind, onRetry: () -> Unit) {
    val message = when (kind) {
        ErrorKind.WEBVIEW_UPDATE -> stringResource(R.string.webview_update_required)
        ErrorKind.OFFLINE -> stringResource(R.string.error_offline)
        ErrorKind.LOAD -> stringResource(R.string.error_load)
    }
    val action = when (kind) {
        ErrorKind.WEBVIEW_UPDATE -> stringResource(R.string.webview_update_action)
        ErrorKind.OFFLINE, ErrorKind.LOAD -> stringResource(R.string.error_retry)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.stackGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            modifier = Modifier.height(36.dp),
        ) {
            Text(action, fontWeight = FontWeight.SemiBold)
        }
    }
}
