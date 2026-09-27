package app.pepslibrary.epub

import org.junit.Assert.assertEquals
import org.junit.Test

class PositionUpdateTest {
    private fun ch(index: Int, heading: String?, content: String) =
        EpubChapter("Work_split_%03d.xhtml".format(index), "hash:$content", heading)

    /** Laid out the way Calibre numbers AO3's files: preface, title page, then one file per chapter. */
    private fun book(vararg chapters: Pair<String, String>, stats: String = "v1"): List<EpubChapter> =
        listOf(ch(0, "Preface", "preface $stats"), ch(1, "The Work", "title")) +
            chapters.mapIndexed { i, (heading, content) -> ch(i + 2, heading, content) }

    private val v1 = book("Chapter 1" to "one", "Chapter 2" to "two", "Chapter 3" to "three")

    @Test
    fun newChaptersAppendedKeepTheExactPosition() {
        val v2 = book("Chapter 1" to "one", "Chapter 2" to "two", "Chapter 3" to "three", "Chapter 4" to "four", stats = "v2")
        assertEquals(PositionUpdate.Keep, reconcilePosition("Work_split_003.xhtml", v1, v2))
    }

    @Test
    fun anEditToAnotherChapterKeepsTheExactPosition() {
        val v2 = book("Chapter 1" to "one, revised", "Chapter 2" to "two", "Chapter 3" to "three")
        assertEquals(PositionUpdate.Keep, reconcilePosition("Work_split_003.xhtml", v1, v2))
    }

    @Test
    fun anEarlierChapterRemovedMovesTheSameSpotToTheRenumberedFile() {
        // Chapter 1 is gone, so "two" now sits in split_002 and split_003 holds different content.
        val v2 = book("Chapter 1" to "two", "Chapter 2" to "three")
        assertEquals(PositionUpdate.Moved("Work_split_002.xhtml"), reconcilePosition("Work_split_003.xhtml", v1, v2))
    }

    @Test
    fun anEarlierChapterInsertedMovesTheSameSpotToTheRenumberedFile() {
        val v2 = book("Prologue" to "zero", "Chapter 1" to "one", "Chapter 2" to "two", "Chapter 3" to "three")
        assertEquals(PositionUpdate.Moved("Work_split_004.xhtml"), reconcilePosition("Work_split_003.xhtml", v1, v2))
    }

    @Test
    fun theCurrentChapterEditedGoesToItsStart() {
        val v2 = book("Chapter 1" to "one", "Chapter 2" to "two, rewritten", "Chapter 3" to "three")
        assertEquals(PositionUpdate.ChapterStart("Work_split_003.xhtml"), reconcilePosition("Work_split_003.xhtml", v1, v2))
    }

    @Test
    fun theCurrentChapterEditedAndRenumberedIsFoundByItsHeading() {
        val v2 = book("Chapter 2" to "two, rewritten", "Chapter 3" to "three")
        assertEquals(PositionUpdate.ChapterStart("Work_split_002.xhtml"), reconcilePosition("Work_split_003.xhtml", v1, v2))
    }

    @Test
    fun theCurrentChapterRemovedGoesToTheStartOfTheWork() {
        val v2 = book("Chapter 1" to "one", "Chapter 3" to "three")
        assertEquals(PositionUpdate.WorkStart("Work_split_000.xhtml"), reconcilePosition("Work_split_003.xhtml", v1, v2))
    }

    @Test
    fun readingThePrefaceWhenItsStatsChangeGoesToItsStart() {
        val v2 = book("Chapter 1" to "one", "Chapter 2" to "two", "Chapter 3" to "three", stats = "v2")
        assertEquals(PositionUpdate.ChapterStart("Work_split_000.xhtml"), reconcilePosition("Work_split_000.xhtml", v1, v2))
    }

    @Test
    fun aPositionTheOldBookDidNotListIsKeptIfItsFileStillExists() {
        assertEquals(PositionUpdate.Keep, reconcilePosition("Work_split_002.xhtml", emptyList(), v1))
        assertEquals(PositionUpdate.WorkStart("Work_split_000.xhtml"), reconcilePosition("gone.xhtml", emptyList(), v1))
    }

    @Test
    fun anEmptyNewBookLeavesThePositionAlone() {
        assertEquals(PositionUpdate.Keep, reconcilePosition("Work_split_003.xhtml", v1, emptyList()))
    }
}
