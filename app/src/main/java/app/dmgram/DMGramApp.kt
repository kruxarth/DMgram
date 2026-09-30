package app.dmgram

import android.app.Application
import android.util.Log
import android.webkit.WebView

class DMGramApp : Application() {
    override fun onCreate() {
        super.onCreate()
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        Log.i(TAG, "debug=${BuildConfig.DEBUG} version=${BuildConfig.VERSION_NAME}")
    }

    companion object {
        const val TAG = "DMGram"
    }
}
