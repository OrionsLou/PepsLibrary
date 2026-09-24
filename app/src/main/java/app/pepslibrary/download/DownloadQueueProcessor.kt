package app.pepslibrary.download

import android.util.Log
import app.pepslibrary.AppScope
import app.pepslibrary.data.DownloadQueueRepository
import app.pepslibrary.data.LibraryRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "PepsLibrary"

/** Pause between downloads even when several are queued: never parallel, never back-to-back. */
private const val DOWNLOAD_DELAY_MS = 5_000L

/**
 * How often an idle loop checks again. The queue's own Flow only fires on data changes, not on a future
 * notBeforeMillis simply arriving, so this poll is what actually notices a retry wait elapsing or a new enqueue.
 */
private const val IDLE_POLL_MS = 3_000L

/**
 * Drains the download queue one work at a time. [download] is a plain function rather than [EpubDownloader]
 * itself, so this can be unit-tested without real networking or file I/O.
 */
class DownloadQueueProcessor(
    private val queue: DownloadQueueRepository,
    private val library: LibraryRepository,
    private val download: suspend (workId: Long) -> DownloadResult,
) {
    /** Downloads the next eligible work, if there is one. Returns whether it processed one, success or failure. */
    suspend fun processNext(): Boolean {
        val next = queue.nextEligible() ?: return false
        queue.markInProgress(next.workId)
        val result = try {
            download(next.workId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // download() is expected to always return a typed Failure rather than throw (EpubDownloader itself
            // never does); this is a last-resort safety net so an unexpected exception can never leave the row
            // stuck at IN_PROGRESS until the next process restart.
            DownloadResult.Failure(FailureKind.NETWORK, e.message ?: e.javaClass.simpleName)
        }
        when (result) {
            is DownloadResult.Success -> {
                library.saveDownload(next.workId, result)
                queue.remove(next.workId)
            }
            is DownloadResult.Failure -> queue.recordFailure(next.workId, result)
        }
        return true
    }

    /**
     * Runs for as long as [scope] lives. Call once per process; safe to call again, since
     * [DownloadQueueRepository.recoverInterrupted] runs first every time and the queue only ever expects one
     * processor running against it (matching the earlier decision to keep this in-process rather than a
     * WorkManager or foreground-service job, which could otherwise overlap with one already running).
     */
    fun start(scope: CoroutineScope = AppScope) {
        scope.launch {
            runCatching { queue.recoverInterrupted() }
                .onFailure { Log.e(TAG, "Could not recover interrupted downloads", it) }
            while (true) {
                val processed = try {
                    processNext()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keeps the loop alive: one bad iteration must not silently stop every future download.
                    Log.e(TAG, "Queue step failed unexpectedly", e)
                    false
                }
                delay(if (processed) DOWNLOAD_DELAY_MS else IDLE_POLL_MS)
            }
        }
    }
}
