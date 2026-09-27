package app.pepslibrary.ui

import app.pepslibrary.data.WorkEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {
    private fun work(id: Long, title: String, downloadedAt: Long, authors: List<String> = emptyList()) = WorkEntity(
        workId = id, title = title, authors = authors, summary = null, rating = null, warnings = emptyList(),
        categories = emptyList(), fandoms = emptyList(), relationships = emptyList(), characters = emptyList(),
        tags = emptyList(), language = null, words = null, chaptersPublished = null, chaptersTotal = null,
        publishedDate = null, updatedDate = null, sourceUpdatedAt = null, epubFileName = "$id.epub",
        fileSizeBytes = 0, downloadedAt = downloadedAt,
    )

    private val works = listOf(
        work(1, "bulbs in the dirt", downloadedAt = 30),
        work(2, "The Summers of Draco Malfoy", downloadedAt = 50),
        work(3, "Álbum", downloadedAt = 10),
        work(4, "A Quiet Place", downloadedAt = 40),
    )

    private fun titles(sort: LibrarySort) = sortWorks(works, sort).map { it.title }

    @Test
    fun byDateDownloadedNewestFirst() {
        assertEquals(
            listOf("The Summers of Draco Malfoy", "A Quiet Place", "bulbs in the dirt", "Álbum"),
            titles(LibrarySort.DOWNLOADED),
        )
    }

    @Test
    fun byTitleIgnoresCaseAndAccents_andKeepsLeadingArticles() {
        assertEquals(
            listOf("A Quiet Place", "Álbum", "bulbs in the dirt", "The Summers of Draco Malfoy"),
            titles(LibrarySort.TITLE),
        )
    }

    @Test
    fun byAuthorUsesTheFirstListedAuthor_thenTitle_withUnknownAuthorsLast() {
        val library = listOf(
            work(1, "Zebra", downloadedAt = 1, authors = listOf("spect3rr")),
            work(2, "No byline", downloadedAt = 9),
            work(3, "Apple", downloadedAt = 2, authors = listOf("Spect3rr")),
            work(4, "Co-written", downloadedAt = 3, authors = listOf("Zed", "Aardvark")),
            work(5, "Middle", downloadedAt = 4, authors = listOf("Émile")),
            work(6, "Unsigned", downloadedAt = 5, authors = listOf("Anonymous")),
        )
        assertEquals(
            listOf("Unsigned", "Middle", "Apple", "Zebra", "Co-written", "No byline"),
            sortWorks(library, LibrarySort.AUTHOR).map { it.title },
        )
    }

    @Test
    fun titleKeyDropsCaseAndAccents() {
        assertEquals("elan vital", titleSortKey("  Élan Vital "))
        assertEquals(titleSortKey("naive"), titleSortKey("Naïve"))
    }

    @Test
    fun sameTitlesFallBackToNewestDownloadFirst() {
        val twins = listOf(work(1, "Untitled", downloadedAt = 1), work(2, "untitled", downloadedAt = 2))
        assertEquals(listOf(2L, 1L), sortWorks(twins, LibrarySort.TITLE).map { it.workId })
    }
}
