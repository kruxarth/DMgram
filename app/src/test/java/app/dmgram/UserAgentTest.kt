package app.dmgram

import app.dmgram.web.UserAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserAgentTest {
    @Test
    fun parsesChromeMajorFromWebViewUserAgent() {
        val ua = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/131.0.6778.200 Mobile Safari/537.36"
        assertEquals("131", UserAgent.chromeMajorFrom(ua))
    }

    @Test
    fun returnsNullWhenChromeTokenIsMissing() {
        assertNull(UserAgent.chromeMajorFrom("Mozilla/5.0 (Linux; Android 10; K)"))
    }

    @Test
    fun buildsReducedChromeUserAgent() {
        assertEquals(
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/131.0.0.0 Mobile Safari/537.36",
            UserAgent.reducedChromeUserAgent("131"),
        )
    }
}
