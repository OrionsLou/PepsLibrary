package app.pepslibrary.data

import app.pepslibrary.download.DownloadResult
import app.pepslibrary.download.FailureKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueRepositoryTest {
    /** In-memory stand-in for the Room DAO, with the same upsert/status-update semantics. */
    private class FakeDao : DownloadQueueDao {
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

    private val dao = FakeDao()
    private var clock = 1_000_000L
    private val repository = DownloadQueueRepository(dao) { clock }

    private fun failure(kind: FailureKind, retryAfterSeconds: Long? = null) =
        DownloadResult.Failure(kind, "$kind happened", retryAfterSeconds)

    // --- enqueue ---

    @Test
    fun enqueueingANewWorkAddsAFreshPendingRow() = runBlocking {
        repository.enqueue(42)
        val row = repository.get(42)!!
        assertEquals(QueueStatus.PENDING, row.status)
        assertEquals(0, row.attempts)
        assertNull(row.notBeforeMillis)
        assertNull(row.lastFailureKind)
        assertNull(row.lastFailureMessage)
        assertEquals(clock, row.enqueuedAt)
    }

    @Test
    fun enqueueingAnAlreadyFailedWorkResetsItRatherThanDuplicating() = runBlocking {
        repeat(MAX_ATTEMPTS) { repository.recordFailure(42, failure(FailureKind.NETWORK)) }
        assertEquals(QueueStatus.FAILED, repository.get(42)!!.status)

        repository.enqueue(42)

        val row = repository.get(42)!!
        assertEquals(QueueStatus.PENDING, row.status)
        assertEquals(0, row.attempts)
        assertNull(row.lastFailureKind)
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun differentWorksAreKeptSeparately() = runBlocking {
        repository.enqueue(1)
        repository.enqueue(2)
        assertEquals(setOf(1L, 2L), dao.rows.value.keys)
    }

    @Test
    fun entriesAreOrderedOldestFirst() = runBlocking {
        clock = 3L; repository.enqueue(30)
        clock = 1L; repository.enqueue(10)
        clock = 2L; repository.enqueue(20)
        assertEquals(listOf(10L, 20L, 30L), repository.entries.first().map { it.workId })
    }

    // --- markInProgress / remove ---

    @Test
    fun markInProgressChangesStatusAndClearsAnyRetryWait() = runBlocking {
        repository.recordFailure(42, failure(FailureKind.NETWORK)) // leaves a non-null notBeforeMillis
        repository.markInProgress(42)
        val row = repository.get(42)!!
        assertEquals(QueueStatus.IN_PROGRESS, row.status)
        assertNull(row.notBeforeMillis)
        assertEquals(1, row.attempts) // untouched by markInProgress
    }

    @Test
    fun removeDeletesTheRow() = runBlocking {
        repository.enqueue(42)
        repository.remove(42)
        assertNull(repository.get(42))
        assertTrue(repository.entries.first().isEmpty())
    }

    @Test
    fun removingAWorkNotInTheQueueIsANoOp() = runBlocking {
        repository.remove(999)
        assertTrue(dao.rows.value.isEmpty())
    }

    // --- recordFailure: retry scheduling ---

    @Test
    fun aRetryableFailureGoesBackToPendingWithDefaultBackoff() = runBlocking {
        clock = 100_000L
        repository.recordFailure(42, failure(FailureKind.NETWORK))
        val row = repository.get(42)!!
        assertEquals(QueueStatus.PENDING, row.status)
        assertEquals(1, row.attempts)
        assertEquals(FailureKind.NETWORK, row.lastFailureKind)
        assertEquals("NETWORK happened", row.lastFailureMessage)
        assertEquals(100_000L + 30_000L, row.notBeforeMillis) // first backoff: 30s
    }

    @Test
    fun eachRetryableFailureDoublesTheBackoffFromWhenItHappens() = runBlocking {
        clock = 0L
        repository.recordFailure(42, failure(FailureKind.SERVER_ERROR)) // attempts=1 -> +30s
        clock = 500_000L
        repository.recordFailure(42, failure(FailureKind.SERVER_ERROR)) // attempts=2 -> +60s
        val row = repository.get(42)!!
        assertEquals(2, row.attempts)
        assertEquals(500_000L + 60_000L, row.notBeforeMillis)
    }

    @Test
    fun retryAfterFromAo3TakesPrecedenceOverOurOwnBackoff() = runBlocking {
        clock = 1_000L
        repository.recordFailure(42, failure(FailureKind.RATE_LIMITED, retryAfterSeconds = 45))
        assertEquals(1_000L + 45_000L, repository.get(42)!!.notBeforeMillis)
    }

    @Test
    fun exhaustingAutomaticRetriesLandsOnFailed() = runBlocking {
        repeat(MAX_ATTEMPTS - 1) {
            repository.recordFailure(42, failure(FailureKind.BOT_CHECK))
            assertEquals("still retrying before the cap", QueueStatus.PENDING, repository.get(42)!!.status)
        }
        repository.recordFailure(42, failure(FailureKind.BOT_CHECK))
        val row = repository.get(42)!!
        assertEquals(QueueStatus.FAILED, row.status)
        assertEquals(MAX_ATTEMPTS, row.attempts)
        assertNull(row.notBeforeMillis)
    }

    @Test
    fun nonRetryableKindsGoStraightToFailedOnTheFirstTry() = runBlocking {
        listOf(FailureKind.NO_EPUB_LINK, FailureKind.NOT_AN_EPUB, FailureKind.HTTP_ERROR).forEach { kind ->
            repository.recordFailure(100L + kind.ordinal, failure(kind))
            val row = repository.get(100L + kind.ordinal)!!
            assertEquals("$kind", QueueStatus.FAILED, row.status)
            assertEquals(1, row.attempts)
            assertNull(row.notBeforeMillis)
        }
    }

    @Test
    fun retryableKindsAreGivenAChanceBeforeFailing() = runBlocking {
        listOf(FailureKind.BOT_CHECK, FailureKind.RATE_LIMITED, FailureKind.SERVER_ERROR, FailureKind.NETWORK)
            .forEach { kind ->
                repository.recordFailure(200L + kind.ordinal, failure(kind))
                assertEquals("$kind", QueueStatus.PENDING, repository.get(200L + kind.ordinal)!!.status)
            }
    }

    @Test
    fun recordFailureForAWorkNotAlreadyInTheQueueStillCreatesARowInsteadOfCrashing() = runBlocking {
        clock = 42L
        repository.recordFailure(7, failure(FailureKind.NETWORK))
        val row = repository.get(7)!!
        assertEquals(1, row.attempts)
        assertEquals(42L, row.enqueuedAt)
    }

    // --- recoverInterrupted ---

    @Test
    fun recoverInterruptedResetsInProgressRowsWithoutCountingAnAttempt() = runBlocking {
        repository.enqueue(1)
        repository.markInProgress(1)
        assertEquals(QueueStatus.IN_PROGRESS, repository.get(1)!!.status)

        repository.recoverInterrupted()

        val row = repository.get(1)!!
        assertEquals(QueueStatus.PENDING, row.status)
        assertNull(row.notBeforeMillis)
        assertEquals(0, row.attempts) // the interruption itself is not a failed attempt
    }

    @Test
    fun recoverInterruptedLeavesOtherStatusesAlone() = runBlocking {
        repository.enqueue(1) // PENDING
        repeat(MAX_ATTEMPTS) { repository.recordFailure(2, failure(FailureKind.NETWORK)) } // FAILED

        repository.recoverInterrupted()

        assertEquals(QueueStatus.PENDING, repository.get(1)!!.status)
        assertEquals(QueueStatus.FAILED, repository.get(2)!!.status)
    }

    // --- backoffSeconds: the cap, exercised directly since MAX_ATTEMPTS never reaches it via recordFailure ---

    @Test
    fun backoffDoublesEachAttemptThenCaps() {
        assertEquals(30L, backoffSeconds(1))
        assertEquals(60L, backoffSeconds(2))
        assertEquals(120L, backoffSeconds(3))
        assertEquals(240L, backoffSeconds(4))
        assertEquals(300L, backoffSeconds(5)) // 480 uncapped -> capped to 300
        assertEquals(300L, backoffSeconds(10))
    }
}
