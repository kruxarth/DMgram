package app.dmgram

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewFeature
import app.dmgram.tabs.FrameUi
import app.dmgram.tabs.TabController
import app.dmgram.ui.AboutSheet
import app.dmgram.ui.AboutState
import app.dmgram.ui.BlockedNotice
import app.dmgram.ui.BlockedNoticeState
import app.dmgram.ui.ErrorKind
import app.dmgram.ui.ErrorState
import app.dmgram.ui.HomeHeader
import app.dmgram.ui.LoadingOverlay
import app.dmgram.ui.TabBar
import app.dmgram.ui.UpdateBanner
import app.dmgram.ui.UpdateStatus
import app.dmgram.ui.theme.DMGramTheme
import app.dmgram.ui.theme.windowBackground
import app.dmgram.web.WebViewFactory

class MainActivity : ComponentActivity() {
    private var night by mutableStateOf(false)
    private var frame by mutableStateOf(FrameUi())
    private var featuresSupported = false
    private var homeReady = false
    private var splashStarted = 0L
    private var webContainer: FrameLayout? = null
    private var controller: TabController? = null
    private var media: app.dmgram.web.MediaRequests? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splashStarted = SystemClock.uptimeMillis()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        night = isDark()
        splash.setKeepOnScreenCondition {
            !homeReady && SystemClock.uptimeMillis() - splashStarted < SPLASH_MS
        }
        featuresSupported = requiredFeatures()
        logFeatureSupport()
        if (!featuresSupported) {
            homeReady = true
            setContent {
                DMGramTheme(dark = night) {
                    ErrorState(ErrorKind.WEBVIEW_UPDATE, onRetry = ::openWebViewStore)
                }
            }
            return
        }
        val container = FrameLayout(this)
        webContainer = container
        val requests = app.dmgram.web.MediaRequests(this)
        media = requests
        val tabs = TabController(
            activity = this,
            container = container,
            media = requests,
            onHomeReady = { homeReady = true },
            onChanged = { frame = controller?.snapshot() ?: FrameUi() },
        )
        controller = tabs
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    tabs.onBack()
                }
            },
        )
        val incoming = if (intent?.action == Intent.ACTION_VIEW) intent?.dataString else null
        tabs.start(incoming)
        frame = tabs.snapshot()
        setContent {
            DMGramTheme(dark = night) {
                DmgramFrame(
                    frame = frame,
                    systemDark = night,
                    container = container,
                    onKeyboard = tabs::setKeyboardOpen,
                    onSelect = tabs::select,
                    onReselect = tabs::reselect,
                    onTitle = tabs::openAbout,
                    onSearch = tabs::openSearch,
                    onActivity = tabs::openActivity,
                    onDismissAbout = tabs::dismissAbout,
                    onInstall = tabs::installUpdate,
                    onDismissUpdate = tabs::dismissUpdate,
                    onGitHub = tabs::openGitHub,
                    onReport = tabs::openReport,
                    onRetry = tabs::retryLoad,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            val url = intent.dataString ?: return
            controller?.openIncoming(url)
        }
    }

    override fun onStart() {
        super.onStart()
        controller?.onHostStart()
    }

    override fun onStop() {
        controller?.onHostStop()
        try {
            CookieManager.getInstance().flush()
        } catch (error: RuntimeException) {
            Log.e(DMGramApp.TAG, "Cookie flush failed", error)
        }
        super.onStop()
    }

    override fun onDestroy() {
        media?.cancel()
        media = null
        controller?.destroyAll()
        controller = null
        webContainer = null
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val dark = isDark()
        night = dark
        controller?.setSystemDark(dark)
        window.decorView.setBackgroundColor(windowBackground(frame.pageDark ?: dark))
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_BACKGROUND) {
            controller?.onTrim()
        }
    }

    private fun requiredFeatures(): Boolean = try {
        WebViewFactory.requiredFeaturesSupported()
    } catch (error: RuntimeException) {
        Log.e(DMGramApp.TAG, "WebView feature check failed", error)
        false
    }

    private fun logFeatureSupport() {
        Log.i(
            DMGramApp.TAG,
            "features documentStart=${flag(WebViewFeature.DOCUMENT_START_SCRIPT)} " +
                "webMessage=${flag(WebViewFeature.WEB_MESSAGE_LISTENER)} " +
                "supported=$featuresSupported",
        )
    }

    private fun flag(feature: String): Boolean = try {
        WebViewFeature.isFeatureSupported(feature)
    } catch (error: RuntimeException) {
        Log.e(DMGramApp.TAG, "Feature check failed for $feature", error)
        false
    }

    private fun openWebViewStore() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.webview"),
        )
        try {
            startActivity(intent)
        } catch (error: RuntimeException) {
            Log.e(DMGramApp.TAG, "Could not open the WebView Play Store listing", error)
        }
    }

    private fun isDark(): Boolean {
        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    companion object {
        private const val SPLASH_MS = 3_000L
    }
}

