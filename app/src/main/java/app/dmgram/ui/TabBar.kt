package app.dmgram.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dmgram.R
import app.dmgram.tabs.Tab
import app.dmgram.ui.theme.BadgeRed
import app.dmgram.ui.theme.Dimens

/** Instagram-style bar: icons only, outline → filled when selected, hairline on top. */
@Composable
fun TabBar(
    current: Tab,
    unread: Int,
    avatar: ImageBitmap?,
    onSelect: (Tab) -> Unit,
    onReselect: (Tab) -> Unit,
) {
    val view = LocalView.current
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth().height(Dimens.tabBarHeight)) {
            Tab.entries.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab == current,
                    unread = if (tab == Tab.DMS) unread else 0,
                    avatar = if (tab == Tab.PROFILE) avatar else null,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (tab == current) onReselect(tab) else onSelect(tab)
                    },
                )
            }
        }
    }
}

@Composable
private fun TabItem(
    tab: Tab,
    selected: Boolean,
    unread: Int,
    avatar: ImageBitmap?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(stiffness = 900f), label = "press")
    val label = stringResource(tab.label())
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.scale(scale)) {
            if (avatar != null) {
                Avatar(avatar, selected, label)
            } else {
                Icon(
                    imageVector = tab.icon(selected),
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(Dimens.tabIcon),
                )
            }
            if (unread > 0) {
                UnreadBadge(
                    count = unread,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 9.dp, y = (-6).dp),
                )
            }
        }
    }
}

/** Instagram shows your photo on the Profile tab; selected adds a ring in the text color. */
@Composable
private fun Avatar(image: ImageBitmap, selected: Boolean, label: String) {
    val ring = if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent
    Image(
        bitmap = image,
        contentDescription = label,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(Dimens.tabIcon + 2.dp)
            .border(2.dp, ring, CircleShape)
            .padding(3.dp)
            .clip(CircleShape),
    )
}

@Composable
private fun UnreadBadge(count: Int, modifier: Modifier) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(50))
            .padding(2.dp)
            .background(BadgeRed, RoundedCornerShape(50))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun Tab.icon(selected: Boolean): ImageVector = when (this) {
    Tab.HOME -> if (selected) Glyphs.HomeFilled else Glyphs.HomeOutline
    Tab.DMS -> if (selected) Glyphs.DirectFilled else Glyphs.DirectOutline
    Tab.PROFILE -> if (selected) Glyphs.PersonFilled else Glyphs.PersonOutline
}

private fun Tab.label(): Int = when (this) {
    Tab.HOME -> R.string.tab_home
    Tab.DMS -> R.string.tab_dms
    Tab.PROFILE -> R.string.tab_profile
}
