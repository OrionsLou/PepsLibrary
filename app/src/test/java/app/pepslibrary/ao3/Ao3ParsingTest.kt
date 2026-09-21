package app.pepslibrary.ao3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Ao3ParsingTest {
    private val pageUrl = "https://archiveofourown.org/works/92982131/chapters/248031186"

    private fun fixture(name: String): String =
        checkNotNull(javaClass.getResource("/ao3/$name")) { "missing fixture $name" }.readText()

    // --- workIdFromUrl ---

    @Test
    fun workIdIsReadFromWorkAndChapterUrls() {
        assertEquals(123L, Ao3.workIdFromUrl("https://archiveofourown.org/works/123"))
        assertEquals(123L, Ao3.workIdFromUrl("https://archiveofourown.org/works/123/"))
        assertEquals(92982131L, Ao3.workIdFromUrl(pageUrl))
        assertEquals(123L, Ao3.workIdFromUrl("https://archiveofourown.org/works/123?view_adult=true#comments"))
        assertEquals(123L, Ao3.workIdFromUrl("https://archiveofourown.org/works/123/navigate"))
    }

    @Test
    fun nonWorkPagesHaveNoWorkId() {
        listOf(
            "https://archiveofourown.org/",
            "https://archiveofourown.org/works",
            "https://archiveofourown.org/works/search?work_search%5Bquery%5D=x",
            "https://archiveofourown.org/works/new",
            "https://archiveofourown.org/works/123abc",
            "https://archiveofourown.org/tags/Batman/works",
            "https://archiveofourown.org/users/someone/works",
            "https://archiveofourown.org/series/123",
            "https://archiveofourown.org/collections/works/123",
        ).forEach { assertNull("expected no id: $it", Ao3.workIdFromUrl(it)) }
    }

    @Test
    fun workIdRequiresAnAo3Url() {
        assertNull(Ao3.workIdFromUrl("https://evil.com/works/123"))
        assertNull(Ao3.workIdFromUrl("https://archiveofourown.org.evil.com/works/123"))
        assertNull(Ao3.workIdFromUrl("http://archiveofourown.org/works/123"))
        assertNull(Ao3.workIdFromUrl(null))
        assertNull(Ao3.workIdFromUrl(""))
    }

    @Test
    fun absurdlyLongWorkIdDoesNotCrash() {
        assertNull(Ao3.workIdFromUrl("https://archiveofourown.org/works/99999999999999999999999"))
    }

    @Test
    fun workUrlSkipsTheAdultInterstitial() {
        assertEquals("https://archiveofourown.org/works/42?view_adult=true", Ao3.workUrl(42))
    }

    // --- findEpubUrl ---

    @Test
    fun findsTheEpubLinkInRealMarkupAndResolvesItAgainstThePageUrl() {
        assertEquals(
            "https://archiveofourown.org/downloads/92982131/Bruces_Business.epub?updated_at=1789956295",
            Ao3.findEpubUrl(fixture("work_page_download_block.html"), pageUrl),
        )
    }

    @Test
    fun otherFormatsAreNotPicked() {
        val html = """<li class="download"><ul><li><a href="/downloads/1/T.pdf">PDF</a></li>
            <li><a href="/downloads/1/T.mobi">MOBI</a></li></ul></li>"""
        assertNull(Ao3.findEpubUrl(html, pageUrl))
    }

    @Test
    fun anEpubLinkOnAnotherAo3HostIsAccepted() {
        val html = """<li class="download"><a href="https://download.archiveofourown.org/downloads/1/T.epub">EPUB</a></li>"""
        assertEquals(
            "https://download.archiveofourown.org/downloads/1/T.epub",
            Ao3.findEpubUrl(html, pageUrl),
        )
    }

    @Test
    fun anEpubLinkPointingOffAo3IsRefusedBecauseCookiesWouldFollowIt() {
        listOf(
            "https://evil.com/downloads/1/T.epub",
            "https://archiveofourown.org.evil.com/downloads/1/T.epub",
            "http://archiveofourown.org/downloads/1/T.epub",
            "javascript:alert(1)//.epub",
        ).forEach {
            val html = """<li class="download"><a href="$it">EPUB</a></li>"""
            assertNull("expected refused: $it", Ao3.findEpubUrl(html, pageUrl))
        }
    }

    @Test
    fun anEpubLinkOutsideTheDownloadListIsIgnored() {
        val html = """<div class="summary"><a href="/downloads/1/T.epub">sneaky</a></div>"""
        assertNull(Ao3.findEpubUrl(html, pageUrl))
    }

    @Test
    fun pagesWithoutADownloadListGiveNull() {
        assertNull(Ao3.findEpubUrl("", pageUrl))
        assertNull(Ao3.findEpubUrl("<html><body><p>Error 404</p></body></html>", pageUrl))
    }
}
