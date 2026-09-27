package app.pepslibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryViewPrefsTest {
    private fun roundTrip(order: LibraryOrder, filter: LibraryFilter): Pair<LibraryOrder, LibraryFilter> {
        val saved = SavedLibraryView.of(order, filter)
        return saved.toOrder() to saved.toFilter()
    }

    @Test
    fun nothingSavedYetGivesTheDefaults() {
        assertEquals(LibraryOrder(), SavedLibraryView().toOrder())
        assertEquals(LibraryFilter(), SavedLibraryView().toFilter())
    }

    @Test
    fun everySortAndDirectionSurvives() {
        LibrarySort.entries.forEach { sort ->
            listOf(false, true).forEach { reversed ->
                val order = LibraryOrder(sort, reversed)
                assertEquals(order, roundTrip(order, LibraryFilter()).first)
            }
        }
    }

    @Test
    fun aFullFilterSurvives_includingNoneListedAndPinnedOnly() {
        val filter = LibraryFilter(
            authors = setOf("Spect3rr", NONE_LISTED),
            statuses = CompletionStatus.entries.toSet(),
            fandoms = setOf("Batman (Comics)", "Harry Potter - J. K. Rowling"),
            pinnedOnly = true,
        )
        assertEquals(filter, roundTrip(LibraryOrder(), filter).second)
    }

    @Test
    fun anUnknownSortFallsBackToTheDefaultOrder_withoutItsDirection() {
        assertEquals(LibraryOrder(), SavedLibraryView(sort = "WORD_COUNT", reversed = true).toOrder())
    }

    @Test
    fun anUnknownStatusIsDropped_andTheRestOfTheFilterKept() {
        val filter = SavedLibraryView(
            authors = setOf("Spect3rr"),
            statuses = setOf("COMPLETED", "ABANDONED"),
            pinnedOnly = true,
        ).toFilter()

        assertEquals(
            LibraryFilter(authors = setOf("Spect3rr"), statuses = setOf(CompletionStatus.COMPLETED), pinnedOnly = true),
            filter,
        )
    }
}
