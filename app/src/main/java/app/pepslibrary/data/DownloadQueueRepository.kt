package app.pepslibrary.data

import app.pepslibrary.download.DownloadResult
import app.pepslibrary.download.FailureKind
import kotlinx.coroutines.flow.Flow

/**
 * Retried automatically on a later attempt; the others won't fix themselves without the person doing something
 * (opening the browser, AO3 fixing its markup, ...). Mirrors the grouping in [FailureKind]'s own doc comment; kept
 * here rather than there because [FailureKind] is deliberately queue-agnostic (see `EpubDownloader`'s doc comment).
 */
private val RETRYABLE_KINDS =
    setOf(FailureKind.BOT_CHECK, FailureKind.RATE_LIMITED, FailureKind.SERVER_ERROR, FailureKind.NETWORK)

/** Automatic attempts before a retryable failure is treated as terminal: the first try plus this many retries. */
internal const val MAX_ATTEMPTS = 3

/** Backoff used when AO3 didn't give a Retry-After: doubles per attempt, capped so it never grows unbounded. */
private const val BASE_BACKOFF_SECONDS = 30L
private const val MAX_BACKOFF_SECONDS = 300L

/** The queue of works waiting to be downloaded. Depends on [DownloadQueueDao] only, so it can be tested with a fake. */
class DownloadQueueRepository(
    private val dao: DownloadQueueDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Queued and failed works, oldest first. Emits again whenever the queue changes. */
    val entries: Flow<List<DownloadQueueEntity>> = dao.observeAll()

    suspend fun get(workId: Long): DownloadQueueEntity? = dao.get(workId)

    /** The oldest work that's ready to download right now, or null if the queue is empty or everything is waiting. */
    suspend fun nextEligible(): DownloadQueueEntity? = dao.nextEligible(QueueStatus.PENDING, now())

    /** Adds a work to the queue, or resets it to a fresh attempt if it was already there (e.g. sitting at FAILED). */
    suspend fun enqueue(workId: Long) {
        dao.upsert(
            DownloadQueueEntity(
                workId = workId,
                status = QueueStatus.PENDING,
                attempts = 0,
                notBeforeMillis = null,
                lastFailureKind = null,
                lastFailureMessage = null,
                enqueuedAt = now(),
            ),
        )
    }

    /** The processor calls this right before it starts downloading a row it pulled off the queue. */
    suspend fun markInProgress(workId: Long) = dao.setStatus(workId, QueueStatus.IN_PROGRESS)

    /** The processor calls this once a download succeeds; the durable record of it now lives in [WorkEntity]. */
    suspend fun remove(workId: Long) = dao.delete(workId)

    /**
     * Records a failed attempt. A retryable kind under the attempt cap goes back to PENDING with [notBeforeMillis]
     * set from AO3's own Retry-After when it gave one, or our own backoff otherwise; anything else lands on
     * FAILED, which the processor will not retry on its own.
     */
    suspend fun recordFailure(workId: Long, failure: DownloadResult.Failure) {
        val existing = dao.get(workId)
        val attempts = (existing?.attempts ?: 0) + 1
        val willRetry = failure.kind in RETRYABLE_KINDS && attempts < MAX_ATTEMPTS
        dao.upsert(
            DownloadQueueEntity(
                workId = workId,
                status = if (willRetry) QueueStatus.PENDING else QueueStatus.FAILED,
                attempts = attempts,
                notBeforeMillis = if (willRetry) now() + retryDelayMillis(failure, attempts) else null,
                lastFailureKind = failure.kind,
                lastFailureMessage = failure.message,
                enqueuedAt = existing?.enqueuedAt ?: now(),
            ),
        )
    }

    /**
     * Call once at startup, before pulling anything from the queue. A row stuck IN_PROGRESS means the process died
     * mid-download, not that the download itself failed, so this doesn't count against [MAX_ATTEMPTS].
     */
    suspend fun recoverInterrupted() = dao.resetStatus(from = QueueStatus.IN_PROGRESS, to = QueueStatus.PENDING)

    private fun retryDelayMillis(failure: DownloadResult.Failure, attempts: Int): Long =
        (failure.retryAfterSeconds ?: backoffSeconds(attempts)) * 1000
}

/** attempts=1 -> 30s, attempts=2 -> 60s, ... capped at [MAX_BACKOFF_SECONDS]. Exposed for direct testing of the cap. */
internal fun backoffSeconds(attempts: Int): Long =
    (BASE_BACKOFF_SECONDS * (1L shl (attempts - 1))).coerceAtMost(MAX_BACKOFF_SECONDS)
