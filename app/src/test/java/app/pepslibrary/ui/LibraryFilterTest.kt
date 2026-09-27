package app.pepslibrary.ui

import app.pepslibrary.data.WorkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFilterTest {
    private fun work(id: Long, vararg authors: String, published: Int? = null, total: Int? = null) = WorkEntity(
        workId = id, title = "Work $id", authors = authors.toList(), summary = null, rating = null,
        warnings = emptyList(), categories = emptyList(), fandoms = emptyList(), relationships = emptyList(),
        characters = emptyList(), tags = emptyList(), language = null, words = null, chaptersPublished = published,
        chaptersTotal = total, publishedDate = null, updatedDate = null, sourceUpdatedAt = null,
        epubFileName = "$id.epub", fileSizeBytes = 0, downloadedAt = id,
    )

    // Complete, WIP with a planned total, WIP with an unknown total ("3/?"), and unreadable chapter counts.
    private val solo = work(1, "Spect3rr", published = 3, total = 3)
    private val coWritten = work(2, "Zed", "Spect3rr", published = 3, total = 10)
    private val other = work(3, "émile", published = 3, total = null)
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
    fun completionStatusMatchesTheCardsRule() {
        assertEquals(CompletionStatus.COMPLETED, completionStatus(solo))
        assertEquals(CompletionStatus.WIP, completionStatus(coWritten))
        assertEquals(CompletionStatus.WIP, completionStatus(other)) // "3/?"
        assertEquals(CompletionStatus.OTHER, completionStatus(unsigned))
        assertEquals(CompletionStatus.COMPLETED, completionStatus(work(9, published = 4, total = 3)))
    }

    @Test
    fun statusFiltersMatchAnySelectedStatus() {
        assertEquals(listOf(1L), shown(LibraryFilter(statuses = setOf(CompletionStatus.COMPLETED))))
        assertEquals(listOf(2L, 3L), shown(LibraryFilter(statuses = setOf(CompletionStatus.WIP))))
        assertEquals(listOf(4L), shown(LibraryFilter(statuses = setOf(CompletionStatus.OTHER))))
        assertEquals(
            listOf(1L, 4L),
            shown(LibraryFilter(statuses = setOf(CompletionStatus.COMPLETED, CompletionStatus.OTHER))),
        )
    }

    @Test
    fun differentKindsCombine_everyActiveKindMustMatch() {
        val spect3rrWips = LibraryFilter(authors = setOf("Spect3rr"), statuses = setOf(CompletionStatus.WIP))
        assertEquals(listOf(2L), shown(spect3rrWips))
        assertEquals(2, spect3rrWips.selectedCount)
        assertEquals(emptyList<Long>(), shown(spect3rrWips.toggleStatus(CompletionStatus.WIP).toggleStatus(CompletionStatus.OTHER)))
    }

    @Test
    fun statusOptionsListAllThreeInOrder_evenAtZero() {
        assertEquals(
            listOf(
                FilterOption("COMPLETED", "Completed", 1),
                FilterOption("WIP", "Work in progress", 2),
                FilterOption("OTHER", "Other (chapter count unknown)", 1),
            ),
            statusOptions(library),
        )
        assertEquals(listOf(1, 0, 0), statusOptions(listOf(solo)).map { it.count })
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
