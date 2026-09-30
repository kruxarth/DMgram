package app.dmgram.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        Text(message)
        Button(onClick = onRetry) {
            Text(action)
        }
    }
}
