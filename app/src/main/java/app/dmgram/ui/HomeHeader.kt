package app.dmgram.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.dmgram.R
import app.dmgram.ui.theme.Dimens

@Composable
fun HomeHeader(
    onTitleClick: () -> Unit,
    onSearch: () -> Unit,
    onActivity: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.headerHeight)
            .padding(horizontal = Dimens.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onTitleClick),
        )
        TextButton(onClick = onSearch) {
            Text(stringResource(R.string.header_search))
        }
        TextButton(onClick = onActivity) {
            Text(stringResource(R.string.header_activity))
        }
    }
}
