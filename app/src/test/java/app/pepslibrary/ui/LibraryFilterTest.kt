package app.pepslibrary.ui

import app.pepslibrary.data.WorkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTest {
    private fun work(id: Long, vararg authors: String) = WorkEntity(
        workId = id, title = "Work $id", authors = authors.toList(), summary = null, rating = null,
        warnings = emptyList(), categories = emptyList(), fandoms = emptyList(), relationships = emptyList(),
        characters = emptyList(), tags = emptyList(), language = null, words = null, chaptersPublished = null,
        chaptersTotal = null, publishedDate = null, updatedDate = null, sourceUpdatedAt = null,
        epubFileName = "$id.epub", fileSizeBytes = 0, downloadedAt = id,
    )

    private val solo = work(1, "Spect3rr")
    private val coWritten = work(2, "Zed", "Spect3rr")
    private val other = work(3, "émile")
    private val unsigned = work(4)
    private val library = listOf(solo, coWritten, other, unsigned)

    private fun shown(filter: LibraryFilter) = library.filter(filter::matches).map { it.workId }

    @Test
    fun noSelectionShowsEverything() {
        assertFalse(LibraryFilter().isActive)
        assertEquals(listOf(1L, 2L, 3L, 4L), shown(LibraryFilter()))
    }

    @Test
    fun anAuthorMatchesEveryWorkListingThem_includingCoWrittenOnes() {
        assertEquals(listOf(1L, 2L), shown(LibraryFilter(authors = setOf("Spect3rr"))))
        assertEquals(listOf(2L), shown(LibraryFilter(authors = setOf("Zed"))))
    }

    @Test
    fun severalAuthorsMatchWorksByAnyOfThem() {
        assertEquals(listOf(2L, 3L), shown(LibraryFilter(authors = setOf("Zed", "émile"))))
    }

    @Test
    fun noAuthorListedIsItsOwnChoice() {
        assertEquals(listOf(4L), shown(LibraryFilter(authors = setOf(NO_AUTHOR))))
    }

    @Test
    fun togglingAddsThenRemoves_andCountsSelections() {
        val one = LibraryFilter().toggleAuthor("Zed")
        assertTrue(one.isActive)
        assertEquals(1, one.selectedCount)
        assertEquals(2, one.toggleAuthor("émile").selectedCount)
        assertEquals(LibraryFilter(), one.toggleAuthor("Zed"))
    }

    @Test
    fun optionsListEachAuthorAlphabeticallyWithCounts_noAuthorLast() {
        assertEquals(
            listOf(
                FilterOption("émile", "émile", 1),
                FilterOption("Spect3rr", "Spect3rr", 2),
                FilterOption("Zed", "Zed", 1),
                FilterOption(NO_AUTHOR, "No author listed", 1),
            ),
            authorOptions(library, LibraryFilter()),
        )
    }

    @Test
    fun theWorkCountSaysHowManyAreShownWhileFiltering() {
        assertEquals("4 works", workCountLabel(shown = 4, total = 4, filtered = false))
        assertEquals("2 of 4 works", workCountLabel(shown = 2, total = 4, filtered = true))
        assertEquals("1 work", workCountLabel(shown = 1, total = 1, filtered = false))
    }

    @Test
    fun aSelectedAuthorWithNoWorksLeftIsStillOffered_soItCanBeUnchecked() {
        val options = authorOptions(listOf(solo), LibraryFilter(authors = setOf("Gone")))
        assertEquals(listOf(FilterOption("Gone", "Gone", 0), FilterOption("Spect3rr", "Spect3rr", 1)), options)
    }
}
