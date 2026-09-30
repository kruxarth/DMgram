package app.dmgram.tabs

enum class Tab {
    HOME,
    DMS,
    PROFILE,
    ;

    fun rootUrl(username: String?): String? = when (this) {
        HOME -> "https://www.instagram.com/"
        DMS -> "https://www.instagram.com/direct/inbox/"
        PROFILE -> username?.let { "https://www.instagram.com/$it/" }
    }

    fun rootPath(username: String?): String? = when (this) {
        HOME -> "/"
        DMS -> "/direct/inbox/"
        PROFILE -> username?.let { "/$it/" }
    }
}
