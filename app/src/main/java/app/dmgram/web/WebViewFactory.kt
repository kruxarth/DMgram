package app.dmgram.web

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import app.dmgram.DMGramApp

object WebViewFactory {
    fun requiredFeaturesSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) &&
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    fun configure(webView: WebView) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        val major = UserAgent.chromeMajor(webView.context)
        if (major == null) {
            Log.e(DMGramApp.TAG, "Default user agent has no Chrome major; leaving it unchanged")
        } else {
            settings.userAgentString = UserAgent.reducedChromeUserAgent(major)
            if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
                WebSettingsCompat.setUserAgentMetadata(settings, UserAgent.clientHints(major))
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
        }
        clearRequestedWithHeader(settings)

        val cookies = CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(webView, true)

        Log.i(
            DMGramApp.TAG,
            "WebView ready ua=${settings.userAgentString} " +
                "darkening=${supported(WebViewFeature.ALGORITHMIC_DARKENING)} " +
                "uaMetadata=${supported(WebViewFeature.USER_AGENT_METADATA)} " +
                "requestedWith=${requestedWithSupported()}",
        )
    }

    @Suppress("DEPRECATION")
    private fun clearRequestedWithHeader(settings: WebSettings) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) return
        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
    }

    @Suppress("DEPRECATION")
    private fun requestedWithSupported(): Boolean =
        WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)

    private fun supported(feature: String): Boolean = WebViewFeature.isFeatureSupported(feature)
}
