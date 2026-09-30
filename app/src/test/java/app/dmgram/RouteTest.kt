package app.dmgram

import app.dmgram.nav.CompiledRules
import app.dmgram.nav.NavAction
import app.dmgram.nav.Rules
import app.dmgram.nav.decide
import app.dmgram.nav.decideIncoming
import app.dmgram.tabs.Surface
import app.dmgram.tabs.Tab
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    @Test
    fun fixturesMatchTheSharedTable() {
        val rules = CompiledRules(Rules.parse(repoFile("rules/rules.json").readText()))
        val fixtures = json.decodeFromString<List<Fixture>>(repoFile("tools/route-fixtures.json").readText())
        assertTrue("Need at least 50 fixtures", fixtures.size >= 50)
        for (fixture in fixtures) {
            val surface = fixture.surface?.let(Surface::valueOf) ?: Surface.PAGE
            val decision = decide(Tab.valueOf(fixture.tab), fixture.url, rules, surface)
            assertEquals(fixture.url, fixture.expectedClass, decision.route.name)
            assertEquals(fixture.url, fixture.expectedAction, decision.action.name)
            val expectedTab = fixture.expectedTab
            if (expectedTab != null) {
                val switch = decision.action as NavAction.Switch
                assertEquals(fixture.url, Tab.valueOf(expectedTab), switch.tab)
            }
        }
    }

    @Test
    fun incomingIntentsOnlyOpenContentRoutesInApp() {
        val rules = CompiledRules(Rules.parse(repoFile("rules/rules.json").readText()))
        for (url in listOf(
            "https://www.instagram.com/accounts/logout/",
            "https://www.instagram.com/accounts/edit/",
            "https://www.instagram.com/accounts/password/reset/confirm/?uidb36=x&token=y",
            "https://www.instagram.com/challenge/",
            "https://www.instagram.com/api/v1/users/web_profile_info/",
            "https://www.instagram.com/graphql/query/",
            "https://accountscenter.instagram.com/",
        )) {
            val action = decideIncoming(url, rules).action
            assertTrue("$url -> $action", action is NavAction.External)
        }
        for (url in listOf(
            "https://www.instagram.com/",
            "https://www.instagram.com/direct/inbox/",
            "https://www.instagram.com/p/abc123/",
            "https://www.instagram.com/some.user/",
        )) {
            val action = decideIncoming(url, rules).action
            assertTrue("$url -> $action", action == NavAction.Allow || action is NavAction.Switch)
        }
        assertEquals(NavAction.Block("reels"), decideIncoming("https://www.instagram.com/reels/", rules).action)
    }

    private fun repoFile(relative: String): File =
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, relative) }
            .first { it.exists() }

    @Serializable
    private data class Fixture(
        val tab: String,
        val url: String,
        val expectedClass: String,
        val expectedAction: String,
        val expectedTab: String? = null,
        val surface: String? = null,
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
