package app.pepslibrary.ao3

import java.net.URI
import java.net.URISyntaxException

/**
 * All AO3-specific knowledge lives under this package: hosts, URLs, and later the selectors, injected JS and
 * parsing. Keep it here so a change on AO3's side is a one-place fix.
 */
object Ao3 {
    const val HOME_URL = "https://archiveofourown.org/"
    private const val HOST = "archiveofourown.org"

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
}
