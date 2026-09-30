package app.dmgram.nav

import app.dmgram.tabs.Surface
import app.dmgram.tabs.Tab

sealed class NavAction {
    data object Allow : NavAction()
    data class Block(val reason: String) : NavAction()
    data class Switch(val tab: Tab) : NavAction()
    data class External(val url: String) : NavAction()
    data class System(val url: String) : NavAction()
    data object Ignore : NavAction()

    /** Only from the stories strip: open this page in the Home feed WebView instead. */
    data object Feed : NavAction()

    val name: String
        get() = when (this) {
            Allow -> "ALLOW"
            is Block -> "BLOCK"
            is Switch -> "SWITCH"
            is External -> "EXTERNAL"
            is System -> "SYSTEM"
            Ignore -> "IGNORE"
            Feed -> "FEED"
        }
}

data class NavDecision(
    val route: RouteClass,
    val action: NavAction,
    val url: String,
)

fun decide(tab: Tab, rawUrl: String, rules: CompiledRules, surface: Surface = Surface.PAGE): NavDecision {
    val classified = classify(rawUrl, rules)
    val action = actionFor(tab, classified)
    if (surface == Surface.STORIES && action == NavAction.Allow && classified.route in STORIES_HANDOFF) {
        return NavDecision(classified.route, NavAction.Feed, classified.url)
    }
    return NavDecision(classified.route, action, classified.url)
}

/**
 * Pages the stories strip hands to the feed below it (a username tapped in the story viewer, "Send message").
 * Anything else, including routes we don't know such as Instagram's story composer, stays in the strip.
 */
private val STORIES_HANDOFF = setOf(
    RouteClass.PROFILE,
    RouteClass.POST,
    RouteClass.REEL_SINGLE,
    RouteClass.DIRECT_THREAD,
    RouteClass.SEARCH,
    RouteClass.ACTIVITY,
)

/**
 * Routes another app may open inside the logged-in WebView. MainActivity is exported, so any app can send
 * any URL: `/accounts/logout/` alone signed the user out. Everything else on Instagram opens in the browser.
 */
private val INCOMING_ROUTES = setOf(
    RouteClass.HOME_FEED,
    RouteClass.DIRECT_INBOX,
    RouteClass.DIRECT_THREAD,
    RouteClass.STORY,
    RouteClass.POST,
    RouteClass.REEL_SINGLE,
    RouteClass.SEARCH,
    RouteClass.ACTIVITY,
    RouteClass.PROFILE,
)

fun decideIncoming(rawUrl: String, rules: CompiledRules): NavDecision {
    val decision = decide(Tab.HOME, rawUrl, rules)
    val loadsInApp = decision.action == NavAction.Allow || decision.action is NavAction.Switch
    if (loadsInApp && decision.route !in INCOMING_ROUTES) {
        return decision.copy(action = NavAction.External(decision.url))
    }
    return decision
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
