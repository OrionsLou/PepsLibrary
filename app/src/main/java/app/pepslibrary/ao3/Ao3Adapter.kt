package app.pepslibrary.ao3

import android.net.Uri

/**
 * All AO3-specific knowledge lives under this package: hosts, URLs, and later the selectors, injected JS and
 * parsing. Keep it here so a change on AO3's side is a one-place fix.
 */
object Ao3 {
    const val HOME_URL = "https://archiveofourown.org/"
    private const val HOST = "archiveofourown.org"

    /** True for https pages on AO3 itself; anything else must not be loaded inside the app's WebView. */
    fun isAo3Url(uri: Uri): Boolean {
        if (uri.scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return host == HOST || host.endsWith(".$HOST")
    }
}
