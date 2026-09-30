package app.dmgram.tabs

import androidx.compose.ui.graphics.ImageBitmap
import app.dmgram.ui.ErrorKind
import app.dmgram.update.UpdateInfo

data class FrameUi(
    val tab: Tab = Tab.HOME,
    val loggedIn: Boolean = false,
    val showTabBar: Boolean = false,
    val showHomeHeader: Boolean = false,
    val fullScreen: Boolean = false,
    val pullToRefresh: Boolean = false,
    val progress: Int = 0,
    val ready: Boolean = false,
    val unread: Int = 0,
    val avatar: ImageBitmap? = null,
    val refreshing: Boolean = false,
    val blockedVisible: Boolean = false,
    val aboutOpen: Boolean = false,
    val pageDark: Boolean? = null,
    val rulesVersion: Int = 1,
    val update: UpdateInfo? = null,
    val updateDismissed: Boolean = false,
    val checkingUpdate: Boolean = false,
    val loadError: ErrorKind? = null,
)
