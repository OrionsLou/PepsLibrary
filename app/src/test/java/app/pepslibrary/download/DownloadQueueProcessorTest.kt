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
        override suspend fun delete(workId: Long) { rows.value = rows.value - workId }
        override suspend fun markOpened(workId: Long, at: Long) {
            rows.value[workId]?.let { rows.value = rows.value + (workId to it.copy(lastOpenedAt = at)) }
        }
        override suspend fun setPinned(workId: Long, pinned: Boolean) {
            rows.value[workId]?.let { rows.value = rows.value + (workId to it.copy(pinned = pinned)) }
        }
    }

    private val queueDao = FakeQueueDao()
    private val workDao = FakeWorkDao()
    private var clock = 1_000_000L
    private val queue = DownloadQueueRepository(queueDao) { clock }
    private val library = LibraryRepository(workDao, File("/unused")) { clock }

    private val calls = mutableListOf<Long>()

    private var online = true

    private fun processor(download: suspend DownloadCanceller.(Long) -> DownloadResult) =
        DownloadQueueProcessor(
            queue,
            library,
            download = { workId -> calls += workId; download(workId) },
            isOnline = { online },
        )

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
    fun afterSuccessRunsOnceTheWorkIsSavedAndDequeued() = runBlocking {
        queue.enqueue(42)
        var seen: Triple<Long, Boolean, Boolean>? = null

        DownloadQueueProcessor(queue, library, download = { success(it) }) { workId, _ ->
            seen = Triple(workId, workDao.get(workId) != null, queue.get(workId) == null)
        }.processNext()

        assertEquals(Triple(42L, true, true), seen)
    }

    @Test
    fun afterSuccessDoesNotRunOnFailure() = runBlocking {
        queue.enqueue(42)
        var ran = false

        DownloadQueueProcessor(queue, library, download = { failure(FailureKind.NETWORK) }) { _, _ -> ran = true }.processNext()

        assertFalse(ran)
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

    // --- cancelling ---

    @Test
    fun cancellingTheRunningDownloadStopsIt_andRemovesItWithoutSavingOrRetrying() = runBlocking {
        queue.enqueue(42)
        var afterSuccessRan = false
        lateinit var p: DownloadQueueProcessor
        p = DownloadQueueProcessor(queue, library, download = { workId ->
            p.cancel(workId) // tapped while this download is running
            if (isCancelled) DownloadResult.Cancelled else success(workId)
        }) { _, _ -> afterSuccessRan = true }

        assertTrue(p.processNext())

        assertNull(queue.get(42))
        assertNull(workDao.get(42))
        assertFalse(afterSuccessRan)
    }

    @Test
    fun aRunningDownloadStaysListedUntilItHasActuallyStopped() = runBlocking {
        queue.enqueue(42)
        var rowDuringCancel: QueueStatus? = null
        lateinit var p: DownloadQueueProcessor
        p = processor { workId ->
            p.cancel(workId)
            rowDuringCancel = queue.get(workId)?.status
            DownloadResult.Cancelled
        }

        p.processNext()

        assertEquals(QueueStatus.IN_PROGRESS, rowDuringCancel)
        assertNull(queue.get(42))
    }

    @Test
    fun aDownloadThatFinishesDespiteALateCancelIsStillSaved() = runBlocking {
        queue.enqueue(42)

        processor { workId -> cancel(); success(workId) }.processNext() // the file was already in place

        assertEquals("Work 42", workDao.get(42)?.title)
        assertNull(queue.get(42))
    }

    @Test
    fun aFailureRacingACancelIsNotQueuedForRetry() = runBlocking {
        queue.enqueue(42)

        processor { cancel(); failure(FailureKind.NETWORK) }.processNext()

        assertNull(queue.get(42))
    }

    @Test
    fun cancellingAWorkThatIsNotRunningJustRemovesIt() = runBlocking {
        queue.enqueue(42)
        queue.recordFailure(42, failure(FailureKind.NO_EPUB_LINK)) // sitting at FAILED

        processor { success(it) }.cancel(42)

        assertNull(queue.get(42))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun cancellingOneWorkLeavesTheNextToDownloadNormally() = runBlocking {
        clock = 1L; queue.enqueue(10)
        clock = 2L; queue.enqueue(20)
        lateinit var p: DownloadQueueProcessor
        p = processor { workId ->
            if (workId == 10L) { p.cancel(10); DownloadResult.Cancelled } else success(workId)
        }

        p.processNext()
        p.processNext()

        assertNull(queue.get(10))
        assertNull(workDao.get(10))
        assertEquals("Work 20", workDao.get(20)?.title)
    }

    // --- offline ---

    @Test
    fun nothingStartsWhileOffline() = runBlocking {
        queue.enqueue(42)
        online = false

        val processed = processor { success(it) }.processNext()

        assertFalse(processed)
        assertTrue(calls.isEmpty())
        assertEquals(QueueStatus.PENDING, queue.get(42)?.status)
    }

    @Test
    fun aNetworkFailureAfterTheConnectionDroppedDoesNotUseAnAttempt() = runBlocking {
        queue.enqueue(42)

        processor { online = false; failure(FailureKind.NETWORK) }.processNext() // dropped mid-download

        val row = queue.get(42)!!
        assertEquals(QueueStatus.PENDING, row.status)
        assertEquals(0, row.attempts)
        assertNull(row.notBeforeMillis)
    }

    @Test
    fun aNetworkFailureWhileStillOnlineCountsAsUsual() = runBlocking {
        queue.enqueue(42)

        processor { failure(FailureKind.NETWORK) }.processNext()

        assertEquals(1, queue.get(42)!!.attempts)
    }

    @Test
    fun otherFailuresCountEvenIfTheConnectionDropped() = runBlocking {
        queue.enqueue(42)

        processor { online = false; failure(FailureKind.NO_EPUB_LINK) }.processNext()

        assertEquals(QueueStatus.FAILED, queue.get(42)!!.status)
    }

    @Test
    fun theQueueResumesOnceBackOnline() = runBlocking {
        queue.enqueue(42)
        online = false
        val p = processor { success(it) }
        p.processNext()

        online = true
        assertTrue(p.processNext())
        assertEquals("Work 42", workDao.get(42)?.title)
    }
}
