package app.dmgram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dmgram.R
import app.dmgram.ui.theme.BadgeRed
import app.dmgram.ui.theme.wordmarkStyle
import app.dmgram.ui.theme.Dimens

/** Instagram's home header layout: wordmark left, actions right (+ only when enabled). The wordmark opens About. */
@Composable
fun HomeHeader(
    onTitleClick: () -> Unit,
    onSearch: () -> Unit,
    onActivity: () -> Unit,
    activity: Boolean,
    onCreate: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.headerHeight)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            color = MaterialTheme.colorScheme.onBackground,
            style = wordmarkStyle(26.sp),
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onTitleClick,
            ),
        )
        Spacer(modifier = Modifier.weight(1f))
        if (onCreate != null) {
            HeaderAction(Glyphs.Plus, stringResource(R.string.header_create), onCreate)
            Spacer(modifier = Modifier.width(4.dp))
        }
        HeaderAction(Glyphs.Search, stringResource(R.string.header_search), onSearch)
        Spacer(modifier = Modifier.width(4.dp))
        HeaderAction(
            icon = Glyphs.Heart,
            label = stringResource(if (activity) R.string.header_activity_new else R.string.header_activity),
            onClick = onActivity,
            dot = activity,
        )
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, label: String, onClick: () -> Unit, dot: Boolean = false) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(Dimens.tabIcon),
        )
        // Instagram's new-activity dot sits just below the heart, in the DM badge red.
        if (dot) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = Dimens.tabIcon / 2 + 4.dp)
                    .size(8.dp)
                    .background(BadgeRed, CircleShape),
            )
        }
    }
}
