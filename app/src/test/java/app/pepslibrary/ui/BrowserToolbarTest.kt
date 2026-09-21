package app.pepslibrary.ui

import app.pepslibrary.ao3.Ao3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserToolbarTest {
    @Test
    fun refreshReloadsTheCurrentPageWhenThereIsOne() {
        assertNull(refreshTarget("https://archiveofourown.org/works/123"))
        assertNull(refreshTarget("https://archiveofourown.org/"))
    }

    @Test
    fun refreshStartsOverFromHomeWhenNothingEverLoaded() {
        assertEquals(Ao3.HOME_URL, refreshTarget(null))
        assertEquals(Ao3.HOME_URL, refreshTarget(""))
        assertEquals(Ao3.HOME_URL, refreshTarget("   "))
    }
}
