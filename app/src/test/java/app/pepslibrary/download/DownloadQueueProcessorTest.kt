package app.pepslibrary.download

import app.pepslibrary.ao3.WorkMetadata
import app.pepslibrary.data.DownloadQueueDao
import app.pepslibrary.data.DownloadQueueEntity
import app.pepslibrary.data.DownloadQueueRepository
import app.pepslibrary.data.LibraryRepository
import app.pepslibrary.data.QueueStatus
import app.pepslibrary.data.WorkDao
import app.pepslibrary.data.WorkEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadQueueProcessorTest {
    /** Same shape as DownloadQueueRepositoryTest's fake, plus nextEligible replicating the real query's filter. */
    private class FakeQueueDao : DownloadQueueDao {
        val rows = MutableStateFlow<Map<Long, DownloadQueueEntity>>(emptyMap())
        override suspend fun upsert(entry: DownloadQueueEntity) { rows.value = rows.value + (entry.workId to entry) }
        override suspend fun get(workId: Long): DownloadQueueEntity? = rows.value[workId]
        override fun observeAll(): Flow<List<DownloadQueueEntity>> =
            rows.map { it.values.sortedBy { e -> e.enqueuedAt } }
        override suspend fun delete(workId: Long) { rows.value = rows.value - workId }
        override suspend fun setStatus(workId: Long, status: QueueStatus) {
            rows.value[workId]?.let { rows.value = rows.value + (workId to it.copy(status = status, notBeforeMillis = null)) }
        }
        override suspend fun resetStatus(from: QueueStatus, to: QueueStatus) {
            rows.value = rows.value.mapValues { (_, e) -> if (e.status == from) e.copy(status = to, notBeforeMillis = null) else e }
        }
        override suspend fun nextEligible(status: QueueStatus, now: Long): DownloadQueueEntity? =
            rows.value.values
                .filter { it.status == status && (it.notBeforeMillis == null || it.notBeforeMillis!! <= now) }
                .minByOrNull { it.enqueuedAt }
    }

    /** Same shape as LibraryRepositoryTest's fake. */
    private class FakeWorkDao : WorkDao {
        val rows = MutableStateFlow<Map<Long, WorkEntity>>(emptyMap())
        override suspend fun upsert(work: WorkEntity) { rows.value = rows.value + (work.workId to work) }
        override fun observeAll(): Flow<List<WorkEntity>> = rows.map { it.values.sortedByDescending { w -> w.downloadedAt } }
        override suspend fun get(workId: Long): WorkEntity? = rows.value[workId]
        override fun observe(workId: Long): Flow<WorkEntity?> = rows.map { it[workId] }
    }

    private val queueDao = FakeQueueDao()
    private val workDao = FakeWorkDao()
    private var clock = 1_000_000L
    private val queue = DownloadQueueRepository(queueDao) { clock }
    private val library = LibraryRepository(workDao) { clock }

    private val calls = mutableListOf<Long>()

    private fun processor(download: suspend (Long) -> DownloadResult) =
        DownloadQueueProcessor(queue, library, download = { workId -> calls += workId; download(workId) })

    private fun success(workId: Long) = DownloadResult.Success(
        file = File("/anywhere/works/$workId.epub"),
        bytes = 12_345,
        epubUrl = "https://archiveofourown.org/downloads/$workId/T.epub",
        metadata = WorkMetadata(title = "Work $workId"),
        sourceUpdatedAt = null,
    )

    private fun failure(kind: FailureKind) = DownloadResult.Failure(kind, "$kind happened")

    @Test
    fun returnsFalseAndDoesNotDownloadWhenTheQueueIsEmpty() = runBlocking {
        val processed = processor { failure(FailureKind.NETWORK) }.processNext()
        assertFalse(processed)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun downloadsTheOldestEligibleWorkFirst() = runBlocking {
        clock = 2L; queue.enqueue(20)
        clock = 1L; queue.enqueue(10)

        val processed = processor { success(it) }.processNext()

        assertTrue(processed)
        assertEquals(listOf(10L), calls) // the one enqueued first, not the one with the lower workId
    }

    @Test
    fun marksTheWorkInProgressBeforeCallingDownload() = runBlocking {
        queue.enqueue(42)
        var statusDuringDownload: QueueStatus? = null

        processor {
            statusDuringDownload = queue.get(42)?.status
            success(42)
        }.processNext()

        assertEquals(QueueStatus.IN_PROGRESS, statusDuringDownload)
    }

    @Test
    fun onSuccessSavesToTheLibraryAndRemovesFromTheQueue() = runBlocking {
        queue.enqueue(42)

        processor { success(42) }.processNext()

        assertEquals("Work 42", workDao.get(42)?.title)
        assertNull(queue.get(42))
    }

    @Test
    fun onFailureRecordsItInTheQueueAndDoesNotTouchTheLibrary() = runBlocking {
        queue.enqueue(42)

        processor { failure(FailureKind.NETWORK) }.processNext()

        val row = queue.get(42)!!
        assertEquals(QueueStatus.PENDING, row.status) // NETWORK is retryable, first attempt
        assertEquals(FailureKind.NETWORK, row.lastFailureKind)
        assertNull(workDao.get(42))
    }

    @Test
    fun aWorkStillWaitingOnItsRetryIsSkipped() = runBlocking {
        clock = 0L
        queue.enqueue(42)
        queue.recordFailure(42, failure(FailureKind.NETWORK)) // schedules a retry 30s out
        calls.clear()

        val processed = processor { success(42) }.processNext()

        assertFalse(processed)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun onceTheRetryTimeArrivesTheWorkBecomesEligibleAgain() = runBlocking {
        clock = 0L
        queue.enqueue(42)
        queue.recordFailure(42, failure(FailureKind.NETWORK)) // notBeforeMillis = 30_000
        clock = 30_000L

        val processed = processor { success(42) }.processNext()

        assertTrue(processed)
        assertEquals(listOf(42L), calls)
    }

    @Test
    fun anUnexpectedExceptionFromDownloadIsRecordedAsAFailureRatherThanLeavingTheRowStuckInProgress() = runBlocking {
        queue.enqueue(42)

        processor { throw RuntimeException("boom") }.processNext()

        val row = queue.get(42)!!
        assertEquals(QueueStatus.PENDING, row.status)
        assertEquals(FailureKind.NETWORK, row.lastFailureKind)
        assertEquals("boom", row.lastFailureMessage)
    }
}
