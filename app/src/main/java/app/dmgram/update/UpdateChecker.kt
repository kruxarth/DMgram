package app.dmgram.update

import android.content.SharedPreferences
import android.util.Log
import app.dmgram.BuildConfig
import app.dmgram.DMGramApp
import app.dmgram.net.AppHttp
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request

class UpdateChecker(
    private val prefs: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun url(): String? {
        if (BuildConfig.DEBUG && BuildConfig.UPDATES_URL.isNotBlank()) return BuildConfig.UPDATES_URL
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) return null
        return "https://api.github.com/repos/$repo/releases/latest"
    }

    fun due(): Boolean {
        if (url() == null) return false
        if (BuildConfig.DEBUG && BuildConfig.UPDATES_URL.isNotBlank()) return true
        return now() - prefs.getLong(KEY_FETCHED, 0L) >= INTERVAL_MS
    }

    fun saved(): UpdateInfo? {
        val version = prefs.getString(KEY_VERSION, null)?.takeIf { it.isNotBlank() } ?: return null
        val apk = prefs.getString(KEY_APK, null)
            ?.takeIf { UpdateDecision.trustedApk(it, BuildConfig.GITHUB_REPO) } ?: return null
        val page = prefs.getString(KEY_PAGE, null).orEmpty()
        return UpdateInfo(version, apk, page)
    }

    fun save(info: UpdateInfo?) {
        val editor = prefs.edit()
        if (info == null) {
            editor.remove(KEY_VERSION).remove(KEY_APK).remove(KEY_PAGE)
        } else {
            editor.putString(KEY_VERSION, info.version)
                .putString(KEY_APK, info.apkUrl)
                .putString(KEY_PAGE, info.releaseUrl)
        }
        editor.apply()
    }

    suspend fun fetch(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val target = url() ?: return@withContext null
        val request = Request.Builder()
            .url(target)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DMGram/${BuildConfig.VERSION_NAME}")
            .build()
        val call = AppHttp.client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { error -> if (error != null) call.cancel() }
        try {
            call.execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    Log.e(DMGramApp.TAG, "Update check HTTP ${response.code}")
                    return@withContext null
                }
                prefs.edit().putLong(KEY_FETCHED, now()).apply()
                UpdateDecision.parse(body, currentVersion, BuildConfig.GITHUB_REPO)
            }
        } catch (error: IOException) {
            if (!coroutineContext.isActive) return@withContext null
            Log.e(DMGramApp.TAG, "Update check failed", error)
            null
        }
    }

    companion object {
        private const val KEY_FETCHED = "update.fetchedAt"
        private const val KEY_VERSION = "update.version"
        private const val KEY_APK = "update.apk"
        private const val KEY_PAGE = "update.page"
        private const val INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
