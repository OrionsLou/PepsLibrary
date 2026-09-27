package app.pepslibrary.ui

import app.pepslibrary.data.WorkEntity
import java.text.Normalizer
import java.util.Locale

/** Each option's natural direction (newest first, A–Z) and its reverse, as labels. */
enum class LibrarySort(val label: String, val naturalDirection: String, val reversedDirection: String) {
    OPENED("Last read", "most recent first", "least recent first"),
    DOWNLOADED("Date downloaded", "newest first", "oldest first"),
    TITLE("Title", "A–Z", "Z–A"),
    AUTHOR("Author", "A–Z", "Z–A"),
}

/** What the library is ordered by. Choosing the current option again reverses it; another option starts natural. */
data class LibraryOrder(val sort: LibrarySort = LibrarySort.OPENED, val reversed: Boolean = false) {
    fun select(option: LibrarySort): LibraryOrder =
        if (option == sort) copy(reversed = !reversed) else LibraryOrder(option)

    val label: String get() = "${sort.label}, ${if (reversed) sort.reversedDirection else sort.naturalDirection}"
}

/**
 * Orders the library. Only the chosen key flips when reversed: ties still fall back to newest download first, works
 * never opened stay at the end of a "Last read" sort and works with no known author at the end of an author sort,
 * either way. Titles compare by [titleSortKey]; a plain
 * key rather than java.text.Collator, whose handling of spaces differs between the JVM the tests run on and Android.
 */
fun sortWorks(works: List<WorkEntity>, order: LibraryOrder): List<WorkEntity> {
    val newestFirst = compareByDescending<WorkEntity> { it.downloadedAt }
    fun Comparator<WorkEntity>.directed() = if (order.reversed) reversed() else this
    return when (order.sort) {
        LibrarySort.OPENED -> works.sortedWith(
            compareBy<WorkEntity> { it.lastOpenedAt == null }
                .then(compareByDescending<WorkEntity> { it.lastOpenedAt }.directed())
                .then(newestFirst),
        )
        LibrarySort.DOWNLOADED -> works.sortedWith(newestFirst.directed())
        LibrarySort.TITLE -> works.sortedWith(compareBy<WorkEntity> { titleSortKey(it.title) }.directed().then(newestFirst))
        // By the first listed author, AO3's byline order; one author's works by title.
        LibrarySort.AUTHOR -> works.sortedWith(
            compareBy<WorkEntity> { it.authors.isEmpty() }
                .then(
                    compareBy<WorkEntity> { it.authors.firstOrNull()?.let(::titleSortKey) }
                        .thenBy { titleSortKey(it.title) }
                        .directed(),
                )
                .then(newestFirst),
        )
    }
}

/**
 * Used for author names too. Case and accents ignored ("Élan" sorts with "elan"), a leading "The"/"A" kept as AO3
 * does, and word by word: a space sorts before any letter, so "A Quiet Place" comes before "Album".
 */
internal fun titleSortKey(title: String): String =
    Normalizer.normalize(title.trim(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)

private val COMBINING_MARKS = Regex("\\p{Mn}+")
