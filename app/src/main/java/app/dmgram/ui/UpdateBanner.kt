package app.dmgram.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.dmgram.R
import app.dmgram.ui.theme.Dimens
import app.dmgram.update.UpdateInfo

@Composable
fun UpdateBanner(
    info: UpdateInfo,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = Dimens.stackGap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.stackGap),
        ) {
            Text(
                text = stringResource(R.string.update_available, info.version),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onInstall) {
                Text(stringResource(R.string.update_install))
            }
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_dismiss))
            }
        }
    }
}
