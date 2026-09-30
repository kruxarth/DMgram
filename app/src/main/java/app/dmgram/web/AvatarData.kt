package app.dmgram.web

import java.util.Base64

/** Validates the avatar the page sends: a small base64 JPEG data URL, nothing else. */
object AvatarData {
    private const val PREFIX = "data:image/jpeg;base64,"
    const val MAX_BYTES = 5_000

    fun decode(data: String): ByteArray? {
        if (!data.startsWith(PREFIX)) return null
        val bytes = try {
            Base64.getDecoder().decode(data.substring(PREFIX.length))
        } catch (error: IllegalArgumentException) {
            return null
        }
        if (bytes.size < 4 || bytes.size > MAX_BYTES) return null
        // JPEG SOI marker.
        if (bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) return null
        return bytes
    }
}
