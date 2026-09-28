package app.pepslibrary.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnToReaderTest {
    @Test
    fun nothingAppliesUntilTheLinkedPageHasLoaded() {
        val reader = ReturnToReader(workId = 42)
        assertFalse(reader.appliesAt(0))
        assertFalse(reader.appliesAt(3))
    }

    @Test
    fun backOnTheLinkedPageReturnsToTheBook() {
        val reader = ReturnToReader(workId = 42)
        reader.onHistory(5) // the linked page loads at history entry 5
        assertTrue(reader.appliesAt(5))
    }

    @Test
    fun pagesFollowedFromTheLinkedPageGoBackThroughHistoryFirst() {
        val reader = ReturnToReader(workId = 42)
        reader.onHistory(5)
        reader.onHistory(6) // followed a link from it
        reader.onHistory(7)
        assertFalse(reader.appliesAt(7))
        assertFalse(reader.appliesAt(6))
        assertTrue("back at the linked page", reader.appliesAt(5))
    }

    @Test
    fun aRedirectKeepsTheLinkedPagesPlace() {
        // A work link redirects to its first chapter: a second history update for the same entry.
        val reader = ReturnToReader(workId = 42)
        reader.onHistory(5)
        reader.onHistory(5)
        assertTrue(reader.appliesAt(5))
    }
}
