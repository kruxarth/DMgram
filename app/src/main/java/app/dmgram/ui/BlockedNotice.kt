package app.dmgram.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.dmgram.R
import app.dmgram.ui.theme.Dimens

data class BlockedNoticeState(val visible: Boolean)

@Composable
fun BlockedNotice(state: BlockedNoticeState) {
    AnimatedVisibility(visible = state.visible) {
        Surface(
            modifier = Modifier.padding(Dimens.screenPadding),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = Dimens.noticeElevation,
        ) {
            Text(
                text = stringResource(R.string.blocked_notice),
                modifier = Modifier.padding(Dimens.stackGap),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
