package app.pepslibrary.network

import android.webkit.CookieManager
import app.pepslibrary.ao3.Ao3
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Where cookies actually live. A seam so the jar's rules can be unit tested without Android. */
interface CookieStore {
    /** The `Cookie:`-style header (`a=1; b=2`) for [url], or null. */
    fun get(url: String): String?
    fun set(url: String, setCookie: String)
    fun flush()
}

/** The WebView's own cookie store, so a sign-in made in the browser is visible to downloads. */
object WebViewCookieStore : CookieStore {
    override fun get(url: String): String? = CookieManager.getInstance().getCookie(url)
    override fun set(url: String, setCookie: String) = CookieManager.getInstance().setCookie(url, setCookie)
    override fun flush() = CookieManager.getInstance().flush()
}

/**
 * Shares the WebView's session with OkHttp. Cookies are read from the [store] for every request (redirect hops
 * included), and cookies AO3 or Cloudflare rotate in a response are written back. Nothing is ever sent to, or
 * stored from, a non-AO3 host.
 */
class WebViewCookieJar(private val store: CookieStore = WebViewCookieStore) : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (!Ao3.isAo3Url(url.toString())) return emptyList()
        val header = store.get(url.toString()) ?: return emptyList()
        return parseCookieHeader(url, header)
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty() || !Ao3.isAo3Url(url.toString())) return
        cookies.forEach { store.set(url.toString(), it.toString()) }
        store.flush()
    }
}

/** Splits a `Cookie:`-style header (`a=1; b=2`), as returned by CookieManager, into OkHttp cookies for [url]. */
internal fun parseCookieHeader(url: HttpUrl, header: String): List<Cookie> =
    header.split(";").mapNotNull { Cookie.parse(url, it.trim()) }
