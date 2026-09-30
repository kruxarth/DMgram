package app.dmgram.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.dmgram.R
import app.dmgram.tabs.Tab

@Composable
fun TabBar(
    current: Tab,
    unread: Int,
    onSelect: (Tab) -> Unit,
    onReselect: (Tab) -> Unit,
) {
    NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { if (tab == current) onReselect(tab) else onSelect(tab) },
                icon = {
                    if (tab == Tab.DMS && unread > 0) {
                        BadgedBox(badge = { Badge { Text(unread.coerceAtMost(99).toString()) } }) {
                            Icon(tab.icon(), contentDescription = null)
                        }
                    } else {
                        Icon(tab.icon(), contentDescription = null)
                    }
                },
                label = { Text(stringResource(tab.label())) },
            )
        }
    }
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.HOME -> Icons.Filled.Home
    Tab.DMS -> Icons.Filled.Email
    Tab.PROFILE -> Icons.Filled.Person
}

private fun Tab.label(): Int = when (this) {
    Tab.HOME -> R.string.tab_home
    Tab.DMS -> R.string.tab_dms
    Tab.PROFILE -> R.string.tab_profile
}
