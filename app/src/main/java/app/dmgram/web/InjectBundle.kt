package app.dmgram.web

import android.content.Context
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import app.dmgram.BuildConfig
import app.dmgram.tabs.Tab
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object InjectBundle {
    private val json = Json { ignoreUnknownKeys = true }

    fun install(context: Context, webView: WebView, tab: Tab, rulesJson: String): ScriptHandler =
        WebViewCompat.addDocumentStartJavaScript(webView, script(context, tab, rulesJson), Bridge.ORIGINS)

    fun configJson(tab: Tab, rulesJson: String): String {
        val rules = json.parseToJsonElement(rulesJson)
        return buildJsonObject {
            put("tab", tab.name)
            put("debug", BuildConfig.DEBUG)
            put("rules", rules as? JsonObject ?: buildJsonObject {})
        }.toString()
    }

    fun script(context: Context, tab: Tab, rulesJson: String): String {
        val config = embedJson(configJson(tab, rulesJson))
        val bridge = asset(context, "inject/bridge.js")
        val router = asset(context, "inject/router.js")
        val cleanup = asset(context, "inject/cleanup.js")
        val polish = asset(context, "inject/polish.js")
        val hide = embed(asset(context, "inject/hide.css"))
        val rulesCss = embed(asset(context, "inject/rules.css"))
        val polishCss = embed(asset(context, "inject/polish.css"))
        return buildString {
            append("(() => {\n")
            append("if (window.__dmgram) return;\n")
            append("const CONFIG = ")
            append(config)
            append(";\n")
            append("const dmgramInjectError = (name, error) => {\n")
            append("  console.error('DMGram ' + name, error);\n")
            append("  try {\n")
            append("    if (CONFIG.debug && window.__dmgramPost) {\n")
            append("      window.__dmgramPost({type:'log', level:'error', msg: String(name + ' ' + (error && error.stack || error)).slice(0, 500)});\n")
            append("    }\n")
            append("  } catch (postError) { console.error('DMGram log failed', postError); }\n")
            append("};\n")
            append("try {\n")
            append(bridge)
            append("\n} catch (error) { dmgramInjectError('bridge', error); }\n")
            append("try {\n")
            append(router)
            append("\n} catch (error) { dmgramInjectError('router', error); }\n")
            append("try {\n")
            append(cleanup)
            append("\n} catch (error) { dmgramInjectError('cleanup', error); }\n")
            append("try {\n")
            append(styleBoot(hide, rulesCss, polishCss))
            append("\n} catch (error) { dmgramInjectError('styles', error); }\n")
            append("try {\n")
            append(polish)
            append("\n} catch (error) { dmgramInjectError('polish', error); }\n")
            append("})();\n")
        }
    }

    private fun styleBoot(hide: String, rules: String, polish: String): String = """
        const dmgramStyles = [
          ["dmgram-hide", $hide],
          ["dmgram-rules", $rules],
          ["dmgram-polish", $polish],
        ];
        const dmgramMountStyles = () => {
          const root = document.documentElement;
          if (!root) return;
          for (const [id, css] of dmgramStyles) {
            let el = document.getElementById(id);
            if (!el) {
              el = document.createElement("style");
              el.id = id;
              root.appendChild(el);
            }
            if (el.textContent !== css) el.textContent = css;
          }
        };
        const dmgramWatchStyles = () => {
          const root = document.documentElement;
          if (!root) return;
          dmgramMountStyles();
          new MutationObserver(() => {
            if (!document.getElementById("dmgram-hide") || !document.getElementById("dmgram-rules") || !document.getElementById("dmgram-polish")) {
              dmgramMountStyles();
            }
          }).observe(root, { childList: true });
        };
        if (document.documentElement) dmgramWatchStyles();
        else document.addEventListener("DOMContentLoaded", dmgramWatchStyles, { once: true });
    """.trimIndent()

    private fun asset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader().use { it.readText() }

    private fun embed(value: String): String = jsQuote(value)

    private fun embedJson(json: String): String =
        json.replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
}
