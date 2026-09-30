package app.dmgram.tabs

import android.content.ActivityNotFoundException
import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import app.dmgram.BuildConfig
import app.dmgram.DMGramApp
import app.dmgram.nav.Chrome
import app.dmgram.nav.CompiledRules
import app.dmgram.nav.NavAction
import app.dmgram.nav.RouteClass
import app.dmgram.nav.Rules
import app.dmgram.nav.chromeFor
import app.dmgram.nav.decide
import app.dmgram.nav.normalizePath
import app.dmgram.rules.RulesRepository
import app.dmgram.ui.ErrorKind
import app.dmgram.update.AboutLinks
import app.dmgram.update.UpdateChecker
import app.dmgram.update.UpdateInfo
import app.dmgram.ui.theme.windowBackground
import app.dmgram.web.Bridge
import app.dmgram.web.BridgeEvent
import app.dmgram.web.DMGramChromeClient
import app.dmgram.web.DMGramWebViewClient
import app.dmgram.web.InjectBundle
import app.dmgram.web.LoginState
import app.dmgram.web.WebViewFactory
import app.dmgram.web.jsQuote
import java.net.URI
import java.net.URISyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TabController(
    private val activity: ComponentActivity,
    private val container: FrameLayout,
    private val media: app.dmgram.web.MediaRequests,
    private val onHomeReady: () -> Unit,
    private val onChanged: () -> Unit,
) {
    private val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val jobs = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val rulesRepository = RulesRepository(activity, prefs)
    private val updateChecker = UpdateChecker(prefs)
    private val main = Handler(Looper.getMainLooper())
    private val slots = Tab.entries.associateWith { Slot(it) }
    private var rulesJson: String = "{}"
    private var compiled = CompiledRules(Rules())
    private var current = Tab.HOME
    private var loggedIn = false
    private var username: String? = null
    private var homeUnread: Int? = null
    private var avatar: ImageBitmap? = null
    private var titleUnread: Int? = null
    private var refreshing = false
    private var blockedVisible = false
    private var aboutOpen = false
    private var pageDark: Boolean? = null
    private var keyboardOpen = false
    private var remoteJob: Job? = null
    private var rulesVersion = 1
    private var updateInfo: UpdateInfo? = null
    private var updateDismissed = false
    private var checkingUpdate = false
    private var foreground = true
    private var homeLoaded = false
    private var blockedAt = 0L
    private var hideNotice: Runnable? = null
    private val titlePattern = Regex("""^\((\d+)\)""")
    private val usernamePattern = Regex("""[A-Za-z0-9._]{1,30}""")

    fun start(incoming: String?) {
        username = prefs.getString(KEY_USERNAME, null)?.takeIf { usernamePattern.matches(it) }
        rulesJson = rulesRepository.bundled()
        val parsed = Rules.parse(rulesJson)
        compiled = CompiledRules(parsed)
        rulesVersion = parsed.version
        loggedIn = LoginState.isLoggedIn()
        create(Tab.HOME, load = true)
        if (!incoming.isNullOrBlank()) openIncoming(incoming)
        publish()
    }

    fun snapshot(): FrameUi {
        val slot = slots.getValue(current)
        val chrome = visualChrome()
        return FrameUi(
            tab = current,
            loggedIn = loggedIn,
            showTabBar = chrome.showTabBar,
            showHomeHeader = chrome.showHomeHeader,
            fullScreen = chrome.fullScreen,
            pullToRefresh = chrome.pullToRefresh,
            progress = slot.progress,
            ready = slot.ready,
            unread = homeUnread ?: titleUnread ?: 0,
            avatar = avatar,
            refreshing = refreshing,
            blockedVisible = blockedVisible,
            aboutOpen = aboutOpen,
            pageDark = pageDark,
            rulesVersion = rulesVersion,
            update = updateInfo,
            updateDismissed = updateDismissed,
            checkingUpdate = checkingUpdate,
            loadError = slot.loadError,
        )
    }

    fun select(tab: Tab) {
        if (!loggedIn && tab != Tab.HOME) return
        if (tab == Tab.PROFILE && username == null) {
            current = tab
            publish()
            return
        }
        if (slots.getValue(tab).web == null) create(tab, load = true)
        if (tab != current) {
            eval(slots.getValue(current).web, "pauseMedia()")
        }
        current = tab
        reveal(tab)
        publish()
    }

    fun reselect(tab: Tab) {
        if (tab != current) {
            select(tab)
            return
        }
        val slot = slots.getValue(tab)
        val web = slot.web ?: return
        if (!isRoot(tab, slot.url)) {
            val steps = stepsBackToRoot(web, tab)
            if (steps != null) {
                eval(web, "go(${-steps})")
            } else {
                val path = tab.rootPath(username)
                if (path != null) eval(web, "navigate(${jsQuote(path)})")
            }
            return
        }
        if (!slot.atTop) {
            eval(web, "scrollToTop()")
        } else {
            slot.ready = false
            refreshing = true
            web.reload()
            publish()
        }
    }

    fun onBack() {
        val slot = slots.getValue(current)
        if (slot.chrome?.isFullscreen() == true) {
            slot.chrome?.exitFullscreen()
            return
        }
        if (aboutOpen) {
            aboutOpen = false
            publish()
            return
        }
        if (slot.reelLocked) {
            val web = slot.web
            if (web != null) dismissReel(web, slot) else continueBack(slot)
            return
        }
        continueBack(slot)
    }

    private fun continueBack(slot: Slot) {
        val web = slot.web
        if (web != null && stepBack(web, slot)) return
        if (current != Tab.HOME) {
            select(Tab.HOME)
            return
        }
        activity.moveTaskToBack(true)
    }

    fun openAbout() {
        aboutOpen = true
        publish()
    }

    fun dismissAbout() {
        if (!aboutOpen) return
        aboutOpen = false
        publish()
    }

    fun dismissUpdate() {
        val version = updateInfo?.version ?: return
        prefs.edit().putString(KEY_UPDATE_DISMISSED, version).apply()
        updateDismissed = true
        publish()
    }

    fun installUpdate() {
        val info = updateInfo ?: return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.apkUrl)))
        } catch (error: ActivityNotFoundException) {
            Log.e(DMGramApp.TAG, "No handler for the update ${info.version}", error)
        }
    }

    fun openGitHub() {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) return
        openExternal(AboutLinks.repoUrl(repo))
    }

    fun openReport() {
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) return
        val body = AboutLinks.reportBody(
            appVersion = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            rulesVersion = rulesVersion,
            androidVersion = Build.VERSION.RELEASE ?: "",
            webViewVersion = webViewVersion(),
        )
        openExternal(AboutLinks.reportUrl(repo, body))
    }

    fun retryLoad() {
        val slot = slots.getValue(current)
        slot.loadError = null
        val web = slot.web
        if (web == null) create(current, load = true) else web.reload()
        publish()
    }

    fun openSearch() {
        eval(slots.getValue(current).web, "openSearch()")
    }

    fun openActivity() {
        eval(slots.getValue(current).web, "navigate(${jsQuote("/notifications/")})")
    }

    fun setKeyboardOpen(open: Boolean) {
        if (keyboardOpen == open) return
        keyboardOpen = open
        publish()
    }

    /** Instagram-like refresh spinner: text-colored arrow on the elevated surface, not Material purple. */
    private fun styleSwipe(swipe: SwipeRefreshLayout, dark: Boolean) {
        swipe.setColorSchemeColors(if (dark) 0xFFF5F5F5.toInt() else 0xFF262626.toInt())
        swipe.setProgressBackgroundColorSchemeColor(if (dark) 0xFF212328.toInt() else 0xFFFFFFFF.toInt())
    }

    fun setSystemDark(dark: Boolean) {
        val color = windowBackground(dark)
        for (slot in slots.values) {
            slot.web?.setBackgroundColor(color)
            slot.swipe?.let { styleSwipe(it, dark) }
            eval(slot.web, "setSystemDark($dark)")
        }
    }

    fun openIncoming(raw: String) {
        val decision = decide(Tab.HOME, raw, compiled)
        when (decision.route) {
            RouteClass.DIRECT_INBOX, RouteClass.DIRECT_THREAD -> {
                if (!loggedIn) {
                    slots.getValue(Tab.HOME).web?.loadUrl(decision.url)
                    select(Tab.HOME)
                    return
                }
                if (slots.getValue(Tab.DMS).web == null) create(Tab.DMS, load = decision.route == RouteClass.DIRECT_INBOX)
                select(Tab.DMS)
                if (decision.route == RouteClass.DIRECT_THREAD) {
                    slots.getValue(Tab.DMS).web?.loadUrl(decision.url)
                }
            }
            // MainActivity is exported: any app can send any URL here, so only Instagram routes load in a WebView.
            else -> when (val action = decision.action) {
                is NavAction.Block -> {
                    select(Tab.HOME)
                    showBlocked()
                }
                is NavAction.External -> openExternal(action.url)
                is NavAction.System -> openSystem(action.url)
                NavAction.Ignore -> Unit
                NavAction.Allow, is NavAction.Switch -> {
                    select(Tab.HOME)
                    slots.getValue(Tab.HOME).web?.loadUrl(decision.url)
                }
            }
        }
    }

    fun onHostStart() {
        foreground = true
        try {
            slots.values.firstNotNullOfOrNull { it.web }?.resumeTimers()
        } catch (error: RuntimeException) {
            Log.e(DMGramApp.TAG, "resumeTimers failed", error)
        }
        for (slot in slots.values) {
            try {
                slot.web?.onResume()
            } catch (error: RuntimeException) {
                Log.e(DMGramApp.TAG, "WebView onResume failed", error)
            }
        }
        refreshLogin()
        refreshRemote()
    }

    fun onHostStop() {
        foreground = false
        remoteJob?.cancel()
        for (slot in slots.values) {
            try {
                slot.web?.onPause()
            } catch (error: RuntimeException) {
                Log.e(DMGramApp.TAG, "WebView onPause failed", error)
            }
        }
        try {
            slots.values.firstNotNullOfOrNull { it.web }?.pauseTimers()
        } catch (error: RuntimeException) {
            Log.e(DMGramApp.TAG, "pauseTimers failed", error)
        }
    }

    fun onTrim() {
        Log.i(DMGramApp.TAG, "Trimming WebViews current=$current")
        if (current != Tab.PROFILE) destroy(Tab.PROFILE)
        if (current != Tab.DMS) destroy(Tab.DMS)
        publish()
    }

    fun destroyAll() {
        jobs.cancel()
        hideNotice?.let { main.removeCallbacks(it) }
        for (tab in Tab.entries) destroy(tab)
    }

    fun applyRules(json: String) {
        rulesJson = json
        compiled = CompiledRules(Rules.parse(json))
        for (slot in slots.values) {
            val web = slot.web ?: continue
            try {
                slot.script?.remove()
            } catch (error: RuntimeException) {
                Log.e(DMGramApp.TAG, "Could not remove the document-start script", error)
            }
            slot.script = InjectBundle.install(activity, web, slot.tab, rulesJson)
            eval(web, "setConfig(${jsQuote(InjectBundle.configJson(slot.tab, rulesJson))})")
        }
    }

    private fun visualChrome(): Chrome {
        val slot = slots.getValue(current)
        if (!loggedIn) return Chrome(false, false, false, false)
        if (slot.fullscreenVideo || slot.reelLocked) return Chrome(false, false, true, false)
        val url = slot.url.ifEmpty { slot.tab.rootUrl(username).orEmpty() }
        var chrome = chromeFor(current, slot.route, isRoot(current, url))
        if (keyboardOpen) chrome = chrome.copy(showTabBar = false)
        return chrome
    }

    private fun create(tab: Tab, load: Boolean) {
        val slot = slots.getValue(tab)
        if (slot.web != null) return
        val root = tab.rootUrl(username) ?: return
        val dark = isDark()
        val web = WebView(activity)
        web.setBackgroundColor(windowBackground(dark))
        // Native apps don't show a scroll thumb or an edge glow on their feeds.
        web.isVerticalScrollBarEnabled = false
        web.isHorizontalScrollBarEnabled = false
        web.overScrollMode = View.OVER_SCROLL_NEVER
        web.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        WebViewFactory.configure(web)
        web.setDownloadListener { url, _, _, _, _ ->
            Log.i(DMGramApp.TAG, "Download opened in a Custom Tab")
            openExternal(url)
        }
        val chrome = DMGramChromeClient(
            host = container,
            files = media,
            onFullscreenChanged = { fullscreen ->
                slot.fullscreenVideo = fullscreen
                publish()
            },
            onProgress = { progress -> onProgress(slot, progress) },
            onTitle = { title -> onTitle(slot, title) },
        )
        web.webChromeClient = chrome
        web.webViewClient = DMGramWebViewClient(
            onRendererGone = { onRendererGone(web) },
            onOverrideUrl = { url -> onOverride(slot, url) },
            onPageFinished = { url -> onFinished(slot, url) },
            onPageStarted = {
                if (slot.loadError != null) {
                    slot.loadError = null
                    publish()
                }
            },
            onMainFrameError = { offline ->
                slot.loadError = if (offline) ErrorKind.OFFLINE else ErrorKind.LOAD
                publish()
            },
        )
        Bridge.attach(web) { event ->
            activity.runOnUiThread { onBridge(slot, event) }
        }
        val script = InjectBundle.install(activity, web, tab, rulesJson)
        val swipe = SwipeRefreshLayout(activity)
        swipe.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        swipe.addView(web)
        styleSwipe(swipe, dark)
        swipe.setOnRefreshListener { onPull(slot) }
        swipe.setOnChildScrollUpCallback { _, _ -> !slot.atTop }
        container.addView(swipe)
        slot.web = web
        slot.swipe = swipe
        slot.chrome = chrome
        slot.script = script
        slot.url = root
        slot.route = when (tab) {
            Tab.HOME -> RouteClass.HOME_FEED
            Tab.DMS -> RouteClass.DIRECT_INBOX
            Tab.PROFILE -> RouteClass.PROFILE
        }
        reveal(current)
        if (load) web.loadUrl(root)
        Log.i(DMGramApp.TAG, "Created ${tab.name} WebView")
    }

    private fun destroy(tab: Tab) {
        val slot = slots.getValue(tab)
        val web = slot.web ?: return
        try {
            slot.script?.remove()
        } catch (error: RuntimeException) {
            Log.e(DMGramApp.TAG, "Could not remove the document-start script", error)
        }
        slot.script = null
        slot.chrome?.exitFullscreen()
        slot.chrome = null
        val swipe = slot.swipe
        slot.web = null
        slot.swipe = null
        if (swipe != null) container.removeView(swipe)
        web.destroy()
        slot.ready = false
        slot.progress = 0
        slot.reelLocked = false
        slot.fullscreenVideo = false
        slot.canGoBack = false
        Log.i(DMGramApp.TAG, "Destroyed ${tab.name} WebView")
    }

    private fun reveal(tab: Tab) {
        for (slot in slots.values) {
            val visible = slot.tab == tab && slot.swipe != null
            slot.swipe?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
            if (visible) slot.swipe?.bringToFront()
        }
    }

    private fun onBridge(slot: Slot, event: BridgeEvent) {
        if (slot.web == null) return
        when (event) {
            is BridgeEvent.Route -> onRoute(slot, event.url, event.index)
            is BridgeEvent.Navigate -> onNavigate(slot, event.url)
            is BridgeEvent.Blocked -> {
                Log.i(DMGramApp.TAG, "Blocked ${event.reason} ${event.url}")
                showBlocked()
            }
            is BridgeEvent.Unread -> {
                if (slot.tab == Tab.HOME) homeUnread = event.count.coerceAtLeast(0)
            }
            is BridgeEvent.Username -> onUsername(event.value)
            is BridgeEvent.Avatar -> {
                if (slot.tab == Tab.HOME) {
                    val bitmap = BitmapFactory.decodeByteArray(event.jpeg, 0, event.jpeg.size)
                    if (bitmap != null && bitmap.width in 1..256 && bitmap.height in 1..256) {
                        avatar = bitmap.asImageBitmap()
                    }
                }
            }
            is BridgeEvent.Theme -> {
                if (slot.tab == current) pageDark = event.dark
                slot.web?.setBackgroundColor(cssColor(event.background, event.dark))
                slot.swipe?.let { styleSwipe(it, event.dark) }
            }
            is BridgeEvent.Scroll -> slot.atTop = event.atTop
            BridgeEvent.Ready -> {
                slot.ready = true
                refreshing = false
                slot.swipe?.isRefreshing = false
                if (slot.tab == Tab.HOME) onHomeReady()
            }
            is BridgeEvent.Log -> Log.i(DMGramApp.TAG, "${event.level} ${event.msg}")
            is BridgeEvent.ReelLock -> {
                slot.reelLocked = event.active
                Log.i(DMGramApp.TAG, "Reel lock ${slot.tab.name} active=${event.active}")
            }
        }
        publish()
    }

    private fun dismissReel(web: WebView, slot: Slot) {
        web.evaluateJavascript(
            "(window.__dmgram&&window.__dmgram.reelBackPoint&&window.__dmgram.reelBackPoint())||''",
        ) { raw ->
            if (tapAt(web, raw)) return@evaluateJavascript
            web.evaluateJavascript(
                "(window.__dmgram&&window.__dmgram.reelEscape&&window.__dmgram.reelEscape())||''",
                null,
            )
            web.postDelayed({
                if (!slot.reelLocked) return@postDelayed
                Log.i(DMGramApp.TAG, "Reel back fell through on ${slot.tab.name}")
                web.evaluateJavascript(
                    "(window.__dmgram&&window.__dmgram.forceUnlock&&window.__dmgram.forceUnlock())||''",
                    null,
                )
                slot.reelLocked = false
                publish()
                continueBack(slot)
            }, 300)
        }
    }

    private fun tapAt(web: WebView, raw: String?): Boolean {
        val text = raw?.trim()?.removeSurrounding("\"") ?: return false
        val parts = text.split(',')
        if (parts.size < 3) return false
        val cssX = parts[0].toFloatOrNull() ?: return false
        val cssY = parts[1].toFloatOrNull() ?: return false
        val inner = parts[2].toFloatOrNull() ?: return false
        if (inner <= 1f || web.width <= 0) return false
        val scale = web.width / inner
        val x = cssX * scale
        val y = cssY * scale
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0)
        web.dispatchTouchEvent(down)
        web.postDelayed({
            val up = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            web.dispatchTouchEvent(up)
            down.recycle()
            up.recycle()
        }, 60)
        return true
    }

    private fun stepBack(web: WebView, slot: Slot): Boolean {
        val list = web.copyBackForwardList()
        Log.i(
            DMGramApp.TAG,
            "back ${slot.tab.name} canGoBack=${web.canGoBack()} list=${list.currentIndex}/${list.size} js=${slot.historyIndex} ${slot.url}",
        )
        if (slot.historyIndex > 0 && slot.historyIndex != slot.stuckIndex) {
            slot.stuckIndex = slot.historyIndex
            eval(web, "back()")
            return true
        }
        if (web.canGoBack()) {
            slot.stuckIndex = -1
            web.goBack()
            return true
        }
        slot.stuckIndex = -1
        return false
    }

    private fun onRoute(slot: Slot, url: String, index: Int) {
        slot.url = url
        if (index >= 0) slot.historyIndex = index
        slot.canGoBack = slot.web?.canGoBack() == true
        val decision = decide(slot.tab, url, compiled)
        slot.route = decision.route
        if (BuildConfig.DEBUG) {
            Log.d(DMGramApp.TAG, "route ${slot.tab.name} ${decision.route} ${decision.action.name} $url")
        }
        when (val action = decision.action) {
            is NavAction.Block -> {
                if (!slot.rewinding) {
                    slot.rewinding = true
                    val web = slot.web
                    if (web == null || !stepBack(web, slot)) web?.loadUrl(rootOf(slot.tab))
                    showBlocked()
                }
            }
            is NavAction.Switch -> {
                if (!slot.rewinding) {
                    slot.rewinding = true
                    val web = slot.web
                    if (web == null || !stepBack(web, slot)) web?.loadUrl(rootOf(slot.tab))
                    if (current == slot.tab) select(action.tab)
                }
            }
            else -> slot.rewinding = false
        }
        if (slot.tab == Tab.HOME) {
            homeLoaded = true
            maybePreloadDms()
        }
        refreshLogin()
    }

    private fun onNavigate(slot: Slot, url: String) {
        val decision = decide(slot.tab, url, compiled)
        when (val action = decision.action) {
            is NavAction.Switch -> select(action.tab)
            is NavAction.External -> openExternal(action.url)
            is NavAction.System -> openSystem(action.url)
            is NavAction.Block -> showBlocked()
            NavAction.Allow, NavAction.Ignore -> Unit
        }
    }

    private fun onOverride(slot: Slot, url: String): Boolean {
        val decision = decide(slot.tab, url, compiled)
        return when (val action = decision.action) {
            NavAction.Allow -> false
            is NavAction.Block -> {
                Log.i(DMGramApp.TAG, "Override blocked ${action.reason} $url")
                showBlocked()
                true
            }
            is NavAction.Switch -> {
                select(action.tab)
                true
            }
            is NavAction.External -> {
                openExternal(action.url)
                true
            }
            is NavAction.System -> {
                openSystem(action.url)
                true
            }
            NavAction.Ignore -> true
        }
    }

    private fun onFinished(slot: Slot, url: String) {
        slot.url = url
        slot.canGoBack = slot.web?.canGoBack() == true
        slot.route = decide(slot.tab, url, compiled).route
        refreshing = false
        slot.swipe?.isRefreshing = false
        if (slot.tab == Tab.HOME) {
            homeLoaded = true
            maybePreloadDms()
        }
        refreshLogin()
        publish()
    }

    private fun onProgress(slot: Slot, progress: Int) {
        slot.progress = progress
        if (progress >= 100) {
            refreshing = false
            slot.swipe?.isRefreshing = false
        }
        if (slot.tab == current) publish()
    }

    private fun onTitle(slot: Slot, title: String?) {
        if (slot.tab != Tab.DMS) return
        val count = title?.let { titlePattern.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: return
        titleUnread = count
        publish()
    }

    private fun onUsername(value: String) {
        if (!usernamePattern.matches(value)) return
        if (username == value && slots.getValue(Tab.PROFILE).web != null) return
        username = value
        prefs.edit().putString(KEY_USERNAME, value).apply()
        Log.i(DMGramApp.TAG, "Username is $value")
        if (current == Tab.PROFILE && slots.getValue(Tab.PROFILE).web == null) create(Tab.PROFILE, load = true)
    }

    private fun onPull(slot: Slot) {
        val chrome = visualChrome()
        if (slot.tab != current || !slot.atTop || !chrome.pullToRefresh) {
            slot.swipe?.isRefreshing = false
            return
        }
        refreshing = true
        slot.ready = false
        slot.web?.reload()
        publish()
    }

    private fun onRendererGone(dead: WebView) {
        val slot = slots.values.firstOrNull { it.web === dead } ?: return
        Log.e(DMGramApp.TAG, "Recreating ${slot.tab.name} after renderer exit")
        val wasCurrent = slot.tab == current
        destroy(slot.tab)
        create(slot.tab, load = true)
        if (wasCurrent) reveal(slot.tab)
        publish()
    }

    private fun refreshLogin() {
        val now = LoginState.isLoggedIn()
        if (now == loggedIn) {
            if (now) maybePreloadDms()
            return
        }
        loggedIn = now
        if (now) {
            Log.i(DMGramApp.TAG, "Logged in")
            maybePreloadDms()
        } else {
            Log.i(DMGramApp.TAG, "Logged out")
            username = null
            homeUnread = null
            avatar = null
            prefs.edit().remove(KEY_USERNAME).apply()
            destroy(Tab.DMS)
            destroy(Tab.PROFILE)
            if (current != Tab.HOME) current = Tab.HOME
            reveal(Tab.HOME)
        }
    }

    private fun maybePreloadDms() {
        if (!foreground || !loggedIn || !homeLoaded) return
        if (slots.getValue(Tab.DMS).web != null) return
        create(Tab.DMS, load = true)
    }

    private fun showBlocked() {
        val now = SystemClock.uptimeMillis()
        if (now - blockedAt < BLOCK_GAP_MS) return
        blockedAt = now
        blockedVisible = true
        hideNotice?.let { main.removeCallbacks(it) }
        val hide = Runnable {
            blockedVisible = false
            publish()
        }
        hideNotice = hide
        main.postDelayed(hide, NOTICE_MS)
        publish()
    }

    private fun openExternal(url: String) {
        try {
            CustomTabsIntent.Builder().build().launchUrl(activity, Uri.parse(url))
        } catch (error: ActivityNotFoundException) {
            Log.e(DMGramApp.TAG, "No Custom Tab${if (BuildConfig.DEBUG) " for $url" else ""}", error)
            openSystem(url)
        }
    }

    private fun openSystem(url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (error: ActivityNotFoundException) {
            Log.e(DMGramApp.TAG, "No handler${if (BuildConfig.DEBUG) " for $url" else ""}", error)
        }
    }

    private fun publish() {
        val chrome = visualChrome()
        for (slot in slots.values) {
            slot.swipe?.isEnabled = chrome.pullToRefresh && slot.tab == current && slot.atTop
            if (slot.tab == current) slot.swipe?.isRefreshing = refreshing
        }
        onChanged()
    }

    private fun isRoot(tab: Tab, url: String): Boolean {
        if (url.isEmpty()) return true
        val path = try {
            normalizePath(URI(url).path)
        } catch (error: URISyntaxException) {
            Log.e(DMGramApp.TAG, "Bad URL while checking the tab root", error)
            return false
        }
        val root = tab.rootPath(username) ?: return false
        return path.trimEnd('/') == root.trimEnd('/')
    }

    private fun stepsBackToRoot(web: WebView, tab: Tab): Int? {
        val list = web.copyBackForwardList()
        val index = list.currentIndex
        for (i in index - 1 downTo 0) {
            val entry = list.getItemAtIndex(i)?.url ?: continue
            if (isRoot(tab, entry)) return index - i
        }
        return null
    }

    private fun rootOf(tab: Tab): String = tab.rootUrl(username) ?: "https://www.instagram.com/"

    private fun eval(web: WebView?, call: String) {
        if (web == null) return
        try {
            web.evaluateJavascript("window.__dmgram && window.__dmgram.$call", null)
        } catch (error: RuntimeException) {
            Log.e(DMGramApp.TAG, "evaluateJavascript failed", error)
        }
    }

    private fun refreshRemote() {
        if (!foreground) return
        remoteJob?.cancel()
        val dismissed = prefs.getString(KEY_UPDATE_DISMISSED, null)
        updateInfo = if (updateChecker.url() == null) null else updateChecker.saved()
        updateDismissed = updateInfo?.version == dismissed
        checkingUpdate = updateChecker.url() != null && updateChecker.due() && updateInfo == null
        publish()
        remoteJob = jobs.launch {
            val bundled = rulesRepository.bundled()
            val bundledVersion = try {
                Rules.parse(bundled).version
            } catch (error: IllegalArgumentException) {
                Log.e(DMGramApp.TAG, "Bundled rules failed to parse", error)
                0
            }
            if (rulesRepository.url() == null) {
                Log.i(DMGramApp.TAG, "Rules fetch skipped: no GitHub repo")
                rulesRepository.discardCache()
            } else {
                val cached = rulesRepository.cached(bundledVersion, BuildConfig.VERSION_CODE)
                if (cached != null) applyIncoming(cached)
                if (foreground && rulesRepository.due()) {
                    val remote = rulesRepository.fetch(bundledVersion, BuildConfig.VERSION_CODE)
                    if (remote != null) applyIncoming(remote)
                }
            }
            if (!foreground) return@launch
            if (updateChecker.url() == null) {
                Log.i(DMGramApp.TAG, "Update check skipped: no GitHub repo")
                updateChecker.save(null)
                updateInfo = null
                updateDismissed = false
                checkingUpdate = false
                publish()
                return@launch
            }
            if (!updateChecker.due()) {
                checkingUpdate = false
                publish()
                return@launch
            }
            val info = updateChecker.fetch(BuildConfig.VERSION_NAME)
            if (!foreground) return@launch
            updateChecker.save(info)
            updateInfo = info
            updateDismissed = info != null && info.version == prefs.getString(KEY_UPDATE_DISMISSED, null)
            checkingUpdate = false
            if (info != null) Log.i(DMGramApp.TAG, "Update available ${info.version}")
            publish()
        }
    }

    private fun applyIncoming(json: String) {
        val version = try {
            Rules.parse(json).version
        } catch (error: IllegalArgumentException) {
            Log.e(DMGramApp.TAG, "Remote rules failed to parse", error)
            return
        }
        rulesVersion = version
        if (json == rulesJson) return
        Log.i(DMGramApp.TAG, "Applying rules version $version")
        applyRules(json)
        publish()
    }

    private fun webViewVersion(): String = try {
        WebViewCompat.getCurrentWebViewPackage(activity)?.versionName ?: "unknown"
    } catch (error: RuntimeException) {
        Log.e(DMGramApp.TAG, "WebView version unavailable", error)
        "unknown"
    }

    private fun cssColor(value: String, dark: Boolean): Int {
        val rgb = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)""").find(value)
        if (rgb != null) {
            return android.graphics.Color.rgb(
                rgb.groupValues[1].toInt().coerceIn(0, 255),
                rgb.groupValues[2].toInt().coerceIn(0, 255),
                rgb.groupValues[3].toInt().coerceIn(0, 255),
            )
        }
        return try {
            android.graphics.Color.parseColor(value)
        } catch (error: IllegalArgumentException) {
            windowBackground(dark)
        }
    }

    private fun isDark(): Boolean {
        val mode = activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private class Slot(val tab: Tab) {
        var web: WebView? = null
        var swipe: SwipeRefreshLayout? = null
        var chrome: DMGramChromeClient? = null
        var script: ScriptHandler? = null
        var url: String = ""
        var route: RouteClass = RouteClass.UNKNOWN
        var canGoBack: Boolean = false
        var historyIndex: Int = 0
        var stuckIndex: Int = -1
        var progress: Int = 0
        var ready: Boolean = false
        var atTop: Boolean = true
        var reelLocked: Boolean = false
        var fullscreenVideo: Boolean = false
        var rewinding: Boolean = false
        var loadError: ErrorKind? = null
    }

    companion object {
        private const val PREFS = "dmgram"
        private const val KEY_USERNAME = "username"
        private const val KEY_UPDATE_DISMISSED = "update.dismissed"
        private const val NOTICE_MS = 2_000L
        private const val BLOCK_GAP_MS = 3_000L
    }
}
