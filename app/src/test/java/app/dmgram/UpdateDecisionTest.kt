package app.dmgram

import app.dmgram.update.AboutLinks
import app.dmgram.update.UpdateDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDecisionTest {
    @Test
    fun newerReleaseExposesTheApk() {
        val info = UpdateDecision.parse(release("v9.9.9"), "0.1.0")
        assertEquals("9.9.9", info?.version)
        assertEquals("https://example.com/DMGram-v9.9.9.apk", info?.apkUrl)
        assertEquals("https://example.com/release", info?.releaseUrl)
    }

    @Test
    fun olderOrEqualOrApkLessIsIgnored() {
        assertNull(UpdateDecision.parse(release("v0.0.1"), "0.1.0"))
        assertNull(UpdateDecision.parse(release("v0.1.0"), "0.1.0"))
        assertNull(UpdateDecision.parse(release("v9.9.9", apk = null), "0.1.0"))
        assertNull(UpdateDecision.parse("not json", "0.1.0"))
        assertNull(UpdateDecision.parse(release("latest"), "0.1.0"))
    }

    @Test
    fun reportBodyHasVersionsAndNothingPersonal() {
        val body = AboutLinks.reportBody("0.1.0", 1, 3, "15", "153.0.0.0")
        assertTrue(body.contains("App version: 0.1.0 (1)"))
        assertTrue(body.contains("Rules version: 3"))
        assertTrue(body.contains("Android version: 15"))
        assertTrue(body.contains("WebView version: 153.0.0.0"))
        assertFalse(body.contains("example_user"))
        val url = AboutLinks.reportUrl("example/dmgram", body)
        assertTrue(url.startsWith("https://github.com/example/dmgram/issues/new?body="))
    }

    private fun release(tag: String, apk: String? = "https://example.com/DMGram-v9.9.9.apk"): String {
        val assets = if (apk == null) "[]" else """[{"name":"DMGram.apk","browser_download_url":"$apk"}]"""
        return """{"tag_name":"$tag","html_url":"https://example.com/release","assets":$assets}"""
    }
}
