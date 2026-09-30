package app.dmgram

import app.dmgram.update.SemVer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    @Test
    fun parsesALeadingV() {
        assertEquals(SemVer(9, 9, 9), SemVer.parse("v9.9.9"))
        assertEquals(SemVer(0, 1, 0), SemVer.parse("0.1.0"))
    }

    @Test
    fun ordersMajorMinorPatch() {
        assertTrue(SemVer.parse("0.1.0")!! < SemVer.parse("0.2.0")!!)
        assertTrue(SemVer.parse("0.9.9")!! < SemVer.parse("1.0.0")!!)
        assertTrue(SemVer.parse("1.2.3")!! > SemVer.parse("1.2.2")!!)
        assertEquals(0, SemVer.parse("v1.2.3")!!.compareTo(SemVer.parse("1.2.3")!!))
    }

    @Test
    fun rejectsIncompleteVersions() {
        assertNull(SemVer.parse("latest"))
        assertNull(SemVer.parse("1.2"))
        assertNull(SemVer.parse(""))
    }
}
