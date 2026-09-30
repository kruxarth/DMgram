package app.dmgram.nav

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class RouteClass {
    HOME_FEED,
    DIRECT_INBOX,
    DIRECT_THREAD,
    STORY,
    POST,
    REEL_SINGLE,
    REELS_FEED,
    SEARCH,
    EXPLORE_BLOCKED,
    ACTIVITY,
    PROFILE,
    ACCOUNT,
    AUTH,
    EXTERNAL,
    UNKNOWN,
}

@Serializable
data class HideRule(
    val id: String = "",
    val selector: String = "",
    val note: String = "",
)

@Serializable
data class Rules(
    val schema: Int = 1,
    val version: Int = 0,
    val minAppVersionCode: Int = 1,
    val routes: List<RoutePattern> = emptyList(),
    val reserved: List<String> = emptyList(),
    val hide: List<HideRule> = emptyList(),
    val css: String = "",
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Rules = json.decodeFromString(text)
    }
}

@Serializable
data class RoutePattern(
    @SerialName("class") val kind: String,
    val pattern: String,
    val hosts: List<String> = emptyList(),
)

class CompiledRules(rules: Rules) {
    val patterns: List<CompiledPattern> = rules.routes.map { pattern ->
        CompiledPattern(
            kind = RouteClass.valueOf(pattern.kind),
            regex = Regex(pattern.pattern),
            hosts = pattern.hosts.map { it.lowercase() }.toSet(),
        )
    }
    val reserved: Set<String> = rules.reserved.map { it.lowercase() }.toSet()
}

data class CompiledPattern(
    val kind: RouteClass,
    val regex: Regex,
    val hosts: Set<String>,
)

data class Classified(
    val route: RouteClass,
    val url: String,
    val scheme: String,
)

private val INSTAGRAM_HOSTS = setOf(
    "instagram.com",
    "www.instagram.com",
    "m.instagram.com",
)

fun classify(raw: String, rules: CompiledRules): Classified {
    val trimmed = raw.trim()
    val uri = try {
        URI(trimmed)
    } catch (error: URISyntaxException) {
        return Classified(RouteClass.EXTERNAL, trimmed, "invalid")
    }
    val scheme = uri.scheme?.lowercase().orEmpty()
    if (scheme != "http" && scheme != "https") {
        return Classified(RouteClass.EXTERNAL, trimmed, scheme.ifEmpty { "unknown" })
    }
    val unwrapped = unwrap(uri)
    // The shim's target is web-supplied: it gets the same scheme check as a direct link.
    val innerScheme = unwrapped.scheme?.lowercase().orEmpty()
    if (innerScheme != "http" && innerScheme != "https") {
        return Classified(RouteClass.EXTERNAL, unwrapped.toString(), innerScheme.ifEmpty { "unknown" })
    }
    val host = unwrapped.host?.lowercase().orEmpty()
    if (host == "l.instagram.com") {
        return Classified(RouteClass.EXTERNAL, unwrapped.toString(), "https")
    }
    val path = normalizePath(unwrapped.path)
    for (pattern in rules.patterns) {
        // Remote rules may list hosts, but never outside instagram.com: a route decides what loads inside DMGram.
        val hostOk = if (pattern.hosts.isEmpty()) host in INSTAGRAM_HOSTS else host in pattern.hosts && isInstagramHost(host)
        if (!hostOk) continue
        if (!pattern.regex.matches(path)) continue
        if (pattern.kind == RouteClass.PROFILE && firstSegment(path) in rules.reserved) continue
        val target = unwrapped.toString()
        return Classified(pattern.kind, target, unwrapped.scheme?.lowercase() ?: scheme)
    }
    if (host in INSTAGRAM_HOSTS || host.endsWith(".instagram.com")) {
        return Classified(RouteClass.UNKNOWN, unwrapped.toString(), "https")
    }
    return Classified(RouteClass.EXTERNAL, unwrapped.toString(), "https")
}

internal fun normalizePath(path: String?): String = if (path.isNullOrEmpty()) "/" else path

internal fun firstSegment(path: String): String =
    path.split('/').firstOrNull { it.isNotEmpty() }?.lowercase().orEmpty()

private fun unwrap(uri: URI): URI {
    val host = uri.host?.lowercase() ?: return uri
    if (host != "l.instagram.com") return uri
    val rawQuery = uri.rawQuery ?: return uri
    val encoded = rawQuery.split('&')
        .map { it.split('=', limit = 2) }
        .firstOrNull { it[0] == "u" }
        ?.getOrNull(1)
        ?: return uri
    val decoded = URLDecoder.decode(encoded, StandardCharsets.UTF_8)
    return try {
        URI(decoded)
    } catch (error: URISyntaxException) {
        uri
    }
}

internal fun isInstagramHost(host: String): Boolean = host == "instagram.com" || host.endsWith(".instagram.com")
