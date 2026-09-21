package app.pepslibrary.ui

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
}
