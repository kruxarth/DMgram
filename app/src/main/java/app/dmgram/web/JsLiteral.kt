package app.dmgram.web

internal fun jsQuote(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '<' -> append("\\u003c")
            '\u2028' -> append("\\u2028")
            '\u2029' -> append("\\u2029")
            else -> append(ch)
        }
    }
    append('"')
}
