package app.dmgram.nav

import app.dmgram.tabs.Tab

sealed class NavAction {
    data object Allow : NavAction()
    data class Block(val reason: String) : NavAction()
    data class Switch(val tab: Tab) : NavAction()
    data class External(val url: String) : NavAction()
    data class System(val url: String) : NavAction()
    data object Ignore : NavAction()

    val name: String
        get() = when (this) {
            Allow -> "ALLOW"
            is Block -> "BLOCK"
            is Switch -> "SWITCH"
            is External -> "EXTERNAL"
            is System -> "SYSTEM"
            Ignore -> "IGNORE"
        }
}

data class NavDecision(
    val route: RouteClass,
    val action: NavAction,
    val url: String,
)

fun decide(tab: Tab, rawUrl: String, rules: CompiledRules): NavDecision {
    val classified = classify(rawUrl, rules)
    val action = actionFor(tab, classified)
    return NavDecision(classified.route, action, classified.url)
}

private fun actionFor(tab: Tab, classified: Classified): NavAction = when (classified.route) {
    RouteClass.EXPLORE_BLOCKED -> NavAction.Block("explore")
    RouteClass.REELS_FEED -> NavAction.Block("reels")
    RouteClass.HOME_FEED ->
        if (tab == Tab.HOME) NavAction.Allow else NavAction.Switch(Tab.HOME)
    RouteClass.DIRECT_INBOX ->
        if (tab == Tab.DMS) NavAction.Allow else NavAction.Switch(Tab.DMS)
    RouteClass.EXTERNAL -> when (classified.scheme) {
        "mailto", "tel" -> NavAction.System(classified.url)
        "http", "https" -> NavAction.External(classified.url)
        else -> NavAction.Ignore
    }
    else -> NavAction.Allow
}
