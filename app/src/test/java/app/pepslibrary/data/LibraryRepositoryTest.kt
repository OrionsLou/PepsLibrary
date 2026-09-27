package app.pepslibrary.data

import app.pepslibrary.ao3.WorkMetadata
import app.pepslibrary.download.DownloadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryRepositoryTest {
    /** In-memory stand-in for the Room DAO. Its upsert has the same replace-by-key meaning. */
    private class FakeWorkDao : WorkDao {
        val rows = MutableStateFlow<Map<Long, WorkEntity>>(emptyMap())
        var upserts = 0
        override suspend fun upsert(work: WorkEntity) { upserts++; rows.value = rows.value + (work.workId to work) }
        override fun observeAll(): Flow<List<WorkEntity>> = rows.map { it.values.sortedByDescending { w -> w.downloadedAt } }
        override suspend fun get(workId: Long): WorkEntity? = rows.value[workId]
        override fun observe(workId: Long): Flow<WorkEntity?> = rows.map { it[workId] }
        override suspend fun delete(workId: Long) { rows.value = rows.value - workId }
        override suspend fun markOpened(workId: Long, at: Long) {
            rows.value[workId]?.let { rows.value = rows.value + (workId to it.copy(lastOpenedAt = at)) }
        }
        override suspend fun setPinned(workId: Long, pinned: Boolean) {
            rows.value[workId]?.let { rows.value = rows.value + (workId to it.copy(pinned = pinned)) }
        }
    }

    @get:Rule
    val tmp = TemporaryFolder()

    private val dao = FakeWorkDao()
    private var clock = 1_000L
    private val repository by lazy { LibraryRepository(dao, tmp.root) { clock } }

    private val fullMetadata = WorkMetadata(
        title = "A Title",
        authors = listOf("Someone", "Someone Else"),
        summary = "Summary text",
        rating = "Mature",
        warnings = listOf("No Archive Warnings Apply"),
        categories = listOf("Gen"),
        fandoms = listOf("Fandom"),
        relationships = listOf("A/B"),
        characters = listOf("A", "B"),
        tags = listOf("Fluff"),
        language = "English",
        words = 8994,
        chaptersPublished = 3,
        chaptersTotal = 3,
        publishedDate = "2026-03-06",
        updatedDate = "2026-08-07",
    )

    private fun success(metadata: WorkMetadata = fullMetadata, updatedAt: Long? = 1789956295, file: String = "42.epub", bytes: Long = 31_837) =
        DownloadResult.Success(File("/anywhere/works/$file"), bytes, "https://archiveofourown.org/downloads/42/T.epub", metadata, updatedAt)

    @Test
    fun aDownloadIsStoredWithItsMetadata() = runBlocking {
        repository.saveDownload(42, success())

        val row = dao.get(42)!!
        assertEquals(42L, row.workId)
        assertEquals("A Title", row.title)
        assertEquals(listOf("Someone", "Someone Else"), row.authors)
        assertEquals("Summary text", row.summary)
        assertEquals("Mature", row.rating)
        assertEquals(listOf("No Archive Warnings Apply"), row.warnings)
        assertEquals(listOf("Gen"), row.categories)
        assertEquals(listOf("Fandom"), row.fandoms)
        assertEquals(listOf("A/B"), row.relationships)
        assertEquals(listOf("A", "B"), row.characters)
        assertEquals(listOf("Fluff"), row.tags)
        assertEquals("English", row.language)
        assertEquals(8994, row.words)
        assertEquals(3, row.chaptersPublished)
        assertEquals(3, row.chaptersTotal)
        assertEquals("2026-03-06", row.publishedDate)
        assertEquals("2026-08-07", row.updatedDate)
        assertEquals(1789956295L, row.sourceUpdatedAt)
    }

    @Test
    fun onlyTheFileNameIsStored_notThePath() = runBlocking {
        repository.saveDownload(42, success(file = "42.epub", bytes = 500))
        val row = dao.get(42)!!
        assertEquals("42.epub", row.epubFileName)
        assertEquals(500L, row.fileSizeBytes)
    }

    @Test
    fun theDownloadTimeComesFromTheClock() = runBlocking {
        clock = 123_456L
        repository.saveDownload(42, success())
        assertEquals(123_456L, dao.get(42)!!.downloadedAt)
    }

    @Test
    fun aWorkWithNoParsedTitleIsNeverStoredUntitled() = runBlocking {
        repository.saveDownload(77, success(metadata = WorkMetadata()))
        val row = dao.get(77)!!
        assertEquals("Work 77", row.title)
        assertEquals(emptyList<String>(), row.authors)
        assertNull(row.words)
        assertNull(row.summary)
    }

    @Test
    fun aMissingUpdatedAtIsStoredAsNull() = runBlocking {
        repository.saveDownload(42, success(updatedAt = null))
        assertNull(dao.get(42)!!.sourceUpdatedAt)
    }

    @Test
    fun downloadingAWorkAgainReplacesItsRowInsteadOfAddingOne() = runBlocking {
        repository.saveDownload(42, success(metadata = fullMetadata.copy(chaptersPublished = 3, chaptersTotal = null)))
        clock = 9_000L
        repository.saveDownload(42, success(metadata = fullMetadata.copy(chaptersPublished = 4, chaptersTotal = null), updatedAt = 1800000000))

        assertEquals(1, dao.rows.value.size)
        val row = dao.get(42)!!
        assertEquals(4, row.chaptersPublished)
        assertEquals(1800000000L, row.sourceUpdatedAt)
        assertEquals(9_000L, row.downloadedAt)
    }

    @Test
    fun differentWorksAreKeptSeparately_mostRecentFirst() = runBlocking {
        clock = 1L; repository.saveDownload(1, success(metadata = fullMetadata.copy(title = "First"), file = "1.epub"))
        clock = 3L; repository.saveDownload(2, success(metadata = fullMetadata.copy(title = "Second"), file = "2.epub"))
        clock = 2L; repository.saveDownload(3, success(metadata = fullMetadata.copy(title = "Third"), file = "3.epub"))

        assertEquals(listOf("Second", "Third", "First"), repository.works.first().map { it.title })
    }

    @Test
    fun aNewDownloadHasNeverBeenOpened_andOpeningItRecordsTheTime() = runBlocking {
        repository.saveDownload(42, success())
        assertNull(dao.get(42)!!.lastOpenedAt)

        clock = 7_000L
        repository.markOpened(42)
        assertEquals(7_000L, dao.get(42)!!.lastOpenedAt)
    }

    @Test
    fun aWorkCanBePinnedAndUnpinned_andStartsUnpinned() = runBlocking {
        repository.saveDownload(42, success())
        assertFalse(dao.get(42)!!.pinned)
        repository.setPinned(42, true)
        assertTrue(dao.get(42)!!.pinned)
        repository.setPinned(42, false)
        assertFalse(dao.get(42)!!.pinned)
    }

    @Test
    fun reDownloadingAPinnedWorkKeepsItPinned() = runBlocking {
        repository.saveDownload(42, success())
        repository.setPinned(42, true)
        repository.saveDownload(42, success(updatedAt = 1800000000))
        assertTrue(dao.get(42)!!.pinned)
    }

    @Test
    fun reDownloadingAWorkKeepsWhenItWasLastOpened() = runBlocking {
        repository.saveDownload(42, success())
        clock = 7_000L
        repository.markOpened(42)

        clock = 9_000L
        repository.saveDownload(42, success(updatedAt = 1800000000))

        val row = dao.get(42)!!
        assertEquals(7_000L, row.lastOpenedAt)
        assertEquals(9_000L, row.downloadedAt)
    }

    @Test
    fun deletingAWorkRemovesItsRowAndItsFile_andLeavesOthersAlone() = runBlocking {
        repository.saveDownload(42, success(file = "42.epub"))
        repository.saveDownload(7, success(file = "7.epub"))
        val file = File(tmp.root, "42.epub").apply { writeText("epub") }
        val other = File(tmp.root, "7.epub").apply { writeText("epub") }

        repository.delete(42)

        assertNull(dao.get(42))
        assertFalse(file.exists())
        assertEquals(7L, dao.get(7)?.workId)
        assertTrue(other.exists())
    }

    @Test
    fun deletingAWorkWhoseFileIsAlreadyGoneStillRemovesTheRow() = runBlocking {
        repository.saveDownload(42, success(file = "42.epub"))
        repository.delete(42)
        assertNull(dao.get(42))
    }

    @Test
    fun deletingAWorkNotInTheLibraryDoesNothing() = runBlocking {
        repository.delete(99)
        assertEquals(emptyMap<Long, WorkEntity>(), dao.rows.value)
    }

    @Test
    fun isDownloadedIsFalseForAWorkNotInTheLibrary() = runBlocking {
        assertEquals(false, repository.isDownloaded(42).first())
    }

    @Test
    fun isDownloadedBecomesTrueOnceTheWorkIsSaved() = runBlocking {
        repository.saveDownload(42, success())
        assertEquals(true, repository.isDownloaded(42).first())
        assertEquals(false, repository.isDownloaded(99).first())
    }
}
