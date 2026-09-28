package app.pepslibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueIconTest {
    @Test
    fun anEmptyQueueHasNoBadge() {
        assertNull(queueBadgeText(0))
    }

    @Test
    fun theBadgeCountsTheQueue_cappedAt99Plus() {
        assertEquals("1", queueBadgeText(1))
        assertEquals("99", queueBadgeText(99))
        assertEquals("99+", queueBadgeText(100))
    }

    @Test
    fun theDescriptionNamesTheButtonAndSaysHowManyWorks() {
        assertEquals("Download queue", queueButtonDescription(0))
        assertEquals("Download queue, 1 work", queueButtonDescription(1))
        assertEquals("Download queue, 3 works", queueButtonDescription(3))
    }
}
