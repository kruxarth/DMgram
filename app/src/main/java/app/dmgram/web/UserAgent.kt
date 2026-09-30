package app.dmgram.web

import android.content.Context
import android.webkit.WebSettings
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebViewFeature

/**
 * Reduced user-agent string that real Chrome for Android sends, plus client
 * hints branded as Chrome rather than Android WebView.
 */
object UserAgent {
    private val chromeMajorPattern = Regex("""Chrome/(\d+)""")

    fun chromeMajorFrom(defaultUserAgent: String): String? =
        chromeMajorPattern.find(defaultUserAgent)?.groupValues?.get(1)

    fun reducedChromeUserAgent(major: String): String =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/$major.0.0.0 Mobile Safari/537.36"

    fun reducedChromeUserAgent(context: Context): String {
        val major = chromeMajorFrom(WebSettings.getDefaultUserAgent(context))
            ?: throw IllegalStateException(
                "Could not parse Chrome major version from the default user agent",
            )
        return reducedChromeUserAgent(major)
    }

    fun clientHints(major: String): UserAgentMetadata {
        val full = "$major.0.0.0"
        val brands = listOf(
            brand("Not.A/Brand", "99", "99.0.0.0"),
            brand("Chromium", major, full),
            brand("Google Chrome", major, full),
        )
        val builder = UserAgentMetadata.Builder()
            .setBrandVersionList(brands)
            .setFullVersion(full)
            .setPlatform("Android")
            .setMobile(true)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA_FORM_FACTORS)) {
            builder.setFormFactors(listOf(UserAgentMetadata.FORM_FACTOR_MOBILE))
        }
        return builder.build()
    }

    fun chromeMajor(context: Context): String? =
        chromeMajorFrom(WebSettings.getDefaultUserAgent(context))

    private fun brand(name: String, major: String, full: String): UserAgentMetadata.BrandVersion =
        UserAgentMetadata.BrandVersion.Builder()
            .setBrand(name)
            .setMajorVersion(major)
            .setFullVersion(full)
            .build()
}
