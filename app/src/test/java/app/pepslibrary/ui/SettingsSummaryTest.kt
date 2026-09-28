package app.pepslibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsSummaryTest {
    @Test
    fun theDefaultViewSaysSo() {
        assertEquals(
            listOf("Sort: Last read, most recent first", "No filters"),
            libraryViewSummary(LibraryOrder(), LibraryFilter()),
        )
        assertTrue(isDefaultLibraryView(LibraryOrder(), LibraryFilter()))
    }

    @Test
    fun everyActiveFilterIsListedInTheSheetsOrder() {
        val filter = LibraryFilter(
            pinnedOnly = true,
            statuses = setOf(CompletionStatus.WIP, CompletionStatus.COMPLETED),
            fandoms = setOf("Harry Potter - J. K. Rowling", "Batman (Comics)"),
            authors = setOf("Spect3rr", NONE_LISTED),
        )
        assertEquals(
            "Filters: Pinned only, Completed, Work in progress, Batman (Comics), Harry Potter - J. K. Rowling, " +
                "No author listed, Spect3rr",
            libraryViewSummary(LibraryOrder(), filter)[1],
        )
    }

    @Test
    fun aReversedOrAnotherSortCountsAsChanged() {
        assertEquals("Sort: Title, Z–A", libraryViewSummary(LibraryOrder(LibrarySort.TITLE, reversed = true), LibraryFilter())[0])
        assertFalse(isDefaultLibraryView(LibraryOrder(LibrarySort.TITLE), LibraryFilter()))
        assertFalse(isDefaultLibraryView(LibraryOrder(reversed = true), LibraryFilter()))
        assertFalse(isDefaultLibraryView(LibraryOrder(), LibraryFilter(pinnedOnly = true)))
    }

    @Test
    fun theMeowNoteIsWordForWord() {
        assertEquals(
            "Made this for me wife so that I may smother her with physical affection of which she is allergic to.",
            MEOW_NOTE,
        )
    }

    // Gradle runs unit tests from the module directory, so the bundled file is reachable by its source path.
    private val licence = File("src/main/res/raw/literata_ofl.txt").readText()

    @Test
    fun reflowingTheLicenceKeepsEveryWordInOrder() {
        val words = { text: String -> text.split(Regex("\\s+")).filter { it.isNotEmpty() } }
        assertEquals(words(licence), words(reflowLicence(licence)))
    }

    @Test
    fun reflowingJoinsWrappedLinesButKeepsHeadingsAndClausesOnTheirOwn() {
        val shown = reflowLicence(licence).lines()
        assertTrue("PREAMBLE" in shown)
        assertTrue("DEFINITIONS" in shown)
        assertTrue(shown.any { it.startsWith("1) Neither the Font Software nor any of its individual components,") })
        // The preamble's first sentence was wrapped over three lines in the file; now it's one.
        assertTrue(shown.any { it.startsWith("The goals of the Open Font License (OFL) are to stimulate worldwide development of") })
    }
}
