package app.dmgram.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.dmgram.R
import app.dmgram.ui.theme.Dimens

enum class UpdateStatus {
    Checking,
    UpToDate,
    Available,
}

data class AboutState(
    val versionName: String,
    val versionCode: Int,
    val rulesVersion: Int,
    val status: UpdateStatus,
    val githubRepo: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    state: AboutState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onGitHub: () -> Unit,
    onReport: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(Dimens.screenPadding)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.about_version, state.versionName),
                modifier = Modifier.padding(top = Dimens.stackGap),
            )
            Text(
                text = stringResource(R.string.about_rules, state.rulesVersion),
                modifier = Modifier.padding(top = Dimens.stackGap),
            )
            when (state.status) {
                UpdateStatus.Checking -> Text(
                    text = stringResource(R.string.about_checking),
                    modifier = Modifier.padding(top = Dimens.stackGap),
                )
                UpdateStatus.UpToDate -> Text(
                    text = stringResource(R.string.about_up_to_date),
                    modifier = Modifier.padding(top = Dimens.stackGap),
                )
                UpdateStatus.Available -> Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.stackGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.about_update_available),
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onInstall) {
                        Text(stringResource(R.string.update_install))
                    }
                }
            }
            if (state.githubRepo.isNotBlank()) {
                TextButton(onClick = onGitHub) {
                    Text(stringResource(R.string.about_github))
                }
                TextButton(onClick = onReport) {
                    Text(stringResource(R.string.about_report))
                }
            }
        }
    }
}
