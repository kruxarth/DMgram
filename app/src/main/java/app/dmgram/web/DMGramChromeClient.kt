package app.dmgram.web

import android.view.View
import android.webkit.WebChromeClient
import android.widget.FrameLayout

class DMGramChromeClient(
    private val host: FrameLayout,
    private val onFullscreenChanged: (Boolean) -> Unit,
    private val onProgress: (Int) -> Unit,
    private val onTitle: (String?) -> Unit,
) : WebChromeClient() {
    private var customView: View? = null
    private var customCallback: CustomViewCallback? = null

    fun isFullscreen(): Boolean = customView != null

    fun exitFullscreen() {
        if (customView == null) return
        val callback = customCallback
        val view = customView
        customView = null
        customCallback = null
        if (view != null) host.removeView(view)
        callback?.onCustomViewHidden()
        onFullscreenChanged(false)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        showCustom(view, callback)
    }

    override fun onShowCustomView(view: View?, requestedOrientation: Int, callback: CustomViewCallback?) {
        showCustom(view, callback)
    }

    private fun showCustom(view: View?, callback: CustomViewCallback?) {
        if (view == null) return
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }
        customView = view
        customCallback = callback
        view.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        host.addView(view)
        view.bringToFront()
        onFullscreenChanged(true)
    }

    override fun onHideCustomView() {
        exitFullscreen()
    }

    override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
        onProgress(newProgress)
    }

    override fun onReceivedTitle(view: android.webkit.WebView?, title: String?) {
        onTitle(title)
    }
}
