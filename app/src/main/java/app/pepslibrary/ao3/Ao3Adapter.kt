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

    // Work metadata. Verified against a real work page on 2026-09-21. The work header is `div.preface.group`;
    // each chapter has its own header, `div.chapter.preface.group`, with a chapter summary we must not pick up.
    private const val WORK_PREFACE = "div.preface.group:not(.chapter)"
    private const val TITLE_SELECTOR = "$WORK_PREFACE h2.title.heading"
    private const val BYLINE_SELECTOR = "$WORK_PREFACE h3.byline.heading"
    private const val AUTHOR_SELECTOR = "$BYLINE_SELECTOR a[rel=author]"
    private const val SUMMARY_SELECTOR = "$WORK_PREFACE div.summary blockquote.userstuff"
    private const val META = "dl.work.meta"

    /** The stats read like "3/10" or "3/?" (author hasn't set a total). */
    private val CHAPTERS = Regex("^\\s*(\\d+)\\s*/\\s*(\\d+|\\?)\\s*$")
    private val ISO_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val UPDATED_AT_PARAM = Regex("[?&]updated_at=(\\d+)")

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

    /**
     * The `updated_at` value AO3 puts on download links. It changes when the work does, so it identifies which
     * version of a work a saved EPUB is (used later to spot work-in-progress updates).
     */
    fun updatedAtFromDownloadUrl(url: String?): Long? =
        url?.let { UPDATED_AT_PARAM.find(it)?.groupValues?.get(1)?.toLongOrNull() }

    /**
     * Reads a work's metadata from its page [html]. Best effort: anything AO3 changed or left out comes back
     * empty or null instead of failing, so a markup change can never stop a download.
     */
    fun parseWorkMetadata(html: String): WorkMetadata {
        val doc = Jsoup.parse(html)
        fun tags(kind: String): List<String> =
            doc.select("$META dd.$kind.tags a.tag").map { it.text().trim() }.filter { it.isNotEmpty() }
        fun stat(kind: String): String? =
            doc.selectFirst("$META dd.$kind")?.text()?.trim()?.takeIf { it.isNotEmpty() }
        fun date(kind: String): String? = stat(kind)?.takeIf { ISO_DATE.matches(it) }

        val authors = doc.select(AUTHOR_SELECTOR).map { it.text().trim() }.filter { it.isNotEmpty() }
            // Anonymous works have a byline but no author link.
            .ifEmpty { listOfNotNull(doc.selectFirst(BYLINE_SELECTOR)?.text()?.trim()?.takeIf { it.isNotEmpty() }) }

        val summary = doc.selectFirst(SUMMARY_SELECTOR)?.let { quote ->
            quote.select("p").map { it.text().trim() }.filter { it.isNotEmpty() }
                .ifEmpty { listOf(quote.text().trim()) }
                .joinToString("\n\n")
        }?.takeIf { it.isNotEmpty() }

        val chapters = stat("chapters")?.let { CHAPTERS.find(it) }?.groupValues

        return WorkMetadata(
            title = doc.selectFirst(TITLE_SELECTOR)?.text()?.trim()?.takeIf { it.isNotEmpty() },
            authors = authors,
            summary = summary,
            rating = tags("rating").firstOrNull(),
            warnings = tags("warning"),
            categories = tags("category"),
            fandoms = tags("fandom"),
            relationships = tags("relationship"),
            characters = tags("character"),
            tags = tags("freeform"),
            language = stat("language"),
            // "12,345" (or "12.345" in other locales): keep the digits.
            words = stat("words")?.filter { it.isDigit() }?.toIntOrNull(),
            chaptersPublished = chapters?.get(1)?.toIntOrNull(),
            chaptersTotal = chapters?.get(2)?.toIntOrNull(), // "?" -> null
            publishedDate = date("published"),
            updatedDate = date("status"), // labelled "Updated:" or, once finished, "Completed:"
        )
    }

    private fun isEpubUrl(url: String): Boolean =
        isAo3Url(url) && URI(url).path.endsWith(".epub", ignoreCase = true)
}