@Composable
private fun DmgramFrame(
    frame: FrameUi,
    systemDark: Boolean,
    container: FrameLayout,
    onKeyboard: (Boolean) -> Unit,
    onSelect: (app.dmgram.tabs.Tab) -> Unit,
    onReselect: (app.dmgram.tabs.Tab) -> Unit,
    onTitle: () -> Unit,
    onSearch: () -> Unit,
    onActivity: () -> Unit,
    onDismissAbout: () -> Unit,
    onInstall: () -> Unit,
    onDismissUpdate: () -> Unit,
    onGitHub: () -> Unit,
    onReport: () -> Unit,
    onRetry: () -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val keyboard = WindowInsets.ime.getBottom(density) > 0
    val pageDark = frame.pageDark ?: systemDark
    SideEffect {
        onKeyboard(keyboard)
        val window = (view.context as? ComponentActivity)?.window ?: return@SideEffect
        val insets = WindowInsetsControllerCompat(window, view)
        insets.isAppearanceLightStatusBars = !pageDark
        insets.isAppearanceLightNavigationBars = !pageDark
        if (frame.fullScreen) {
            insets.hide(WindowInsetsCompat.Type.systemBars())
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
        window.decorView.setBackgroundColor(windowBackground(pageDark))
    }
    val top = if (frame.fullScreen) Modifier else Modifier.statusBarsPadding()
    val bottom = when {
        frame.fullScreen -> WindowInsets(0, 0, 0, 0)
        keyboard -> WindowInsets.ime.union(WindowInsets.navigationBars)
        !frame.showTabBar -> WindowInsets.navigationBars
        else -> WindowInsets(0, 0, 0, 0)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().then(top)) {
            val update = frame.update
            if (frame.showHomeHeader && update != null && !frame.updateDismissed) {
                UpdateBanner(update, onInstall, onDismissUpdate)
            }
            if (frame.showHomeHeader) {
                HomeHeader(
                    onTitleClick = onTitle,
                    onSearch = onSearch,
                    onActivity = onActivity,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .windowInsetsPadding(bottom),
            ) {
                AndroidView(
                    factory = {
                        (container.parent as? ViewGroup)?.removeView(container)
                        container
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                LoadingOverlay(progress = frame.progress, ready = frame.ready)
                val error = frame.loadError
                if (error != null) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        ErrorState(error, onRetry)
                    }
                }
            }
            if (frame.showTabBar) {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    TabBar(
                        current = frame.tab,
                        unread = frame.unread,
                        onSelect = onSelect,
                        onReselect = onReselect,
                    )
                }
            }
        }
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            BlockedNotice(BlockedNoticeState(frame.blockedVisible))
        }
        if (frame.aboutOpen) {
            val status = when {
                frame.update != null -> UpdateStatus.Available
                frame.checkingUpdate -> UpdateStatus.Checking
                else -> UpdateStatus.UpToDate
            }
            AboutSheet(
                state = AboutState(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE,
                    rulesVersion = frame.rulesVersion,
                    status = status,
                    githubRepo = BuildConfig.GITHUB_REPO,
                ),
                onDismiss = onDismissAbout,
                onInstall = onInstall,
                onGitHub = onGitHub,
                onReport = onReport,
            )
        }
    }
}
