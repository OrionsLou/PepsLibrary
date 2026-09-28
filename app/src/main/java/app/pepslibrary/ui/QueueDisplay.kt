package app.pepslibrary.ui

import app.pepslibrary.data.DownloadQueueEntity
import app.pepslibrary.data.MAX_ATTEMPTS
import app.pepslibrary.data.QueueStatus
import app.pepslibrary.download.DownloadProgress

/**
 * What the download bar should say about a work's queue entry, or null when it isn't queued at all. While
 * [online] is false the queue is paused, so anything waiting says so rather than implying it's about to run.
 */
internal fun queueStatusLabel(
    entry: DownloadQueueEntity?,
    online: Boolean = true,
    /** What the running download is doing, when this entry is the one running. */
    progress: DownloadProgress? = null,
): String? = when {
    entry == null -> null
    entry.status == QueueStatus.IN_PROGRESS -> progress?.let(::downloadProgressLabel) ?: "Downloading..."
    entry.status == QueueStatus.FAILED -> "Failed: ${entry.lastFailureMessage}"
    !online -> "Waiting for a connection. It will download once you're back online."
    // PENDING with a retry time set means a failure already happened and this is waiting its turn to try again.
    entry.notBeforeMillis != null ->
        "Retrying automatically (attempt ${entry.attempts} of $MAX_ATTEMPTS failed so far): ${entry.lastFailureMessage}"
    else -> "Queued, waiting its turn..."
}

/** While a work is downloading, its button is Cancel; otherwise (not queued, waiting, failed) it (re-)starts it. */
internal fun queueShowsCancel(entry: DownloadQueueEntity?): Boolean = entry?.status == QueueStatus.IN_PROGRESS

/** "Download EPUB" normally; "Retry download" once it's landed on FAILED, since tapping re-enqueues it. */
internal fun queueButtonLabel(entry: DownloadQueueEntity?): String =
    if (entry?.status == QueueStatus.FAILED) "Retry download" else "Download EPUB"

/** What a running download is doing, in words: the page, AO3 building the file, then how much has arrived. */
internal fun downloadProgressLabel(progress: DownloadProgress): String = when (progress) {
    DownloadProgress.LoadingPage -> "Loading the work page..."
    DownloadProgress.WaitingForAo3 -> "Waiting for AO3 to prepare the file..."
    is DownloadProgress.Receiving -> progress.total
        ?.let { "Downloading, ${formatFileSize(progress.bytes)} of ${formatFileSize(it)}" }
        ?: "Downloading, ${formatFileSize(progress.bytes)}"
}

/** How much of the file has arrived, 0 to 1, or null while that isn't known (a moving bar is shown instead). */
internal fun downloadProgressFraction(progress: DownloadProgress?): Float? {
    val receiving = progress as? DownloadProgress.Receiving ?: return null
    val total = receiving.total ?: return null
    return (receiving.bytes.toFloat() / total).coerceIn(0f, 1f)
}
