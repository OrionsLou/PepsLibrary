package app.pepslibrary.ui

/** An AO3 link tapped in a book: the page to open, and the work it was tapped in, for Back to return to. */
data class LinkFromBook(val url: String, val workId: Long)

/**
 * Remembers that a page in the browser was opened from a link in a book, so Back on that page reopens the book
 * instead of going to the previous web page. Pages followed from there go back through history as usual until
 * they reach the linked page again.
 */
internal class ReturnToReader(val workId: Long) {
    /** The linked page's place in the browser's history, once it has loaded; null until then. */
    var historyIndex: Int? = null
        private set

    /** Called on every history change; the first after the link starts loading is the linked page. */
    fun onHistory(currentIndex: Int) {
        if (historyIndex == null) historyIndex = currentIndex
    }

    /** Whether Back, on the page at [currentIndex], should reopen the book. */
    fun appliesAt(currentIndex: Int): Boolean = historyIndex == currentIndex
}
