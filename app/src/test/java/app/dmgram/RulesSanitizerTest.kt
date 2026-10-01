package app.dmgram

import app.dmgram.nav.Rules
import app.dmgram.rules.RulesSanitizer
import app.dmgram.rules.acceptedRemote
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesSanitizerTest {
    @Test
    fun bundledRulesPass() {
        val text = repoFile("rules/rules.json").readText()
        val rules = Rules.parse(text)
        assertEquals(1, rules.schema)
        assertTrue(rules.version >= 1)
        for (rule in rules.hide) assertTrue(rule.selector.isNotBlank())
        assertNull(RulesSanitizer.rejection(text))
    }

    @Test
    fun acceptsANewerCleanFile() {
        val text = rules(version = 2, css = "article{outline:1px solid transparent}")
        assertNull(RulesSanitizer.rejection(text))
        assertTrue(acceptedRemote(text, bundledVersion = 1, appVersionCode = 1))
    }

    @Test
    fun rejectsAnOlderOrTooNewFile() {
        assertFalse(acceptedRemote(rules(version = 1), bundledVersion = 1, appVersionCode = 1))
        assertFalse(acceptedRemote(rules(version = 3, minApp = 99), bundledVersion = 1, appVersionCode = 1))
    }

    @Test
    fun rejectsRoutesForOtherHosts() {
        val text = """{"schema":1,"version":2,"routes":[{"class":"POST","pattern":"^/.*","hosts":["evil.example"]}]}"""
        assertEquals("host", RulesSanitizer.rejection(text))
        assertFalse(acceptedRemote(text, bundledVersion = 1, appVersionCode = 1))
    }

    @Test
    fun rejectsMaliciousCss() {
        assertEquals("css", RulesSanitizer.rejection(rules(css = "body{background:url(https://evil)}")))
        assertEquals("css", RulesSanitizer.rejection(rules(css = "@import 'https://evil';")))
        assertEquals("css", RulesSanitizer.rejection(rules(css = "a{content:\"\\\"")))
        assertEquals("css", RulesSanitizer.rejection(rules(css = "div{image-set(url(a) 1x)}")))
        assertEquals("css", RulesSanitizer.rejection(rules(css = "@font-face{font-family:x}")))
        assertEquals("css", RulesSanitizer.rejection(rules(css = "a{xss:expression(alert(1))}")))
        assertEquals("css", RulesSanitizer.rejection(rules(css = "a{-moz-binding:url(x)}")))
        assertEquals("css", RulesSanitizer.rejection(rules(css = "a{behavior:url(x)}")))
        assertEquals("css", RulesSanitizer.rejection(rules(css = "a{background:src(\"https://evil\")}")))
        assertEquals("css", RulesSanitizer.rejection(rules(css = "a{background:-webkit-image-set(\"https://evil\" 1x)}")))
        assertEquals("css", RulesSanitizer.rejection(rules(css = "a{background:URL(https://evil)}")))
    }

    @Test
    fun selectorsCannotSmuggleCss() {
        // Selectors are joined into CSS text ahead of `css`, so this was a working remote @import.
        assertEquals("selector", RulesSanitizer.rejection(rules(selector = "@import url(https://evil/x.css);a")))
        assertEquals("selector", RulesSanitizer.rejection(rules(selector = "@import 'https://evil/x.css';a")))
        assertEquals("selector", RulesSanitizer.rejection(rules(selector = "a[href^=x]::after;b")))
        assertEquals("selector", RulesSanitizer.rejection(rules(selector = "a\\7b")))
        assertNull(RulesSanitizer.rejection(rules(selector = "div[role='dialog'] > a[href^='/explore/']")))
    }

    @Test
    fun rejectsUnknownRouteClassesSoTheyCannotCrashStartup() {
        val text = """{"schema":1,"version":2,"routes":[{"class":"NOPE","pattern":"^/$"}]}"""
        assertEquals("class", RulesSanitizer.rejection(text))
        assertFalse(acceptedRemote(text, bundledVersion = 1, appVersionCode = 1))
    }

    @Test
    fun rejectsBadSelectorsPatternsAndSize() {
        assertEquals("selector", RulesSanitizer.rejection(rules(selector = "div{color:red}")))
        assertEquals("selector", RulesSanitizer.rejection(rules(selector = "a".repeat(501))))
        assertEquals("pattern", RulesSanitizer.rejection(rules(pattern = "(?")))
        assertEquals("pattern", RulesSanitizer.rejection(rules(pattern = "a".repeat(301))))
        assertEquals("schema", RulesSanitizer.rejection(rules(schema = 2)))
        assertEquals("large", RulesSanitizer.rejection(" ".repeat(RulesSanitizer.MAX_BYTES + 1)))
        assertEquals("unreadable", RulesSanitizer.rejection("{"))
    }

    private fun rules(
        version: Int = 2,
        schema: Int = 1,
        minApp: Int = 1,
        css: String = "",
        selector: String = "article",
        pattern: String = "^/\$",
    ): String = """
        {
          "schema": $schema,
          "version": $version,
          "minAppVersionCode": $minApp,
          "routes": [ { "class": "HOME_FEED", "pattern": ${jsonString(pattern)} } ],
          "reserved": [],
          "hide": [ { "id": "sample", "selector": ${jsonString(selector)} } ],
          "css": ${jsonString(css)}
        }
    """.trimIndent()

    private fun jsonString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun repoFile(relative: String): File =
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it, relative) }
            .first { it.exists() }
}
