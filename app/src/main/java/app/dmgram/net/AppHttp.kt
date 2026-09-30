package app.dmgram.net

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

object AppHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}
