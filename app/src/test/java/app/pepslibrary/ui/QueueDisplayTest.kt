package app.pepslibrary.ui

import app.pepslibrary.data.DownloadQueueEntity
import app.pepslibrary.data.MAX_ATTEMPTS
import app.pepslibrary.data.QueueStatus
import app.pepslibrary.download.DownloadProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueDisplayTest {
    private fun entry(
        status: QueueStatus,
        attempts: Int = 1,
        notBeforeMillis: Long? = null,
        lastFailureMessage: String? = null,
    ) = DownloadQueueEntity(
        workId = 1,
        status = status,
        attempts = attempts,
        notBeforeMillis = notBeforeMillis,
        lastFailureKind = null,
        lastFailureMessage = lastFailureMessage,
        enqueuedAt = 0,
    )

    // --- queueStatusLabel ---

    @Test
    fun notQueuedHasNoLabel() {
        assertNull(queueStatusLabel(null))
    }

    @Test
    fun freshlyQueuedSaysWaitingItsTurn() {
        assertEquals("Queued, waiting its turn...", queueStatusLabel(entry(QueueStatus.PENDING, notBeforeMillis = null)))
    }

    @Test
    fun inProgressSaysDownloading() {
        assertEquals("Downloading...", queueStatusLabel(entry(QueueStatus.IN_PROGRESS)))
    }

    @Test
    fun pendingWithARetryTimeMentionsTheAttemptAndTheFailure() {
        val label = queueStatusLabel(entry(QueueStatus.PENDING, attempts = 2, notBeforeMillis = 5_000, lastFailureMessage = "network blip"))
        assertTrue(label, label!!.contains("attempt 2 of $MAX_ATTEMPTS"))
        assertTrue(label, label.contains("network blip"))
    }

    @Test
    fun failedShowsTheFailureMessage() {
        assertEquals("Failed: no epub link", queueStatusLabel(entry(QueueStatus.FAILED, lastFailureMessage = "no epub link")))
    }

    @Test
    fun offlineWaitingWorksSaySoInsteadOfLookingAboutToRun() {
        val waiting = "Waiting for a connection. It will download once you're back online."
        assertEquals(waiting, queueStatusLabel(entry(QueueStatus.PENDING), online = false))
        assertEquals(waiting, queueStatusLabel(entry(QueueStatus.PENDING, notBeforeMillis = 5), online = false))
    }

    @Test
    fun offlineLeavesDownloadingAndFailedLabelsAlone() {
        assertEquals("Downloading...", queueStatusLabel(entry(QueueStatus.IN_PROGRESS), online = false))
        assertEquals(
            "Failed: no epub link",
            queueStatusLabel(entry(QueueStatus.FAILED, lastFailureMessage = "no epub link"), online = false),
        )
        assertNull(queueStatusLabel(null, online = false))
    }

    // --- queueShowsCancel ---

    @Test
    fun cancelIsOfferedOnlyWhileInProgress() {
        assertFalse(queueShowsCancel(null))
        assertFalse(queueShowsCancel(entry(QueueStatus.PENDING)))
        assertFalse(queueShowsCancel(entry(QueueStatus.FAILED)))
        assertTrue(queueShowsCancel(entry(QueueStatus.IN_PROGRESS)))
    }

    // --- queueButtonLabel ---

    @Test
    fun buttonLabelIsDownloadExceptWhenFailed() {
        assertEquals("Download EPUB", queueButtonLabel(null))
        assertEquals("Download EPUB", queueButtonLabel(entry(QueueStatus.PENDING)))
        assertEquals("Download EPUB", queueButtonLabel(entry(QueueStatus.IN_PROGRESS)))
        assertEquals("Retry download", queueButtonLabel(entry(QueueStatus.FAILED)))
    }

    // --- download progress ---

    @Test
    fun progressIsDescribedStageByStage() {
        assertEquals("Loading the work page...", downloadProgressLabel(DownloadProgress.LoadingPage))
        assertEquals("Waiting for AO3 to prepare the file...", downloadProgressLabel(DownloadProgress.WaitingForAo3))
        assertEquals("Downloading, 123 KB of 426 KB", downloadProgressLabel(DownloadProgress.Receiving(123_000, 426_000)))
        assertEquals("Downloading, 123 KB", downloadProgressLabel(DownloadProgress.Receiving(123_000, null)))
    }

    @Test
    fun aRunningEntrySaysWhatItsDoing_fallingBackToDownloading() {
        val running = entry(QueueStatus.IN_PROGRESS)
        assertEquals("Waiting for AO3 to prepare the file...", queueStatusLabel(running, progress = DownloadProgress.WaitingForAo3))
        assertEquals("Downloading...", queueStatusLabel(running))
    }

    @Test
    fun theBarFillsOnlyWhenTheSizeIsKnown() {
        assertEquals(0.5f, downloadProgressFraction(DownloadProgress.Receiving(50, 100)))
        assertEquals(1f, downloadProgressFraction(DownloadProgress.Receiving(150, 100)))
        assertNull(downloadProgressFraction(DownloadProgress.Receiving(50, null)))
        assertNull(downloadProgressFraction(DownloadProgress.WaitingForAo3))
        assertNull(downloadProgressFraction(null))
    }

    // --- queueRowTitle ---

    @Test
    fun aRowShowsItsTitle_orItsIdWhenTheTitleIsUnknown() {
        assertEquals("Some Work", queueRowTitle(entry(QueueStatus.PENDING).copy(title = "Some Work")))
        assertEquals("Work 1", queueRowTitle(entry(QueueStatus.PENDING)))
    }
}
