package app.dmgram.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object UpdateDecision {
    private val json = Json { ignoreUnknownKeys = true }

    /** A release asset of this repo on github.com. Anything else could be any host a compromised release names. */
    fun trustedApk(url: String, repo: String): Boolean {
        if (repo.isBlank()) return false
        val prefix = "https://github.com/$repo/releases/download/"
        if (!url.startsWith(prefix)) return false
        val rest = url.removePrefix(prefix)
        return rest.endsWith(".apk", ignoreCase = true) &&
            rest.none { it == '?' || it == '#' || it == '\\' || it == '@' || it.isWhitespace() } &&
            !rest.contains("..")
    }

    fun parse(body: String, currentVersion: String, repo: String): UpdateInfo? {
        val release = try {
            json.decodeFromString<GitHubRelease>(body)
        } catch (error: IllegalArgumentException) {
            return null
        }
        val remote = SemVer.parse(release.tagName) ?: return null
        val current = SemVer.parse(currentVersion) ?: return null
        if (remote <= current) return null
        val apk = release.assets.firstOrNull { asset ->
            trustedApk(asset.browserDownloadUrl, repo)
        }?.browserDownloadUrl.orEmpty()
        if (apk.isBlank()) return null
        return UpdateInfo(
            version = remote.toString(),
            apkUrl = apk,
            releaseUrl = release.htmlUrl,
        )
    }

    @Serializable
    private data class GitHubRelease(
        @SerialName("tag_name") val tagName: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        val assets: List<GitHubAsset> = emptyList(),
    )

    @Serializable
    private data class GitHubAsset(
        val name: String = "",
        @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )
}
