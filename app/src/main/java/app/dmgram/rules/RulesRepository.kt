package app.dmgram.rules

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import app.dmgram.BuildConfig
import app.dmgram.DMGramApp
import app.dmgram.net.AppHttp
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Request

class RulesRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun bundled(): String = try {
        context.assets.open("rules.default.json").bufferedReader().use { it.readText() }
    } catch (error: IOException) {
        Log.e(DMGramApp.TAG, "Bundled rules are missing", error)
        """{"schema":1,"version":0,"routes":[],"reserved":[]}"""
    }

    fun url(): String? {
        if (BuildConfig.DEBUG && BuildConfig.RULES_URL.isNotBlank()) return BuildConfig.RULES_URL
        val repo = BuildConfig.GITHUB_REPO
        if (repo.isBlank()) return null
        return "https://raw.githubusercontent.com/$repo/main/rules/rules.json"
    }

    fun due(): Boolean {
        val target = url() ?: return false
        if (BuildConfig.DEBUG && BuildConfig.RULES_URL.isNotBlank()) return true
        return now() - prefs.getLong(KEY_FETCHED, 0L) >= INTERVAL_MS
    }

    suspend fun discardCache() = withContext(Dispatchers.IO) {
        val file = cacheFile()
        if (file.exists() && !file.delete()) Log.e(DMGramApp.TAG, "Could not delete cached rules")
    }

    suspend fun cached(bundledVersion: Int, appVersionCode: Int): String? = withContext(Dispatchers.IO) {
        val file = cacheFile()
        if (!file.exists()) return@withContext null
        val text = try {
            file.readText()
        } catch (error: IOException) {
            Log.e(DMGramApp.TAG, "Could not read cached rules", error)
            return@withContext null
        }
        if (!acceptedRemote(text, bundledVersion, appVersionCode)) {
            Log.i(DMGramApp.TAG, "Cached rules are stale or rejected")
            return@withContext null
        }
        text
    }

    suspend fun fetch(bundledVersion: Int, appVersionCode: Int): String? = withContext(Dispatchers.IO) {
        val target = url() ?: return@withContext null
        val request = Request.Builder()
            .url(target)
            .header("User-Agent", "DMGram/${BuildConfig.VERSION_NAME}")
            .build()
        val call = AppHttp.client.newCall(request)
        coroutineContext[Job]?.invokeOnCompletion { error -> if (error != null) call.cancel() }
        try {
            call.execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) {
                    Log.e(DMGramApp.TAG, "Rules fetch HTTP ${response.code}")
                    return@withContext null
                }
                prefs.edit().putLong(KEY_FETCHED, now()).apply()
                if (!acceptedRemote(body, bundledVersion, appVersionCode)) {
                    val reason = RulesSanitizer.rejection(body) ?: "version"
                    Log.e(DMGramApp.TAG, "Remote rules rejected: $reason")
                    return@withContext null
                }
                writeCache(body)
                body
            }
        } catch (error: IOException) {
            if (!coroutineContext.isActive) return@withContext null
            Log.e(DMGramApp.TAG, "Rules fetch failed", error)
            null
        }
    }

    private fun writeCache(text: String) {
        try {
            val file = cacheFile()
            val tmp = File(file.parentFile, "rules.remote.json.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(file)) file.writeText(text)
        } catch (error: IOException) {
            Log.e(DMGramApp.TAG, "Could not cache remote rules", error)
        }
    }

    private fun cacheFile(): File = File(context.filesDir, "rules.remote.json")

    companion object {
        private const val KEY_FETCHED = "rules.fetchedAt"
        private const val INTERVAL_MS = 6L * 60L * 60L * 1000L
    }
}
