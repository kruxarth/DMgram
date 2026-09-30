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
        val info = UpdateDecision.parse(release("v9.9.9"), "0.1.0", REPO)
        assertEquals("9.9.9", info?.version)
        assertEquals(APK, info?.apkUrl)
        assertEquals("https://example.com/release", info?.releaseUrl)
    }

    @Test
    fun olderOrEqualOrApkLessIsIgnored() {
        assertNull(UpdateDecision.parse(release("v0.0.1"), "0.1.0", REPO))
        assertNull(UpdateDecision.parse(release("v0.1.0"), "0.1.0", REPO))
        assertNull(UpdateDecision.parse(release("v9.9.9", apk = null), "0.1.0", REPO))
        assertNull(UpdateDecision.parse("not json", "0.1.0", REPO))
        assertNull(UpdateDecision.parse(release("latest"), "0.1.0", REPO))
    }

    @Test
    fun apkMustBeAReleaseAssetOfThisRepo() {
        assertNull(UpdateDecision.parse(release("v9.9.9", apk = "https://evil.example/DMGram.apk"), "0.1.0", REPO))
        assertNull(UpdateDecision.parse(release("v9.9.9", apk = "https://github.com/other/repo/releases/download/v9.9.9/DMGram.apk"), "0.1.0", REPO))
        assertNull(UpdateDecision.parse(release("v9.9.9", apk = "http://github.com/$REPO/releases/download/v9.9.9/DMGram.apk"), "0.1.0", REPO))
        assertNull(UpdateDecision.parse(release("v9.9.9", apk = "https://github.com/$REPO/releases/download/../../../evil/x.apk"), "0.1.0", REPO))
        assertNull(UpdateDecision.parse(release("v9.9.9"), "0.1.0", ""))
        assertFalse(UpdateDecision.trustedApk("https://github.com/$REPO/releases/download/v1/x.apk?u=https://evil", REPO))
        assertTrue(UpdateDecision.trustedApk(APK, REPO))
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

    private fun release(tag: String, apk: String? = APK): String {
        val assets = if (apk == null) "[]" else """[{"name":"DMGram.apk","browser_download_url":"$apk"}]"""
        return """{"tag_name":"$tag","html_url":"https://example.com/release","assets":$assets}"""
    }

    private companion object {
        const val REPO = "example/dmgram"
        const val APK = "https://github.com/example/dmgram/releases/download/v9.9.9/DMGram-v9.9.9.apk"
    }
}
