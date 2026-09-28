package app.pepslibrary.ui

import app.pepslibrary.ao3.Ao3

/**
 * Only plain web links may be handed to other apps. Anything else (javascript:, file:, intent:, content:, ...)
 * is dropped rather than turned into an Intent.
 */
internal fun isOpenableExternally(scheme: String?): Boolean =
    scheme.equals("https", ignoreCase = true) || scheme.equals("http", ignoreCase = true)

/** Where a link tapped inside a book goes. */
internal enum class BookLinkTarget {
    /** An AO3 page: the reader closes and the app's own browser opens it, signed in as usual. */
    IN_APP,

    /** Any other web link: the phone's browser, so the app's browser stays on AO3. */
    EXTERNAL,

    /** Not a web link at all: nothing happens. */
    IGNORE,
}

internal fun bookLinkTarget(url: String): BookLinkTarget = when {
    Ao3.isAo3Url(url) -> BookLinkTarget.IN_APP
    isOpenableExternally(url.substringBefore(':', missingDelimiterValue = "")) -> BookLinkTarget.EXTERNAL
    else -> BookLinkTarget.IGNORE
}
