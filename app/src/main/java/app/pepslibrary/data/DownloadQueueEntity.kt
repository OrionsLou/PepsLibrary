package app.pepslibrary.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.pepslibrary.download.FailureKind

/** Where a queued work currently stands. Waiting to retry is not its own status: see [DownloadQueueEntity.notBeforeMillis]. */
enum class QueueStatus { PENDING, IN_PROGRESS, FAILED }

/**
 * One work waiting to be downloaded, or one that failed and hasn't been cleared. A row is removed on success: the
 * durable record of "this is downloaded" is [WorkEntity], via `LibraryRepository.saveDownload`; the queue only
 * ever represents work still outstanding.
 */
@Entity(tableName = "download_queue")
data class DownloadQueueEntity(
    @PrimaryKey val workId: Long,
    val status: QueueStatus,
    /** Automatic-retry counter; a manual re-enqueue (re-tapping Download) resets it. */
    val attempts: Int,
    /** Null or in the past: eligible to run now. In the future: still backing off (Retry-After or our own delay). */
    val notBeforeMillis: Long?,
    val lastFailureKind: FailureKind?,
    val lastFailureMessage: String?,
    /** Also the FIFO ordering key. */
    val enqueuedAt: Long,
)
