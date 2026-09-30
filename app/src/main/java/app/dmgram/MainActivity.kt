package app.dmgram

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewFeature
import app.dmgram.ui.theme.DMGramTheme
import app.dmgram.ui.theme.Dimens
import app.dmgram.ui.theme.windowBackground
import app.dmgram.web.DMGramWebViewClient
import app.dmgram.web.WebViewFactory

class MainActivity : ComponentActivity() {
    private lateinit var webContainer: FrameLayout
    private var webView: WebView? = null
    private var featuresSupported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        featuresSupported = requiredFeatures()
        logFeatureSupport()
        if (featuresSupported) {
            webContainer = FrameLayout(this)
            mountWebView()
        }
        setContent {
            DMGramTheme {
                if (featuresSupported) {
                    AndroidView(
                        factory = {
                            (webContainer.parent as? android.view.ViewGroup)?.removeView(webContainer)
                            webContainer
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    WebViewUpdateRequired(onOpenStore = ::openWebViewStore)
                }
            }
        }
    }

    override fun onStop() {
        CookieManager.getInstance().flush()
        super.onStop()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyWindowBackground()
    }

    private fun mountWebView() {
        val view = WebView(this)
        view.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        view.setBackgroundColor(windowBackground(isDark()))
        WebViewFactory.configure(view)
        view.webViewClient = DMGramWebViewClient(onRendererGone = { replaceWebView(view) })
        webContainer.addView(view)
        webView = view
        applyWindowBackground()
        view.loadUrl(HOME_URL)
    }

    private fun replaceWebView(dead: WebView) {
        if (webView !== dead) return
        webContainer.removeView(dead)
        webView = null
        dead.destroy()
        mountWebView()
    }

    private fun applyWindowBackground() {
        val color = windowBackground(isDark())
        window.decorView.setBackgroundColor(color)
        webView?.setBackgroundColor(color)
    }

    private fun isDark(): Boolean {
        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun requiredFeatures(): Boolean {
        return try {
            WebViewFactory.requiredFeaturesSupported()
        } catch (error: RuntimeException) {
            Log.e(DMGramApp.TAG, "WebView feature check failed", error)
            false
        }
    }

    private fun logFeatureSupport() {
        Log.i(
            DMGramApp.TAG,
            "features documentStart=${flag(WebViewFeature.DOCUMENT_START_SCRIPT)} " +
                "webMessage=${flag(WebViewFeature.WEB_MESSAGE_LISTENER)} " +
                "supported=$featuresSupported",
        )
    }

    private fun flag(feature: String): Boolean =
        try {
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

    companion object {
        const val HOME_URL = "https://www.instagram.com/?variant=following"
    }
}

@Composable
private fun WebViewUpdateRequired(onOpenStore: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.stackGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.webview_update_required))
        Button(onClick = onOpenStore) {
            Text(stringResource(R.string.webview_update_action))
        }
    }
}
