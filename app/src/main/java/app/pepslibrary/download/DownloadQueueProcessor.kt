package app.pepslibrary.download

import android.util.Log
import app.pepslibrary.AppScope
import app.pepslibrary.data.DownloadQueueRepository
import app.pepslibrary.data.LibraryRepository
import app.pepslibrary.data.countsAsAttempt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "PepsLibrary"

/** Pause between downloads even when several are queued: never parallel, never back-to-back. */
private const val DOWNLOAD_DELAY_MS = 5_000L

/**
 * How often an idle loop checks again. The queue's own Flow only fires on data changes, not on a future
 * notBeforeMillis simply arriving, so this poll is what actually notices a retry wait elapsing or a new enqueue.
 */
private const val IDLE_POLL_MS = 3_000L

/** [workId]'s download and what it's doing right now. */
data class RunningDownload(val workId: Long, val progress: DownloadProgress)

/**
 * Drains the download queue one work at a time. [download] is a plain function rather than [EpubDownloader]
 * itself, so this can be unit-tested without real networking or file I/O. Its receiver is that download's own
 * [DownloadHandle], which [cancel] uses to stop it.
 */
class DownloadQueueProcessor(
    private val queue: DownloadQueueRepository,
    private val library: LibraryRepository,
    private val download: suspend DownloadHandle.(workId: Long) -> DownloadResult,
    /**
     * While false, nothing starts: an outage shouldn't burn through every queued work's retry attempts. Checked
     * again after a network failure, which then doesn't count as an attempt if the connection has gone.
     */
    private val isOnline: () -> Boolean = { true },
    /** Runs after a success is saved and dequeued, so a failure here can never leave the work stuck in the queue. */
    private val afterSuccess: suspend (workId: Long, result: DownloadResult.Success) -> Unit = { _, _ -> },
) {
    /** The work downloading right now and its handle. Guarded by `this`: [cancel] runs on another thread. */
    private var current: Pair<Long, DownloadHandle>? = null

    private val runningState = MutableStateFlow<RunningDownload?>(null)

    /** The download in progress and how far it has got, or null when none is running. For the progress on screen. */
    val running: StateFlow<RunningDownload?> = runningState.asStateFlow()

    /**
     * Stops [workId]'s download if it's the one running, and takes it off the queue either way. A running
     * download is removed once it has actually stopped, so it never shows as gone while still writing its file.
     * One that finishes before the cancel lands is kept: it's already on disk.
     */
    suspend fun cancel(workId: Long) {
        val running = synchronized(this) { current?.takeIf { it.first == workId }?.second }
        if (running != null) running.cancel() else queue.remove(workId)
    }

    /** Downloads the next eligible work, if there is one. Returns whether it processed one, success or failure. */
    suspend fun processNext(): Boolean {
        if (!isOnline()) return false
        val next = queue.nextEligible() ?: return false
        val handle = DownloadHandle { runningState.value = RunningDownload(next.workId, it) }
        // Set before the row shows IN_PROGRESS, which is when the UI starts offering Cancel.
        synchronized(this) { current = next.workId to handle }
        runningState.value = RunningDownload(next.workId, DownloadProgress.LoadingPage)
        try {
            queue.markInProgress(next.workId)
            val result = runDownload(next.workId, handle)
            // A failure racing a cancel (the network dropped just as Cancel was tapped) must not be queued for retry.
            val cancelled = result is DownloadResult.Failure && handle.isCancelled
            record(next.workId, if (cancelled) DownloadResult.Cancelled else result)
        } finally {
            synchronized(this) { current = null }
            runningState.value = null
        }
        return true
    }

    private suspend fun runDownload(workId: Long, handle: DownloadHandle): DownloadResult =
        try {
            handle.download(workId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // download() is expected to always return a typed Failure rather than throw (EpubDownloader itself
            // never does); this is a last-resort safety net so an unexpected exception can never leave the row
            // stuck at IN_PROGRESS until the next process restart.
            DownloadResult.Failure(FailureKind.NETWORK, e.message ?: e.javaClass.simpleName)
        }

    private suspend fun record(workId: Long, result: DownloadResult) {
        when (result) {
            is DownloadResult.Success -> {
                library.saveDownload(workId, result)
                queue.remove(workId)
                afterSuccess(workId, result)
            }
            is DownloadResult.Failure ->
                if (countsAsAttempt(result.kind, isOnline())) queue.recordFailure(workId, result)
                else queue.waitForConnection(workId)
            DownloadResult.Cancelled -> queue.remove(workId)
        }
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
