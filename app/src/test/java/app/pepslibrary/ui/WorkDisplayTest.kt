package app.pepslibrary.ui

import app.pepslibrary.data.WorkEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkDisplayTest {
    private fun work(
        authors: List<String> = emptyList(),
        words: Int? = null,
        published: Int? = null,
        total: Int? = null,
    ) = WorkEntity(
        workId = 1, title = "T", authors = authors, summary = null, rating = null, warnings = emptyList(),
        categories = emptyList(), fandoms = emptyList(), relationships = emptyList(), characters = emptyList(),
        tags = emptyList(), language = null, words = words, chaptersPublished = published, chaptersTotal = total,
        publishedDate = null, updatedDate = null, sourceUpdatedAt = null, epubFileName = "1.epub",
        fileSizeBytes = 0, downloadedAt = 0,
    )

    @Test
    fun aCompleteMultiChapterWork() {
        assertEquals("8,994 words · 3/3 chapters · Complete", workStatsLine(work(words = 8994, published = 3, total = 3)))
    }

    @Test
    fun anUnfinishedWorkWithAPlannedTotal() {
        assertEquals("120,000 words · 3/10 chapters · In progress", workStatsLine(work(words = 120000, published = 3, total = 10)))
    }

    @Test
    fun anUnfinishedWorkWithNoPlannedTotalShowsAQuestionMark() {
        assertEquals("5,000 words · 4/? chapters · In progress", workStatsLine(work(words = 5000, published = 4, total = null)))
    }

    @Test
    fun aOneShotSaysOneChapterAndIsComplete() {
        assertEquals("900 words · 1 chapter · Complete", workStatsLine(work(words = 900, published = 1, total = 1)))
    }

    @Test
    fun aOneChapterWorkThatIsPlannedToGrowIsInProgress() {
        assertEquals("900 words · 1/5 chapters · In progress", workStatsLine(work(words = 900, published = 1, total = 5)))
    }

    @Test
    fun oneWordIsSingular() {
        assertEquals("1 word", workStatsLine(work(words = 1)))
    }

    @Test
    fun partsWeDoNotKnowAreLeftOut() {
        assertEquals("", workStatsLine(work()))
        assertEquals("2,000 words", workStatsLine(work(words = 2000)))
        assertEquals("2/2 chapters · Complete", workStatsLine(work(published = 2, total = 2)))
    }

    @Test
    fun bylineJoinsAuthorsAndIsNullWithoutAny() {
        assertEquals("by A", workByline(work(authors = listOf("A"))))
        assertEquals("by A, B", workByline(work(authors = listOf("A", "B"))))
        assertNull(workByline(work()))
    }
}
