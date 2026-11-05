package com.wfmarket.app.ui

import androidx.compose.ui.text.font.FontWeight
import com.wfmarket.app.ui.screens.releaseNotesText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {
    private val notes = """
        ### Features
        - Riven prices for every weapon
        * A **pastel** theme, see [the issue](https://example.com/1)

        ### Fixes
        - `Statistics` tab no longer wraps

        **Full Changelog**: https://github.com/orrnithogalum/wfmarket-android/compare/v0.0.0...v0.1.0
    """.trimIndent()

    @Test
    fun listsItemsWithBullets() {
        val text = releaseNotesText(notes).text
        assertEquals(
            "Features\n•  Riven prices for every weapon\n•  A pastel theme, see the issue\nFixes\n•  Statistics tab no longer wraps",
            text,
        )
    }

    @Test
    fun setsHeadingsInBold() {
        val notes = releaseNotesText(notes)
        val bold = notes.spanStyles.filter { it.item.fontWeight == FontWeight.SemiBold }.map { notes.text.substring(it.start, it.end) }
        assertEquals(listOf("Features", "Fixes"), bold)
    }

    @Test
    fun leavesOutTheChangelogLink() {
        assertFalse(releaseNotesText(notes).text.contains("Full Changelog"))
    }

    @Test
    fun emptyNotesStayEmpty() {
        assertTrue(releaseNotesText("").text.isEmpty())
        assertTrue(releaseNotesText("\n\n").text.isEmpty())
    }
}
