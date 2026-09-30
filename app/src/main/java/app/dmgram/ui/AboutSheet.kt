package app.dmgram.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dmgram.R

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

/** Calm, Instagram-settings-like sheet: identity on top, a status line, then plain rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSheet(
    state: AboutState,
    onDismiss: () -> Unit,
    onInstall: () -> Unit,
    onGitHub: () -> Unit,
    onReport: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(bottom = 8.dp)) {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(R.string.about_version, state.versionName) + " · " +
                        stringResource(R.string.about_rules, state.rulesVersion),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val status = when (state.status) {
                        UpdateStatus.Checking -> R.string.about_checking
                        UpdateStatus.UpToDate -> R.string.about_up_to_date
                        UpdateStatus.Available -> R.string.about_update_available
                    }
                    Text(
                        text = stringResource(status),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (state.status == UpdateStatus.Available) {
                        Button(
                            onClick = onInstall,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.height(34.dp),
                        ) {
                            Text(stringResource(R.string.update_install), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (state.githubRepo.isNotBlank()) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = colors.outlineVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                SheetRow(stringResource(R.string.about_github), onGitHub)
                SheetRow(stringResource(R.string.about_report), onReport)
            }
        }
    }
}

@Composable
private fun SheetRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}
