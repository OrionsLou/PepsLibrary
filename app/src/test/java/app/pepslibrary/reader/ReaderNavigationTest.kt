package app.pepslibrary.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderNavigationTest {
    // Shaped like a real AO3 download: preface, an untitled title page, chapters, afterword.
    private val readingOrder = listOf("w_000.xhtml", "w_001.xhtml", "w_002.xhtml", "w_003.xhtml", "w_004.xhtml")
    private val contents = listOf(
        ChapterEntry("Preface", "w_000.xhtml"),
        ChapterEntry("Chapter 1: The Maze", "w_002.xhtml"),
        ChapterEntry("Chapter 2", "w_003.xhtml"),
        ChapterEntry("Afterword", "w_004.xhtml"),
    )

    @Test
    fun eachFileBelongsToTheLastContentsEntryAtOrBeforeIt() {
        assertEquals(
            mapOf(
                "w_000.xhtml" to "Preface",
                "w_001.xhtml" to "Preface",
                "w_002.xhtml" to "Chapter 1: The Maze",
                "w_003.xhtml" to "Chapter 2",
                "w_004.xhtml" to "Afterword",
            ),
            chapterTitlesByFile(readingOrder, contents),
        )
    }

    @Test
    fun filesBeforeTheFirstEntryHaveNoChapter() {
        val titles = chapterTitlesByFile(readingOrder, contents.drop(1))
        assertNull(titles["w_000.xhtml"])
        assertNull(titles["w_001.xhtml"])
        assertEquals("Chapter 1: The Maze", titles["w_002.xhtml"])
    }

    @Test
    fun severalEntriesInOneFileUseTheFirst() {
        val titles = chapterTitlesByFile(listOf("a.xhtml"), listOf(ChapterEntry("Part One", "a.xhtml"), ChapterEntry("Scene 2", "a.xhtml")))
        assertEquals("Part One", titles["a.xhtml"])
    }

    @Test
    fun percentRoundsLikeTheTopBarAndStaysInRange() {
        assertEquals(9, percentOf(0.0909))
        assertEquals(12, percentOf(0.116))
        assertEquals(100, percentOf(1.0000001))
        assertEquals(0, percentOf(-0.01))
        assertNull(percentOf(null))
        assertNull(percentOf(Double.NaN))
    }

    @Test
    fun theLabelShowsWhateverIsKnown() {
        assertEquals("Chapter 7: Title · 42%", positionLabel("Chapter 7: Title", 42))
        assertEquals("42%", positionLabel(null, 42))
        assertEquals("Chapter 7: Title", positionLabel("Chapter 7: Title", null))
    }
}
