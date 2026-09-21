package app.pepslibrary.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewCookieJarTest {
    private val url = "https://archiveofourown.org/works/1".toHttpUrl()

    private class FakeStore(private val cookieHeader: String? = null) : CookieStore {
        val reads = mutableListOf<String>()
        val writes = mutableListOf<Pair<String, String>>()
        var flushes = 0
        override fun get(url: String): String? = cookieHeader.also { reads += url }
        override fun set(url: String, setCookie: String) { writes += url to setCookie }
        override fun flush() { flushes++ }
    }

    // --- parseCookieHeader ---

    @Test
    fun cookieHeaderIsSplitIntoNamedCookies() {
        val cookies = parseCookieHeader(url, "_otwarchive_session=abc; user_credentials=1; __cf_bm=x.y-z")
        assertEquals(listOf("_otwarchive_session", "user_credentials", "__cf_bm"), cookies.map { it.name })
        assertEquals(listOf("abc", "1", "x.y-z"), cookies.map { it.value })
    }

    @Test
    fun valuesContainingEqualsSignsSurvive() {
        assertEquals("a=b==", parseCookieHeader(url, "token=a=b==").single().value)
    }

    @Test
    fun emptyAndGarbagePiecesAreSkipped() {
        assertTrue(parseCookieHeader(url, "").isEmpty())
        assertEquals(listOf("a"), parseCookieHeader(url, "a=1;;  ; no-equals-sign").map { it.name })
    }

    @Test
    fun parsedCookiesMatchTheRequestUrl() {
        assertTrue(parseCookieHeader(url, "a=1").single().matches(url))
    }

    // --- the jar's host rules ---

    @Test
    fun ao3RequestsGetTheStoredCookies() {
        val store = FakeStore("_otwarchive_session=abc; user_credentials=1")
        val cookies = WebViewCookieJar(store).loadForRequest(url)
        assertEquals(listOf("_otwarchive_session", "user_credentials"), cookies.map { it.name })
        assertEquals(listOf(url.toString()), store.reads)
    }

    @Test
    fun ao3SubdomainsAndRedirectHopsAreLookedUpByTheirOwnUrl() {
        val hop = "https://download.archiveofourown.org/downloads/1/T.epub".toHttpUrl()
        val store = FakeStore("a=1")
        assertEquals(1, WebViewCookieJar(store).loadForRequest(hop).size)
        assertEquals(listOf(hop.toString()), store.reads)
    }

    @Test
    fun nonAo3HostsNeverGetCookies_andTheStoreIsNotEvenAsked() {
        val store = FakeStore("_otwarchive_session=secret")
        val jar = WebViewCookieJar(store)
        listOf(
            "https://evil.com/",
            "https://archiveofourown.org.evil.com/",
            "https://evilarchiveofourown.org/",
            "http://archiveofourown.org/",
        ).forEach {
            assertTrue("cookies leaked to $it", jar.loadForRequest(it.toHttpUrl()).isEmpty())
        }
        assertTrue(store.reads.isEmpty())
    }

    @Test
    fun noStoredCookiesGivesAnEmptyList() {
        assertTrue(WebViewCookieJar(FakeStore(null)).loadForRequest(url).isEmpty())
    }

    @Test
    fun rotatedAo3CookiesAreWrittenBackAndFlushed() {
        val store = FakeStore()
        val cookie = Cookie.parse(url, "__cf_bm=new; Path=/; Secure; HttpOnly")!!
        WebViewCookieJar(store).saveFromResponse(url, listOf(cookie))
        assertEquals(1, store.writes.size)
        assertEquals(url.toString(), store.writes.single().first)
        assertTrue(store.writes.single().second.startsWith("__cf_bm=new"))
        assertEquals(1, store.flushes)
    }

    @Test
    fun cookiesFromNonAo3HostsAreNotStored() {
        val store = FakeStore()
        val evil = "https://evil.com/".toHttpUrl()
        val cookie = Cookie.parse(evil, "tracker=1")!!
        WebViewCookieJar(store).saveFromResponse(evil, listOf(cookie))
        assertTrue(store.writes.isEmpty())
        assertEquals(0, store.flushes)
    }

    @Test
    fun savingNoCookiesDoesNothing() {
        val store = FakeStore()
        WebViewCookieJar(store).saveFromResponse(url, emptyList())
        assertFalse(store.flushes > 0)
        assertTrue(store.writes.isEmpty())
    }
}
