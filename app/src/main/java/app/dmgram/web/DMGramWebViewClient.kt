package app.dmgram.web

import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import app.dmgram.BuildConfig
import app.dmgram.DMGramApp

class DMGramWebViewClient(
    private val onRendererGone: () -> Unit,
    private val onOverrideUrl: (String) -> Boolean,
    private val onPageFinished: (String) -> Unit,
    private val onPageStarted: () -> Unit,
    private val onMainFrameError: (Boolean) -> Unit,
) : WebViewClient() {
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.e(
            DMGramApp.TAG,
            "WebView renderer gone didCrash=${detail.didCrash()} priority=${detail.rendererPriorityAtExit()}",
        )
        onRendererGone()
        return true
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (!request.isForMainFrame) return false
        return onOverrideUrl(request.url.toString())
    }

    override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
        onPageStarted()
    }

    override fun onPageFinished(view: WebView, url: String) {
        onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (!request.isForMainFrame) return
        Log.e(
            DMGramApp.TAG,
            "Main frame error ${error.errorCode} ${error.description} ${if (BuildConfig.DEBUG) request.url else ""}",
        )
        val offline = error.errorCode == ERROR_HOST_LOOKUP ||
            error.errorCode == ERROR_CONNECT ||
            error.errorCode == ERROR_TIMEOUT ||
            error.errorCode == ERROR_IO
        onMainFrameError(offline)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse,
    ) {
        if (!request.isForMainFrame || errorResponse.statusCode < 400) return
        Log.e(DMGramApp.TAG, "Main frame HTTP ${errorResponse.statusCode} ${if (BuildConfig.DEBUG) request.url else ""}")
        onMainFrameError(false)
    }
}
