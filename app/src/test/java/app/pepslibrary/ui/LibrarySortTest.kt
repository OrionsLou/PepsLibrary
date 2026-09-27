package app.pepslibrary.ui

import app.pepslibrary.data.WorkEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {
    private fun work(
        id: Long,
        title: String,
        downloadedAt: Long,
        authors: List<String> = emptyList(),
        lastOpenedAt: Long? = null,
    ) = WorkEntity(
        workId = id, title = title, authors = authors, summary = null, rating = null, warnings = emptyList(),
        categories = emptyList(), fandoms = emptyList(), relationships = emptyList(), characters = emptyList(),
        tags = emptyList(), language = null, words = null, chaptersPublished = null, chaptersTotal = null,
        publishedDate = null, updatedDate = null, sourceUpdatedAt = null, epubFileName = "$id.epub",
        fileSizeBytes = 0, downloadedAt = downloadedAt, lastOpenedAt = lastOpenedAt,
    )

    // Two opened (the older-downloaded one opened most recently), two never opened.
    private val opened = listOf(
        work(1, "Read last week", downloadedAt = 10, lastOpenedAt = 100),
        work(2, "Never opened, new", downloadedAt = 40),
        work(3, "Reading now", downloadedAt = 5, lastOpenedAt = 300),
        work(4, "Never opened, old", downloadedAt = 20),
    )

    @Test
    fun lastOpenedIsTheDefault() {
        assertEquals(LibraryOrder(LibrarySort.OPENED, reversed = false), LibraryOrder())
        assertEquals("Last read, most recent first", LibraryOrder().label)
        assertEquals(LibrarySort.OPENED, LibrarySort.entries.first())
    }

    @Test
    fun byLastOpenedMostRecentFirst_neverOpenedLastByNewestDownload() {
        assertEquals(
            listOf("Reading now", "Read last week", "Never opened, new", "Never opened, old"),
            titles(opened, LibrarySort.OPENED),
        )
    }

    @Test
    fun byLastOpenedReversedFlipsTheOpenedOnes_butNeverOpenedStayLast() {
        assertEquals(
            listOf("Read last week", "Reading now", "Never opened, new", "Never opened, old"),
            titles(opened, LibrarySort.OPENED, reversed = true),
        )
        assertEquals("Last read, least recent first", LibraryOrder(LibrarySort.OPENED, reversed = true).label)
    }

    private val works = listOf(
        work(1, "bulbs in the dirt", downloadedAt = 30),
        work(2, "The Summers of Draco Malfoy", downloadedAt = 50),
        work(3, "Álbum", downloadedAt = 10),
        work(4, "A Quiet Place", downloadedAt = 40),
    )

    private val authored = listOf(
        work(1, "Zebra", downloadedAt = 1, authors = listOf("spect3rr")),
        work(2, "No byline", downloadedAt = 9),
        work(3, "Apple", downloadedAt = 2, authors = listOf("Spect3rr")),
        work(4, "Co-written", downloadedAt = 3, authors = listOf("Zed", "Aardvark")),
        work(5, "Middle", downloadedAt = 4, authors = listOf("Émile")),
        work(6, "Unsigned", downloadedAt = 5, authors = listOf("Anonymous")),
    )

    private fun titles(list: List<WorkEntity>, sort: LibrarySort, reversed: Boolean = false) =
        sortWorks(list, LibraryOrder(sort, reversed)).map { it.title }

    @Test
    fun byDateDownloadedNewestFirst_orOldestFirstReversed() {
        val newest = listOf("The Summers of Draco Malfoy", "A Quiet Place", "bulbs in the dirt", "Álbum")
        assertEquals(newest, titles(works, LibrarySort.DOWNLOADED))
        assertEquals(newest.reversed(), titles(works, LibrarySort.DOWNLOADED, reversed = true))
    }

    @Test
    fun byTitleIgnoresCaseAndAccents_andKeepsLeadingArticles_eitherWay() {
        val aToZ = listOf("A Quiet Place", "Álbum", "bulbs in the dirt", "The Summers of Draco Malfoy")
        assertEquals(aToZ, titles(works, LibrarySort.TITLE))
        assertEquals(aToZ.reversed(), titles(works, LibrarySort.TITLE, reversed = true))
    }

    @Test
    fun byAuthorUsesTheFirstListedAuthor_thenTitle_withUnknownAuthorsLast() {
        assertEquals(
            listOf("Unsigned", "Middle", "Apple", "Zebra", "Co-written", "No byline"),
            titles(authored, LibrarySort.AUTHOR),
        )
    }

    @Test
    fun byAuthorReversedFlipsTheAuthorsButKeepsUnknownAuthorsLast() {
        assertEquals(
            listOf("Co-written", "Zebra", "Apple", "Middle", "Unsigned", "No byline"),
            titles(authored, LibrarySort.AUTHOR, reversed = true),
        )
    }

    @Test
    fun sameTitlesFallBackToNewestDownloadFirst_evenWhenReversed() {
        val twins = listOf(work(1, "Untitled", downloadedAt = 1), work(2, "untitled", downloadedAt = 2))
        assertEquals(listOf(2L, 1L), sortWorks(twins, LibraryOrder(LibrarySort.TITLE)).map { it.workId })
        assertEquals(listOf(2L, 1L), sortWorks(twins, LibraryOrder(LibrarySort.TITLE, reversed = true)).map { it.workId })
    }

    @Test
    fun choosingTheCurrentOptionAgainReversesIt_andAnotherOptionStartsNatural() {
        val title = LibraryOrder().select(LibrarySort.TITLE)
        assertEquals(LibraryOrder(LibrarySort.TITLE, reversed = false), title)
        assertEquals(LibraryOrder(LibrarySort.TITLE, reversed = true), title.select(LibrarySort.TITLE))
        assertEquals(LibraryOrder(LibrarySort.TITLE, reversed = false), title.select(LibrarySort.TITLE).select(LibrarySort.TITLE))
        assertEquals(LibraryOrder(LibrarySort.AUTHOR, reversed = false), title.select(LibrarySort.TITLE).select(LibrarySort.AUTHOR))
    }

    @Test
    fun theLabelNamesTheDirection() {
        assertEquals("Date downloaded, newest first", LibraryOrder(LibrarySort.DOWNLOADED).label)
        assertEquals("Date downloaded, oldest first", LibraryOrder(LibrarySort.DOWNLOADED, reversed = true).label)
        assertEquals("Title, A–Z", LibraryOrder(LibrarySort.TITLE).label)
        assertEquals("Author, Z–A", LibraryOrder(LibrarySort.AUTHOR, reversed = true).label)
    }

    @Test
    fun titleKeyDropsCaseAndAccents() {
        assertEquals("elan vital", titleSortKey("  Élan Vital "))
        assertEquals(titleSortKey("naive"), titleSortKey("Naïve"))
    }
}
