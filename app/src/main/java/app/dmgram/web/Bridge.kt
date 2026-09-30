package app.dmgram.web

import android.net.Uri
import android.util.Log
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import app.dmgram.BuildConfig
import app.dmgram.DMGramApp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface BridgeEvent {
    data class Route(val url: String, val index: Int) : BridgeEvent
    data class Navigate(val url: String) : BridgeEvent
    data class Blocked(val url: String, val reason: String) : BridgeEvent
    data class Unread(val count: Int) : BridgeEvent
    data class Username(val value: String) : BridgeEvent
    data class Theme(val dark: Boolean, val background: String) : BridgeEvent
    data class Scroll(val atTop: Boolean) : BridgeEvent
    data object Ready : BridgeEvent
    data class Log(val level: String, val msg: String) : BridgeEvent
    data class ReelLock(val active: Boolean) : BridgeEvent
}

object Bridge {
    const val NAME = "DMGramNative"
    val ORIGINS: Set<String> = setOf("https://www.instagram.com")

    private val json = Json { ignoreUnknownKeys = true }

    fun attach(webView: WebView, onEvent: (BridgeEvent) -> Unit) {
        WebViewCompat.addWebMessageListener(
            webView,
            NAME,
            ORIGINS,
            object : WebViewCompat.WebMessageListener {
                override fun onPostMessage(
                    view: WebView,
                    message: WebMessageCompat,
                    sourceOrigin: Uri,
                    isMainFrame: Boolean,
                    replyProxy: JavaScriptReplyProxy,
                ) {
                    if (!isMainFrame) return
                    val data = message.data ?: return
                    val event = parse(data) ?: return
                    onEvent(event)
                }
            },
        )
    }

    fun parse(raw: String): BridgeEvent? {
        if (raw.length > 8_000) {
            Log.w(DMGramApp.TAG, "Ignoring oversized bridge message")
            return null
        }
        val obj = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (error: RuntimeException) {
            if (BuildConfig.DEBUG) Log.w(DMGramApp.TAG, "Ignoring malformed bridge message", error)
            return null
        } catch (error: IllegalArgumentException) {
            if (BuildConfig.DEBUG) Log.w(DMGramApp.TAG, "Ignoring malformed bridge message", error)
            return null
        }
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        return when (type) {
            "route" -> text(obj, "url", 2_000)?.let { url ->
                BridgeEvent.Route(url, obj["index"]?.jsonPrimitive?.intOrNull ?: -1)
            }
            "navigate" -> text(obj, "url", 2_000)?.let(BridgeEvent::Navigate)
            "blocked" -> {
                val url = text(obj, "url", 2_000) ?: return null
                BridgeEvent.Blocked(url, text(obj, "reason", 80) ?: "blocked")
            }
            "unread" -> obj["count"]?.jsonPrimitive?.intOrNull?.let(BridgeEvent::Unread)
            "username" -> text(obj, "value", 30)?.let(BridgeEvent::Username)
            "theme" -> {
                val dark = obj["dark"]?.jsonPrimitive?.booleanOrNull ?: return null
                BridgeEvent.Theme(dark, text(obj, "background", 80) ?: "")
            }
            "scroll" -> obj["atTop"]?.jsonPrimitive?.booleanOrNull?.let(BridgeEvent::Scroll)
            "ready" -> BridgeEvent.Ready
            "log" -> {
                if (!BuildConfig.DEBUG) return null
                BridgeEvent.Log(text(obj, "level", 20) ?: "info", text(obj, "msg", 500) ?: "")
            }
            "reelLock" -> obj["active"]?.jsonPrimitive?.booleanOrNull?.let(BridgeEvent::ReelLock)
            else -> null
        }
    }

    private fun text(obj: kotlinx.serialization.json.JsonObject, key: String, max: Int): String? =
        obj[key]?.jsonPrimitive?.contentOrNull?.take(max)
}
