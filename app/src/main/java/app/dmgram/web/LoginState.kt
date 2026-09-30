package app.dmgram.web

import android.util.Log
import android.webkit.CookieManager
import app.dmgram.DMGramApp

object LoginState {
    fun isLoggedIn(): Boolean {
        val raw = try {
            CookieManager.getInstance().getCookie("https://www.instagram.com")
        } catch (error: RuntimeException) {
            Log.e(DMGramApp.TAG, "Could not read the Instagram cookie", error)
            null
        } ?: return false
        return raw.split(';').any { part ->
            val trimmed = part.trim()
            trimmed.startsWith("ds_user_id=") && trimmed.substringAfter('=').isNotBlank()
        }
    }
}
