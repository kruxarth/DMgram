package app.dmgram

import app.dmgram.web.AvatarData
import java.util.Base64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarDataTest {
    private fun url(bytes: ByteArray, type: String = "jpeg") =
        "data:image/$type;base64," + Base64.getEncoder().encodeToString(bytes)

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 1, 2, 3)

    @Test
    fun acceptsSmallJpeg() {
        assertArrayEquals(jpeg, AvatarData.decode(url(jpeg)))
    }

    @Test
    fun rejectsOtherTypesAndPayloads() {
        assertNull(AvatarData.decode(url(jpeg, "png")))
        assertNull(AvatarData.decode(url(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))))
        assertNull(AvatarData.decode("data:image/jpeg;base64,@@@"))
        assertNull(AvatarData.decode("https://example.com/a.jpg"))
    }

    @Test
    fun rejectsOversized() {
        val big = ByteArray(AvatarData.MAX_BYTES + 1).also { it[0] = 0xFF.toByte(); it[1] = 0xD8.toByte() }
        assertNull(AvatarData.decode(url(big)))
    }
}
