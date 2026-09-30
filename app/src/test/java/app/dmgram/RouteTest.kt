package app.dmgram

import app.dmgram.nav.CompiledRules
import app.dmgram.nav.NavAction
import app.dmgram.nav.Rules
import app.dmgram.nav.decide
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
            val decision = decide(Tab.valueOf(fixture.tab), fixture.url, rules)
            assertEquals(fixture.url, fixture.expectedClass, decision.route.name)
            assertEquals(fixture.url, fixture.expectedAction, decision.action.name)
            val expectedTab = fixture.expectedTab
            if (expectedTab != null) {
                val switch = decision.action as NavAction.Switch
                assertEquals(fixture.url, Tab.valueOf(expectedTab), switch.tab)
            }
        }
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
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}
