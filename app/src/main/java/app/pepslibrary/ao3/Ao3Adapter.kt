package app.pepslibrary.ao3

import org.jsoup.Jsoup
import java.net.URI
import java.net.URISyntaxException

/**
 * All AO3-specific knowledge lives under this package: hosts, URLs, and later the selectors, injected JS and
 * parsing. Keep it here so a change on AO3's side is a one-place fix.
 */
object Ao3 {
    const val HOME_URL = "https://archiveofourown.org/"
    private const val HOST = "archiveofourown.org"

    /** Work pages, including the `/works/<id>/chapters/<chapter id>` form AO3 redirects multi-chapter works to. */
    private val WORK_PATH = Regex("^/works/(\\d+)(?:/|$)")

    /** Download links on a work page: `li.download` holds the AZW3/EPUB/MOBI/PDF/HTML links. */
    private const val DOWNLOAD_LINK_SELECTOR = "li.download a[href]"

    /**
     * True for https pages on AO3 itself (including subdomains such as download.archiveofourown.org); anything
     * else must not be loaded inside the app's WebView. Takes a String and uses java.net.URI so it can be unit
     * tested on the JVM without Android's Uri.
     */
    fun isAo3Url(url: String?): Boolean {
        val uri = try {
            URI(url ?: return false)
        } catch (_: URISyntaxException) {
            return false
        }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host == HOST || host.endsWith(".$HOST")
    }

    /** The work page for [workId]. `view_adult=true` skips the adult-content interstitial. */
    fun workUrl(workId: Long): String = "https://$HOST/works/$workId?view_adult=true"

    /** The work ID if [url] is an AO3 work page (or one of its chapters), else null. */
    fun workIdFromUrl(url: String?): Long? {
        if (!isAo3Url(url)) return null
        val path = URI(url!!).path ?: return null
        return WORK_PATH.find(path)?.groupValues?.get(1)?.toLongOrNull()
    }

    /**
     * Finds the EPUB download link in a work page's [html] and resolves it against [pageUrl]. Returns null if
     * there is none, or if it would point anywhere other than AO3 (we send session cookies with the request).
     */
    fun findEpubUrl(html: String, pageUrl: String): String? =
        Jsoup.parse(html, pageUrl)
            .select(DOWNLOAD_LINK_SELECTOR)
            .asSequence()
            .map { it.absUrl("href") }
            .firstOrNull { isEpubUrl(it) }

    private fun isEpubUrl(url: String): Boolean =
        isAo3Url(url) && URI(url).path.endsWith(".epub", ignoreCase = true)
}
