package app.pepslibrary.ao3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ao3Test {
    private fun assertAllowed(vararg urls: String) =
        urls.forEach { assertTrue("expected allowed: $it", Ao3.isAo3Url(it)) }

    private fun assertRejected(vararg urls: String?) =
        urls.forEach { assertFalse("expected rejected: $it", Ao3.isAo3Url(it)) }

    @Test
    fun homeUrlIsAllowed() {
        assertEquals("https://archiveofourown.org/", Ao3.HOME_URL)
        assertAllowed(Ao3.HOME_URL)
    }

    @Test
    fun ordinaryAo3PagesAreAllowed() {
        assertAllowed(
            "https://archiveofourown.org",
            "https://archiveofourown.org/works/123456",
            "https://archiveofourown.org/works/123456/chapters/789?view_adult=true#comments",
            "https://archiveofourown.org/works/search?work_search%5Bquery%5D=tea+and+sympathy",
        )
    }

    @Test
    fun subdomainsAreAllowed() {
        assertAllowed(
            "https://www.archiveofourown.org/",
            "https://download.archiveofourown.org/downloads/123456/Some_Title.epub",
        )
    }

    @Test
    fun hostAndSchemeMatchingIsCaseInsensitive() {
        assertAllowed(
            "https://ArchiveOfOurOwn.ORG/",
            "HTTPS://archiveofourown.org/",
        )
    }

    @Test
    fun explicitDefaultPortIsAllowed() {
        assertAllowed("https://archiveofourown.org:443/works/1")
    }

    @Test
    fun lookalikeHostsAreRejected() {
        assertRejected(
            "https://archiveofourown.org.evil.com/",
            "https://evilarchiveofourown.org/",
            "https://notarchiveofourown.org/",
            "https://archiveofourown.com/",
            "https://archiveofourown.org-evil.com/",
        )
    }

    @Test
    fun ao3NameInUserInfoPathOrQueryDoesNotCount() {
        assertRejected(
            "https://archiveofourown.org@evil.com/",
            "https://archiveofourown.org:secret@evil.com/",
            "https://evil.com/?u=archiveofourown.org",
            "https://evil.com/archiveofourown.org",
            "https://evil.com/#archiveofourown.org",
        )
    }

    @Test
    fun ao3UserInfoOnTheRealHostIsStillTheRealHost() {
        assertAllowed("https://someone@archiveofourown.org/")
    }

    @Test
    fun backslashAndOtherSyntaxTricksAreRejected() {
        assertRejected(
            "https://evil.com\\@archiveofourown.org/",
            "https://archiveofourown.org\\.evil.com/",
            "https://archiveofourown.org%2eevil.com/",
            "https://archive of our own.org/",
        )
    }

    @Test
    fun onlyHttpsIsAllowed() {
        assertRejected(
            "http://archiveofourown.org/",
            "ftp://archiveofourown.org/",
            "javascript:alert(1)//archiveofourown.org",
            "file://archiveofourown.org/etc/passwd",
            "intent://archiveofourown.org/#Intent;scheme=https;end",
            "data:text/html,archiveofourown.org",
            "content://archiveofourown.org/x",
        )
    }

    @Test
    fun degenerateInputIsRejected() {
        assertRejected(
            null,
            "",
            " ",
            "https://",
            "https:///works/1",
            "archiveofourown.org",
            "//archiveofourown.org/",
            "/works/123",
            "not a url",
        )
    }

    @Test
    fun trailingDotHostIsRejectedConservatively() {
        assertRejected("https://archiveofourown.org./")
    }
}
