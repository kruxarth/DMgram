package app.dmgram.rules

import app.dmgram.nav.Rules

object RulesSanitizer {
    const val MAX_BYTES = 256 * 1024

    private val bannedCss = listOf(
        "url(",
        "@import",
        "image-set(",
        "@font-face",
        "expression(",
        "-moz-binding",
        "behavior:",
    )

    fun rejection(text: String): String? {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return "large"
        val rules = try {
            Rules.parse(text)
        } catch (error: IllegalArgumentException) {
            return "unreadable"
        }
        if (rules.schema != 1) return "schema"
        if (cssRejected(rules.css)) return "css"
        for (rule in rules.hide) {
            val selector = rule.selector
            if (selector.length > 500 || selector.contains('{') || selector.contains('}')) return "selector"
        }
        for (route in rules.routes) {
            if (route.pattern.length > 300 || !validRegex(route.pattern)) return "pattern"
        }
        return null
    }

    private fun cssRejected(css: String): Boolean {
        if (css.contains('\\')) return true
        val lower = css.lowercase()
        return bannedCss.any { lower.contains(it) }
    }

    private fun validRegex(pattern: String): Boolean = try {
        Regex(pattern)
        true
    } catch (error: IllegalArgumentException) {
        false
    }
}

fun acceptedRemote(text: String, bundledVersion: Int, appVersionCode: Int): Boolean {
    if (RulesSanitizer.rejection(text) != null) return false
    val rules = Rules.parse(text)
    return rules.version > bundledVersion && rules.minAppVersionCode <= appVersionCode
}
