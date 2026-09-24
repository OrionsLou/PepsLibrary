package app.pepslibrary.ui

import app.pepslibrary.data.DownloadQueueEntity
import app.pepslibrary.data.MAX_ATTEMPTS
import app.pepslibrary.data.QueueStatus

/** What the download bar should say about a work's queue entry, or null when it isn't queued at all. */
internal fun queueStatusLabel(entry: DownloadQueueEntity?): String? = when {
    entry == null -> null
    entry.status == QueueStatus.IN_PROGRESS -> "Downloading..."
    entry.status == QueueStatus.FAILED -> "Failed: ${entry.lastFailureMessage}"
    // PENDING with a retry time set means a failure already happened and this is waiting its turn to try again.
    entry.notBeforeMillis != null ->
        "Retrying automatically (attempt ${entry.attempts} of $MAX_ATTEMPTS failed so far): ${entry.lastFailureMessage}"
    else -> "Queued, waiting its turn..."
}

/** Downloading disables the button; everything else (not queued, waiting, failed) can be (re-)started by tapping it. */
internal fun queueButtonEnabled(entry: DownloadQueueEntity?): Boolean = entry?.status != QueueStatus.IN_PROGRESS

/** "Download EPUB" normally; "Retry download" once it's landed on FAILED, since tapping re-enqueues it. */
internal fun queueButtonLabel(entry: DownloadQueueEntity?): String =
    if (entry?.status == QueueStatus.FAILED) "Retry download" else "Download EPUB"
