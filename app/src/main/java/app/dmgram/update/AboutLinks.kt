package app.dmgram.update

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object AboutLinks {
    fun reportBody(
        appVersion: String,
        versionCode: Int,
        rulesVersion: Int,
        androidVersion: String,
        webViewVersion: String,
    ): String = buildString {
        append("App version: ").append(appVersion).append(" (").append(versionCode).append(")\n")
        append("Rules version: ").append(rulesVersion).append('\n')
        append("Android version: ").append(androidVersion).append('\n')
        append("WebView version: ").append(webViewVersion).append('\n')
    }

    fun reportUrl(repo: String, body: String): String =
        "https://github.com/$repo/issues/new?body=" + URLEncoder.encode(body, StandardCharsets.UTF_8)

    fun repoUrl(repo: String): String = "https://github.com/$repo"
}
