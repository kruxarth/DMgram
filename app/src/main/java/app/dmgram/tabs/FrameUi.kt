package app.dmgram.tabs

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
    val refreshing: Boolean = false,
    val blockedVisible: Boolean = false,
    val aboutOpen: Boolean = false,
    val pageDark: Boolean? = null,
)
