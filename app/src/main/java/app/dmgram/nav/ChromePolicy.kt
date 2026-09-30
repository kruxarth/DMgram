package app.dmgram.nav

import app.dmgram.tabs.Tab

data class Chrome(
    val showTabBar: Boolean,
    val showHomeHeader: Boolean,
    val fullScreen: Boolean,
    val pullToRefresh: Boolean,
)

fun chromeFor(tab: Tab, route: RouteClass, atRoot: Boolean): Chrome = when (route) {
    RouteClass.HOME_FEED ->
        if (tab == Tab.HOME && atRoot) {
            Chrome(showTabBar = true, showHomeHeader = true, fullScreen = false, pullToRefresh = true)
        } else {
            Chrome(showTabBar = true, showHomeHeader = false, fullScreen = false, pullToRefresh = false)
        }
    RouteClass.DIRECT_INBOX ->
        Chrome(showTabBar = true, showHomeHeader = false, fullScreen = false, pullToRefresh = true)
    RouteClass.DIRECT_THREAD ->
        Chrome(showTabBar = false, showHomeHeader = false, fullScreen = false, pullToRefresh = false)
    RouteClass.STORY, RouteClass.REEL_SINGLE ->
        Chrome(showTabBar = false, showHomeHeader = false, fullScreen = true, pullToRefresh = false)
    RouteClass.AUTH ->
        Chrome(showTabBar = false, showHomeHeader = false, fullScreen = false, pullToRefresh = false)
    RouteClass.PROFILE ->
        if (tab == Tab.PROFILE && atRoot) {
            Chrome(showTabBar = true, showHomeHeader = false, fullScreen = false, pullToRefresh = true)
        } else {
            Chrome(showTabBar = true, showHomeHeader = false, fullScreen = false, pullToRefresh = false)
        }
    else ->
        Chrome(showTabBar = true, showHomeHeader = false, fullScreen = false, pullToRefresh = false)
}
