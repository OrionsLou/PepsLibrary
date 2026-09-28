package app.pepslibrary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalLinksTest {
    @Test
    fun webSchemesAreOpenable() {
        assertTrue(isOpenableExternally("https"))
        assertTrue(isOpenableExternally("http"))
    }

    @Test
    fun schemeMatchingIsCaseInsensitive() {
        assertTrue(isOpenableExternally("HTTPS"))
        assertTrue(isOpenableExternally("Http"))
    }

    @Test
    fun nonWebSchemesAreDropped() {
        listOf("javascript", "file", "intent", "content", "data", "market", "tel", "mailto", "ftp").forEach {
            assertFalse("expected not openable: $it", isOpenableExternally(it))
        }
    }

    @Test
    fun missingOrEmptySchemeIsDropped() {
        assertFalse(isOpenableExternally(null))
        assertFalse(isOpenableExternally(""))
    }

    // --- bookLinkTarget ---

    @Test
    fun ao3LinksInABookOpenInTheAppsBrowser() {
        listOf(
            "https://archiveofourown.org/works/80705631",
            "https://archiveofourown.org/works/80705631/chapters/211980101#comments",
            "https://archiveofourown.org/users/someone/pseuds/someone",
        ).forEach { assertEquals(it, BookLinkTarget.IN_APP, bookLinkTarget(it)) }
    }

    @Test
    fun otherWebLinksOpenInThePhonesBrowser() {
        listOf(
            "https://example.com/page",
            "http://archiveofourown.org/works/1", // not https, so not one the app's browser would load
            "https://archiveofourown.org.evil.com/works/1",
            "https://archiveofourown.org@evil.com/",
        ).forEach { assertEquals(it, BookLinkTarget.EXTERNAL, bookLinkTarget(it)) }
    }

    @Test
    fun nonWebLinksGoNowhere() {
        listOf("javascript:alert(1)", "file:///sdcard/x", "intent://x", "mailto:a@b.c", "no-scheme-at-all")
            .forEach { assertEquals(it, BookLinkTarget.IGNORE, bookLinkTarget(it)) }
    }
}
