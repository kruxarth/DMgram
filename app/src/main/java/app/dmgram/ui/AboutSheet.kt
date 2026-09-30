package app.dmgram.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.dmgram.R
import app.dmgram.ui.theme.Dimens

data class AboutState(
    val versionName: String,
    val versionCode: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(state: AboutState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(Dimens.screenPadding)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.about_version, state.versionName),
                modifier = Modifier.padding(top = Dimens.stackGap),
            )
        }
    }
}
