package app.dmgram.net

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.ResponseBody

object AppHttp {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
}

/** The body as text, or null when it is longer than [maxBytes]: never buffers more than that. */
fun ResponseBody.cappedString(maxBytes: Long): String? {
    val source = source()
    source.request(maxBytes + 1)
    if (source.buffer.size > maxBytes) return null
    return source.buffer.readUtf8()
}
