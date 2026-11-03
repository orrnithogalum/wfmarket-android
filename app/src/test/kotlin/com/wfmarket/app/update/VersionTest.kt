package com.wfmarket.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {
    private fun v(text: String) = requireNotNull(Version.parse(text)) { text }

    @Test
    fun readsTagsWithOrWithoutTheirV() {
        assertEquals(Version(1, 2, 3), v("v1.2.3"))
        assertEquals(Version(0, 0, 0), v("0.0.0"))
        assertEquals(Version(1, 0, 0, listOf("beta", "2")), v("1.0.0-beta.2"))
        assertEquals(Version(1, 0, 0), v("1.0.0+build.7"))
    }

    @Test
    fun rejectsWhatIsNotAVersion() {
        assertNull(Version.parse("latest"))
        assertNull(Version.parse("1.0"))
        assertNull(Version.parse("v1.0.0.1"))
    }

    @Test
    fun comparesNumbersAsNumbers() {
        assertTrue(v("1.2.10") > v("1.2.9"))
        assertTrue(v("0.10.0") > v("0.9.9"))
        assertTrue(v("1.0.0") > v("0.99.99"))
    }

    @Test
    fun putsPreReleasesBeforeTheirRelease() {
        assertTrue(v("1.0.0-beta.1") < v("1.0.0"))
        assertTrue(v("1.0.0-alpha") < v("1.0.0-alpha.1"))
        assertTrue(v("1.0.0-alpha.1") < v("1.0.0-alpha.beta"))
        assertTrue(v("1.0.0-beta.2") < v("1.0.0-beta.11"))
        assertTrue(v("1.0.0-rc.1") > v("0.9.0"))
    }

    @Test
    fun printsTheVersionBackWithoutItsV() {
        assertEquals("1.4.0-rc.1", v("v1.4.0-rc.1").toString())
    }
}
