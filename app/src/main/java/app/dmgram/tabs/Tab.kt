package app.dmgram.tabs

enum class Tab {
    HOME,
    DMS,
    PROFILE,
    ;

    fun rootUrl(username: String?): String? = when (this) {
        HOME -> "https://www.instagram.com/?variant=following"
        DMS -> "https://www.instagram.com/direct/inbox/"
        PROFILE -> username?.let { "https://www.instagram.com/$it/" }
    }

    fun rootPath(username: String?): String? = when (this) {
        HOME -> "/?variant=following"
        DMS -> "/direct/inbox/"
        PROFILE -> username?.let { "/$it/" }
    }
}

/**
 * What a WebView is for. Home has two: the Following FEED, and a STORIES strip above it that loads
 * Instagram's normal home only for its stories tray. Every other WebView is a plain PAGE.
 */
enum class Surface {
    PAGE,
    FEED,
    STORIES,
}
