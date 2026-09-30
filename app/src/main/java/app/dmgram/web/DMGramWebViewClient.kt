package app.dmgram.web

import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import app.dmgram.DMGramApp

class DMGramWebViewClient(
    private val onRendererGone: () -> Unit,
) : WebViewClient() {
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        Log.e(
            DMGramApp.TAG,
            "WebView renderer gone didCrash=${detail.didCrash()} priority=${detail.rendererPriorityAtExit()}",
        )
        onRendererGone()
        return true
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            Log.e(
                DMGramApp.TAG,
                "Main frame error ${error.errorCode} ${error.description} ${request.url}",
            )
        }
    }
}
